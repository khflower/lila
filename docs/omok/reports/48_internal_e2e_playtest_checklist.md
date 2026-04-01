# Omok Internal E2E Playtest Checklist

## Scope

This is the first concrete internal playtest script for the current `omok/mvp` live loop.

It is intentionally scoped to what the branch actually supports today:

- manually seeded omok round state in `OmokRoundRepo`;
- player and watcher page boot with `data.omok`;
- one legal `place` packet;
- socket broadcast observation;
- manual reload/reconnect recovery;
- reject/resync behavior after an invalid follow-up action.

It is not a release checklist. Normal omok game creation, full turn handoff, terminal status, and polished live redraw are not complete on this branch yet.

## Verified Current Branch State

The checklist below is based on the code currently on `omok/mvp`.

- `modules/api/src/main/RoundApi.scala` only adds `data.omok` when `omokRoundRepo.get(gameId)` already has state.
- `modules/round/src/main/ui/RoundUi.scala`, `app/views/round/player.scala`, `app/views/round/watcher.scala`, `ui/round/src/omok.ts`, and `ui/round/css/_omok.scss` already mark the page as omok mode and hide the visible chessground board behind an omok overlay.
- `modules/round/src/main/RoundSocket.scala` already parses `r/place` with `CoordinateNotation.parse(...)`.
- `modules/round/src/main/RoundAsyncActor.scala` now routes `HumanPlace` into `OmokMovePlayer.placeIfPresent(...)`, increments socket version, emits `omokMove` for accepted placements, and resyncs instead of silently creating missing omok state.
- `modules/round/src/main/OmokEvent.scala` currently emits `omokMove` as `{ "move": ..., "position": ... }`.
- `ui/round/src/ctrl.ts` still consumes `ApiOmokMove` as a flatter payload with top-level `boardRows`, `turn`, `ply`, and `lastMove`.
- `ui/round/src/round.ts` still boots the standard `RoundController` for omok pages.
- `ui/round/src/xhr.ts` reloads by refetching the round JSON from the normal player or watcher URL, so reload/reconnect currently depends on `data.omok`, not on incremental omok replay.
- `modules/round/src/main/RoundAsyncActor.scala` validates omok turn by player color, but the frontend still decides clickability from generic round state, so omok turn and generic round turn can drift.

## Setup

1. Start local services needed for a real socket round loop.
   - MongoDB on `127.0.0.1:27017`.
   - Redis on `127.0.0.1`.
   - lila via `./lila.sh` then `run`.
   - `lila-ws` on `localhost:9664`.
   - Optional but recommended while iterating UI: `ui/build -w`.

2. Do not disable websockets in `conf/application.conf`.
   - `conf/base.conf` points `net.socket.domains` at `localhost:9664`.
   - `conf/application.conf.default` shows that `net.socket.domains = []` disables sockets, which makes this checklist invalid.

3. Obtain one deliberately seeded round.
   - There is still no production omok creation path on this branch.
   - `OmokRoundRepo` must already contain state for the target `GameId` before any page is loaded.
   - For the first move case, use a round whose generic active player is also black, because a fresh omok snapshot starts with `turn = black` and the current UI still gates clicks from generic round turn.

4. Open three browser sessions for the same round.
   - Black player tab.
   - White player tab.
   - Watcher tab.

5. Open DevTools on at least the black player tab and the watcher tab.
   - Preserve console logs.
   - Preserve websocket frames.
   - Keep the network panel visible for reload verification.

## Checklist

### 1. Seeded Boot

Actions:

- [ ] Load the black player page.
- [ ] Load the white player page.
- [ ] Load the watcher page.
- [ ] Do not click yet.

Expected:

- `body` has `data-round-mode="omok"`.
- `.round__app` has `data-board-game="omok"` and `data-board-ready="placeholder"`.
- A visible 15x15 omok overlay board is rendered with file labels `A` through `O` and rank labels `1` through `15`.
- The omok state pill shows the seeded values for turn, ply, last move, and ruleset.
- The page still uses the normal round websocket endpoints:
  - player: `/play/<fullId>/v6`
  - watcher: `/watch/<gameId>/<color>/v6`
- Player and watcher boot from the same `data.omok.position` snapshot.

Failure signatures:

- `data.omok` is absent from the boot payload.
- Only a chess board is visible and no omok overlay appears.
- The omok overlay renders but the turn, ply, or last move does not match the seeded snapshot.
- Console errors mention `boardRows`, `position`, or omok render hooks during initial paint.

