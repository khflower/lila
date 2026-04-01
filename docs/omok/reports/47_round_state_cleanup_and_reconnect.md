# Omok Round State Cleanup and Reconnect Checklist

## Purpose

This checkpoint follows the live-play cutover checklist and narrows the next omok round work to runtime integrity.

It is based on the current `omok/mvp` branch state as inspected in code, not on the earlier planning reports.

Focus here is limited to:

- reconnect and reload correctness;
- resync behavior after invalid or missing state;
- finish, abort, expiry, and teardown cleanup;
- stale in-memory omok state risks.

## Verified Current State

- `modules/round/src/main/OmokRoundRepo.scala` stores live omok round state in an in-memory `TrieMap[GameId, OmokRoundState]`.
- `modules/api/src/main/RoundApi.scala` only appends `data.omok` when `omokRoundRepo.get(pov.gameId)` already has state; it does not seed on read.
- `modules/round/src/main/OmokMovePlayer.scala` exists, and `modules/round/src/test/OmokMovePlayerTest.scala` confirms preview and repo persistence behavior, but there is still no production caller wired into the round actor.
- `modules/round/src/main/RoundSocket.scala` already parses `r/place` into `HumanPlace`.
- `modules/round/src/main/RoundAsyncActor.scala` still ignores `HumanPlace`, logs, and resyncs the sender instead of applying a move.
- `modules/round/src/main/OmokEvent.scala` plus `RoundSocket.Protocol.Out.omokMove(...)` already define the outward omok move packet as `{ move, position }`.
- `ui/round/src/socket.ts` forwards `omokMove`, but `ui/round/src/interfaces.ts` and `ui/round/src/ctrl.ts` still expect a flatter `ApiOmokMove` shape, not the current `{ move, position }` envelope.
- `ui/round/src/interfaces.ts` also types `omok.position.moves` as `string[]`, while the current omok DTO layer emits move objects.
- `ui/round/src/round.ts` still boots `RoundController` for omok pages, so reconnect and reload still pass through the chess-oriented round runtime.
- No current production hook removes `OmokRoundRepo` entries on `FinishGame`, abort, `DeleteUnplayed`, round termination, or `RoundAsyncActor.Stop`.
- `modules/round/src/main/Titivate.scala` deletes stale unplayed games and chat state, but it does not touch `OmokRoundRepo`.

## Main Integrity Risks

### 1. Orphaned repo state after terminal paths

`OmokRoundRepo` can outlive the round actor and the game lifecycle because current finish, abort, delete-unplayed, and actor-stop paths do not clear it.

That creates two concrete risks:

- memory leaks for every seeded omok round;
- stale repo presence becoming a false omok detector when a later read path only checks `omokRoundRepo.get(gameId)`.

### 2. Silent state creation from the wrong path

`OmokMovePlayer.place(...)` currently uses `getOrInit(...)`.

That is acceptable for a local helper, but once it is wired into production it will silently create new omok state from a move path unless the caller prevents it. That would hide missing-seed bugs and make reconnect/resync behavior nondeterministic.

### 3. Socket and reload schemas are not yet aligned

The server-side `omokMove` packet and the frontend `ApiOmokMove` contract do not currently describe the same JSON shape.

If move publish is wired without fixing that mismatch first, the likely result is:

- client-side omok state not updating from the happy-path socket event;
- forced reload becoming the de facto transport path;
- watchers and reconnecting clients drifting onto different state assumptions.

### 4. Reconnect still depends on chess round runtime assumptions

The current omok page branch changes preload markers, but the live JS controller is still `RoundController`.

That means reconnect and reload still run through code that is keyed off chess `steps`, `fen`, `san`, `Chessground`, and chess-specific redraw assumptions. Even if the initial `data.omok` payload is correct, the runtime still has stale-state exposure until the omok branch stops sharing that controller.

## Checklist

### 1. Make omok state ownership explicit

- [ ] Choose one authoritative seed path for `OmokRoundRepo` before any player or watcher boot path reads it.
- [ ] Keep `RoundApi.withOmok(...)` as a read-only gate; do not let page boot or reconnect initialize omok state implicitly.
- [ ] Treat missing repo state during `place`, reload, or reconnect as an integrity failure to recover from, not as a signal to create a fresh board.
- [ ] Document whether repo presence remains the omok round detector for MVP, or replace it with a more explicit marker.

Exit condition:

- A round only gets `data.omok` because a deliberate seed step happened earlier in the lifecycle.

