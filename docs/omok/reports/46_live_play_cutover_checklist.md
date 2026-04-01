# Omok Live Play Cutover Checklist

## Purpose

This checklist starts after the new round boot/preload seam landed on `omok/mvp`.

The branch now has enough wiring to detect an omok round at round boot time, branch the server-rendered preload shell, and reserve socket/frontend seams. What is still missing is the actual playable loop.

This report lists the next concrete steps to get from the current placeholders and stubs to a first end-to-end internal omok round path.

## Verified Current State

- `build.sbt` already makes `round` depend on `omok`.
- `modules/round/src/main/OmokRoundRepo.scala` exists and is wired from `modules/round/src/main/Env.scala`.
- `modules/api/src/main/RoundApi.scala` now appends `data.omok` for player and watcher JSON when `OmokRoundRepo` has state for the current `GameId`.
- `modules/round/src/main/ui/RoundUi.scala` plus `app/views/round/player.scala` and `app/views/round/watcher.scala` now preload an omok shell instead of server-rendering `povChessground(...)` when `data.omok` is present.
- `ui/round/src/round.ts`, `ui/round/src/omok.ts`, and `ui/round/src/view/omokPlaceholder.ts` already identify omok rounds and mark the DOM with placeholder dataset hints.
- `modules/round/src/main/RoundSocket.scala` already parses `r/place` into `HumanPlace`.
- `modules/round/src/main/OmokEvent.scala` plus `RoundSocket.Protocol.Out.omokMove(...)` already define the outward omok move envelope.
- `modules/round/src/main/RoundAsyncActor.scala` still ignores `HumanPlace`, logs, and resyncs instead of applying a move.
- `ui/round/src/round.ts` still falls through to `RoundController`, and `ui/round/src/view/main.ts`, `ui/round/src/ground.ts`, and `ui/round/src/ctrl.ts` still assume chess `steps`, `fen`, `san`, `Chessground`, promotion, premove, and chess replay/material surfaces.
- `OmokRoundRepo` is currently read by `RoundApi`, but there is still no authoritative live seed/write/cleanup path on this branch.

## First Internal Playable Path

The cutover target for this wave should stay narrow:

1. a known omok round seeds live omok state before page boot;
2. player and watcher round JSON include `data.omok`;
3. the frontend mounts a visible 15x15 omok board, not chessground;
4. clicking an empty cell on your turn sends `place { pos }`;
5. the server validates and applies the move with `lila.omok.Game.play(...)`;
6. the round actor publishes `omokMove`;
7. players and watchers redraw from the new snapshot;
8. reload/reconnect rebuild from `data.omok`;
9. win/resign/timeout/cleanup flow through the existing generic round/game finish paths.

## Dependency-Ordered Checklist

### 1. Lock one authoritative omok round detector and seed path

- [ ] Choose one server-side source of truth for "this `GameId` is an omok round".
- [ ] Seed `OmokRoundRepo` from a creation/start path that already knows the game is omok.
- [ ] Do not seed from `RoundApi.withOmok(...)`, server preload rendering, or socket receive; those are read paths and will hide lifecycle bugs.
- [ ] Make the initial seed explicit about rule set and starting snapshot.
- [ ] Confirm both player and watcher loads observe the same seeded repo state before round JSON is built.

Exit condition:

- `data.omok` appears because the round was deliberately seeded, not because a read path opportunistically called `getOrInit(...)`.

### 2. Replace the `HumanPlace` stub with real omok move application

- [ ] Add an omok move executor alongside chess `MovePlayer` logic, likely as `modules/round/src/main/OmokMovePlayer.scala`.
- [ ] Load the current `OmokRoundState` from `OmokRoundRepo`.
- [ ] Reject non-omok rounds, wrong-turn placements, occupied cells, finished games, and Renju-forbidden black moves on the server.
- [ ] Apply legal placements through `lila.omok.Game.play(...)`.
- [ ] Persist the next snapshot and move list back into `OmokRoundRepo`.
- [ ] Update generic round state in lockstep: clocks, status, winner, and any draw/finish flags that already belong to the shared round model.
- [ ] Keep invalid placements on the existing resync/error boundary instead of inventing a second failure channel.

Exit condition:

- A valid `place` changes authoritative round state exactly once; an invalid `place` leaves state untouched and resyncs the sender.

### 3. Put `omokMove` on the normal round publish path

- [ ] Promote `OmokEvent.Move` into something the round actor can publish the same way it publishes chess events.
- [ ] Emit `omokMove` only after repo persistence and generic round/game state updates succeed.
- [ ] Keep existing generic events unchanged: `reload`, `endData`, `gone`, `goneIn`, `drawOffer`, `rematchOffer`, and chat events.
- [ ] Do not make full reload the primary happy path; reload should remain recovery, not normal move transport.

Exit condition:

- One successful `place` sends one versioned `omokMove` packet to all connected clients for that round.

### 4. Replace the frontend omok placeholder with a minimal real runtime

- [ ] Stop booting `RoundController` for `opts.data.omok` in `ui/round/src/round.ts`.
- [ ] Add a minimal omok round controller/view that renders directly from `data.omok.position`.
- [ ] Render a visible 15x15 board, current turn, last move marker, and generic round clocks/status.
- [ ] Send `place` packets from board clicks only when the local side may act.
- [ ] Consume `omokMove` by replacing board state, turn, clocks, status, and winner from the payload.
- [ ] Reuse the existing page shell, websocket bootstrap, chat, and clock widgets instead of forking the whole round page.

Exit condition:

- An omok round page no longer instantiates chessground or chess move handlers, and a player can place a stone through the UI.

### 5. Keep the omok runtime off chess-only round surfaces

- [ ] Do not try to keep omok alive by feeding fake chess `steps`, `fen`, or `san` into the current controller.
- [ ] Hide or bypass chess-only board-adjacent UI on the omok branch, especially the existing paths in `ui/round/src/ground.ts`, `ui/round/src/view/main.ts`, and related chess-only helpers.
- [ ] Keep promotion, premove, predrop, material diff, crazyhouse hooks, and the SAN replay table off the omok branch.
- [ ] Keep keyboard and voice move hooks off by default unless they are explicitly ported to the omok move model.
- [ ] Treat the current placeholder DOM dataset markers as boot hints only, not as the final runtime.

Exit condition:

- The omok path is a thin parallel runtime, not a patched chess runtime carrying fake board data.

### 6. Close the loop for watcher, reload, and cleanup

- [ ] Make watcher pages boot the same omok runtime as player pages.
- [ ] Make XHR reload/reconnect rebuild entirely from `data.omok` plus generic round metadata.
- [ ] Clear `OmokRoundRepo` state on finish, abort, expiry, and actor teardown.
- [ ] Verify reconnect after one or more moves does not depend on transient client-side history.

Exit condition:

- Player refresh, watcher connect, and actor cleanup work without stale in-memory omok state leaking across rounds.

## Recommended Cut Line

Keep this wave narrow:

- human vs human live play;
- watcher visibility;
- reconnect/reload from `data.omok`;
- finish through existing resign/timeout/win plumbing.

Do not spend this cutover on:

- SAN/FEN replay parity;
- forcing omok through chess `steps`;
- premove, promotion, crazyhouse, keyboard, or voice parity;
- Rapfi/AI move generation;
- wider persistence redesign beyond what is required to seed, update, and clear live round state.

If a choice is ambiguous, prefer the option that gets one real omok round from page boot to successful live move broadcast without extending the chess controller further.