### 2. First Legal Place

Actions:

- [ ] On the black player tab, click an empty center point such as `H8`.
- [ ] Do not manually reload yet.
- [ ] Watch websocket frames on the black player tab and the watcher tab.

Expected socket behavior:

- One outbound `place` message is sent from the black player tab for `H8`.
- One versioned `omokMove` message is broadcast back to connected clients for the round.
- The current server payload shape is:

```json
{
  "move": { "key": "H8", "row": 7, "col": 7 },
  "position": {
    "boardSize": 15,
    "boardRows": ["...............", "..."],
    "turn": "white",
    "ruleSet": "renju",
    "ply": 1,
    "lastMove": { "key": "H8", "row": 7, "col": 7 },
    "moves": [{ "key": "H8", "row": 7, "col": 7 }]
  }
}
```

- No `resync` message should be sent for this legal first click.
- No full-page reload should happen automatically for this legal first click.

Expected state if transport succeeded:

- The authoritative omok snapshot is now `ply = 1`.
- `turn = white`.
- `lastMove.key = "H8"`.
- Row 8 of `boardRows` is `.......b.......`.

Failure signatures to watch for on the current branch:

- The player tab or watcher tab receives `omokMove` but the board does not redraw.
- A console error appears immediately after `omokMove` because the client expects flat top-level omok fields instead of `{ move, position }`.
- The board overlay freezes or disappears after the legal click.
- A legal first click triggers `resync` instead of `omokMove`.

### 3. Manual Reload / Reconnect Recovery

Actions:

- [ ] Hard refresh the black player tab after step 2.
- [ ] Hard refresh the watcher tab after step 2.
- [ ] Optionally close the watcher tab and reopen it instead of refreshing.

Expected:

- The page boots back into omok mode after refresh.
- `data.omok.position` reflects the persisted post-move state from step 2.
- The reloaded board shows one black stone at `H8`.
- The state pill shows `Turn: White`, `Ply: 1`, and `Last move: H8`.
- The watcher tab and player tab show the same omok board after reload.
- This reload path is currently the main recovery path if live redraw failed on `omokMove`.

Failure signatures:

- The refreshed page comes back without `data.omok`.
- The refreshed page resets to an empty omok board.
- Player and watcher reload to different omok positions.
- `lastMove` or `ply` is missing after refresh even though the legal socket event was emitted.

### 4. Wrong-Turn Reject / Sender Resync

Actions:

- [ ] After step 3, stay on the black player tab.
- [ ] If empty cells are clickable there, click a new empty point such as `I8`.
- [ ] Wait one second and watch both websocket frames and page reload behavior.

Expected socket and reload behavior:

- The server rejects the move because omok turn is now white.
- The sender receives `resync`.
- The websocket client converts `resync` into a full page reload roughly 0.5 seconds later.
- After reload, the omok board returns to the authoritative one-stone state from step 3.
- The watcher tab does not advance.

Failure signatures:

- The second black move is accepted and advances the board.
- The sender never reloads after the rejected click.
- The reloaded board is different from the last accepted state.
- The watcher tab changes even though the move was rejected.

### 5. White Turn Handoff Sanity Check

Actions:

- [ ] After step 3, inspect whether the white player tab has clickable empty cells.
- [ ] If it does, try one legal white click such as `A1`.
- [ ] If it does not, record that as the observed result.

Expected on a complete loop:

- White should be the only side allowed to place after the black `H8` move.

Likely current-branch outcomes to record:

- White cannot click at all after reload because the frontend still gates on generic round turn instead of omok turn.
- Black can still click locally after reload, but the server rejects and resyncs.
- If white can click and the move is accepted server-side, the same live redraw failure from step 2 may repeat because the incremental socket payload shape is still mismatched.

## Current Pass Criteria For This Checkpoint

Treat the current branch as a partial transport and recovery checkpoint, not as a polished playable loop.

Pass this checklist if all of the following hold:

- a pre-seeded round boots into visible omok mode for player and watcher pages;
- one legal `place` produces one `omokMove` broadcast on the socket;
- manual refresh rebuilds the correct omok board from `data.omok`;
- an invalid follow-up `place` causes sender resync and returns to the last accepted state.

Record but do not treat as a surprise if any of the following are still broken:

- live board redraw after `omokMove`;
- white-turn handoff after the first reload;
- generic round clocks, status, and winner staying aligned with omok state;
- normal round creation without manual seeding.