### 2. Align incremental and full-state payloads

- [ ] Make the frontend omok move contract match the actual server packet shape, or change the server packet to the contract the frontend consumes.
- [ ] Align `ui/round/src/interfaces.ts` with the real omok DTO shapes, especially `position.moves`.
- [ ] Ensure the same canonical omok DTO fields drive both `data.omok` boot payloads and `omokMove` updates.
- [ ] Keep generic round metadata updates in sync with the omok snapshot: status, winner, clocks, and any draw flags.

Exit condition:

- A client that boots from `data.omok` and a client that stays connected through `omokMove` end up with the same board, turn, and terminal status.

### 3. Define reconnect and resync rules before wiring live move execution

- [ ] Decide what happens when a player reconnects and omok repo state is missing while the generic round still exists.
- [ ] Keep invalid placement handling on the existing sender-resync boundary, but make the resync source authoritative server state, not client history.
- [ ] Make watcher first-load, player refresh, websocket resume, and XHR reload all rebuild from the same server omok snapshot.
- [ ] Refuse to treat a reconnect path as successful if it only reconstructs generic chess `steps` while omok state is absent or stale.

Exit condition:

- Refresh, reconnect, and invalid-move resync all converge onto the same server-side omok state without silently creating a new one.

### 4. Assign one cleanup owner and make cleanup idempotent

- [ ] Pick one primary cleanup owner for `OmokRoundRepo` removal.
- [ ] Cover game finish paths triggered by `Finisher`.
- [ ] Cover user abort and forced abort paths.
- [ ] Cover delete-unplayed cleanup from `Titivate` / `DeleteUnplayed`.
- [ ] Cover round actor termination and `RoundAsyncActor.Stop`.
- [ ] Cover shutdown-time teardown if omok state can still be seeded when `LilaStop` runs.
- [ ] Make repeated cleanup safe so finish + stop or delete + stop do not race into inconsistent behavior.

Exit condition:

- Every terminal round lifecycle path removes the repo entry exactly once from the perspective of observable state.

### 5. Guard against stale-state reads

- [ ] Before appending `data.omok`, decide how to treat repo entries whose underlying game is already finished, aborted, or removed.
- [ ] Ensure actor recreation after disconnect does not blindly reuse a stale omok snapshot that should already have been cleared.
- [ ] Add minimal logging or counters for missing-state reconnects, orphan cleanup, and stale-entry cleanup so integrity failures are visible during MVP rollout.

Exit condition:

- Stale repo entries are either impossible by construction or detectable quickly enough to fix before they become normal reconnect behavior.

## Best Current Hook Points

If the next implementation wave keeps the current branch structure, these are the most relevant hook points to audit:

- `modules/api/src/main/RoundApi.scala` for read-only `data.omok` exposure.
- `modules/round/src/main/OmokMovePlayer.scala` for preventing implicit `getOrInit(...)` from becoming the production seed path by accident.
- `modules/round/src/main/RoundAsyncActor.scala` for `HumanPlace`, `Stop`, and shutdown behavior.
- `modules/round/src/main/RoundSocket.scala` for round termination and existing bus subscriptions.
- `modules/round/src/main/Finisher.scala` for generic terminal game completion.
- `modules/round/src/main/Titivate.scala` for delete-unplayed cleanup.

## Manual Acceptance Cases

- [ ] Seed an omok round, load player and watcher pages, and confirm both see the same `data.omok`.
- [ ] Make one or more omok moves, refresh either side, and confirm the board rebuilds from server state only.
- [ ] Send an invalid `place` and confirm sender resync returns the pre-existing authoritative omok board.
- [ ] Finish a game by win, resign, timeout, and abort, then confirm reconnect does not resurrect live omok repo state.
- [ ] Let an unplayed game age into `DeleteUnplayed` cleanup and confirm no `OmokRoundRepo` entry survives.
- [ ] Disconnect both players long enough for actor termination, reconnect later, and confirm behavior is either a clean rebuild from the authoritative seed/state source or a deliberate hard failure, not a silent blank-board reset.

## Recommended Cut Line

Keep this wave focused on integrity, not features:

- reconnect and reload must converge on one omok state source;
- invalid actions must resync from that same source;
- finish and teardown must clear live omok state;
- stale repo entries must stop being invisible.

Do not spend this cleanup wave on board UX polish, replay parity, AI, or broader persistence redesign beyond what is required to make omok round state trustworthy.
