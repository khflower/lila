# Omok Round / Live-Play Integration Plan

## Current Checkpoint

Most of the live-play plan below has now landed on `omok/mvp`.

Treat this document as the record of the round-side architecture that is already in use:

- `data.omok` for boot
- `place` for client -> server input
- `omokMove` for server -> client updates
- a parallel omok round controller instead of threading omok through chess-only round code

Coordinator note for follow-up work:

- native start-flow patches should reuse this contract unchanged
- persistence work should move storage below this contract, not replace it
- do not reopen chess `move` / `drop`, SAN/FEN, or `Chessground` paths unless there is a hard blocker

## Recommendation

Do not try to force the first omok live-play slice through the existing UCI/FEN/SAN and `Chessground` path.

The safest executable plan is:

1. keep the existing round routes, page shell, chat, clock, and socket transport;
2. add an omok sidecar state/payload keyed by the same `GameId`;
3. add a new omok move socket boundary (`place` in, `omokMove` out);
4. boot a parallel omok round controller on the frontend when `data.omok` is present;
5. leave the chess round controller and chess socket payloads untouched.

This avoids the two highest-risk changes in one wave:

- changing the global chess variant/runtime model;
- threading omok through the current `ui/round/src/ctrl.ts` `CgApi` path.

## Verified Current Chess Round Flow

### Initial page/data flow

1. `app/controllers/Round.scala`
   - `player` and `watcher` load a `Pov` and call `env.api.roundApi.player(...)` or `watcher(...)`.
2. `modules/api/src/main/RoundApi.scala`
   - builds the round JSON;
   - currently always appends `steps` through `lila.round.StepBuilder(...)`.
3. `modules/round/src/main/JsonView.scala`
   - builds player/watcher JSON from `lila.game.JsonView.baseWithChessDenorm(...)`;
   - currently always emits chess-only fields such as `possibleMoves`, `possibleDrops`, `crazyhouse`, `initialFen`.
4. `app/views/round/player.scala`
5. `app/views/round/watcher.scala`
   - boot the `round` frontend with `PageModule("round", Json.obj("data" -> data, ...))`.
6. `modules/round/src/main/ui/RoundUi.scala`
   - currently pre-renders a chess board with `povChessground(pov)`.

### Live move flow

1. `ui/round/src/round.ts`
   - opens `/play/<fullId>/v6` or `/watch/<gameId>/<color>/v6`.
2. `ui/round/src/ctrl.ts`
   - `sendMove` builds `{ u: "<uci>" }`;
   - `actualSendMove('move', ...)` sends it through the socket.
3. `modules/round/src/main/RoundSocket.scala`
   - parses `r/move` into `Protocol.In.PlayerMove(fullId, uci, blur, lag)`.
4. `modules/round/src/main/RoundAsyncActor.scala`
   - dispatches `HumanPlay` to `MovePlayer.human(...)`.
5. `modules/round/src/main/MovePlayer.scala`
   - parses/applies UCI against `game.chess`;
   - produces `Progress.events`.
6. `modules/game/src/main/Event.scala`
   - emits chess `move` / `drop` socket payloads with `uci`, `san`, `fen`, `dests`, `check`, `crazyhouse`, `drops`.
7. `modules/round/src/main/RoundAsyncActor.scala`
   - publishes events with `Protocol.Out.tellVersion(...)`.
8. `ui/round/src/socket.ts`
   - routes `move` / `drop` to `ctrl.apiMove`.
9. `ui/round/src/ctrl.ts`
   - mutates `Chessground`, appends a chess `Step`, updates clocks/status, triggers premove/promotion/crazyhouse logic.

## Hard Omok Boundaries

These files are the hard blockers for direct omok reuse:

- `modules/round/src/main/MovePlayer.scala`
  - hard-coded `Uci`, `MoveOrDrop`, `game.chess.moveWithCompensated`, `game.applyMove(...)`.
- `modules/round/src/main/StepBuilder.scala`
  - rebuilds history from SAN + FEN + chess replay.
- `modules/round/src/main/JsonView.scala`
  - computes `possibleMoves` from chess destinations and exports crazyhouse/drops.
- `modules/game/src/main/JsonView.scala`
  - exports `fen`, `check`, `lastMove`, `player` from `game.chess`.
- `ui/round/src/ctrl.ts`
  - hard-coded to `CgApi`, UCI, promotion, predrop, crazyhouse, SAN speech.
- `ui/round/src/ground.ts`
  - always mounts `Chessground`.
- `ui/round/src/view/replay.ts`
  - renders SAN move list only.
- `ui/round/src/view/main.ts`
  - always renders material diff / crazyhouse hooks around a chess board.
- `modules/round/src/main/ui/RoundUi.scala`
  - server preloads a chess board, not a neutral mount point.

## Socket Boundary To Add

Do not change chess `move` / `drop`.

Add a separate omok socket boundary.

### Client -> server

New event:

- `place`

Payload:

```ts
type SocketPlace = {
  pos: string; // "A1".."O15"
  b?: 1;       // blur flag, same convention as chess move/drop
};
```

Server parse location:

- `modules/round/src/main/RoundSocket.scala`
- add `Protocol.In.PlayerPlace(fullId, pos, blur, lag)`
- parse raw `r/place`

### Server -> client

New event:

- `omokMove`

Payload:

```ts
type ApiOmokMove = {
  pos: string;
  ply: number;
  turn: 'black' | 'white';
  boardRows: string[]; // 15 strings of length 15, using current dto encoding
  lastMove?: { key: string; row: number; col: number };
  status?: Status;
  winner?: 'black' | 'white';
  clock?: {
    white: number;
    black: number;
    lag?: number;
  };
  wDraw?: boolean;
  bDraw?: boolean;
};
```

Keep these existing generic events unchanged:

- `reload`
- `endData`
- `gone`
- `goneIn`
- `rematchOffer`
- `rematchTaken`
- `drawOffer`
- `takebackOffers`
- chat events

Reason:

- the current chess move parser in `RoundSocket` stays intact;
- the current chess `ctrl.apiMove` path stays intact;
- omok gets a payload shaped for coordinate placement and full-board redraw, not fake UCI/FEN.

## Minimum Playable Round Path

The next coding wave should target exactly this path:

1. two players load normal round pages for the same `GameId`;
2. round JSON contains `data.omok`;
3. frontend boots the omok round controller instead of the chess one;
4. the board shows the current `boardRows`, turn, last move, and clocks;
5. clicking an empty cell on your turn sends `place { pos }`;
6. server validates with `lila.omok.Game.play(...)`;
7. server saves the next omok snapshot and updates generic round status/clock state;
8. server broadcasts `omokMove`;
9. both clients redraw from the new snapshot;
10. win / resign / timeout end the game with existing `endData`.

That is enough for:

- human vs human live play;
- reconnect by `reload`;
- basic watcher support if the same `data.omok` branch is used on spectator pages.

Not required for the first live-play slice:

- premove
- predrop
- promotion
- crazyhouse pockets
- SAN/FEN-derived move list
- keyboard move
- voice move
- blindfold
- takeback
- draw claim / threefold / fifty-move logic
- analysis button

## Backend Patch Plan

### Wave 1: add round-side omok state seam

Edit:

- `build.sbt`
  - add `omok` as a dependency of `round`.

Add:

- `modules/round/src/main/OmokRoundRepo.scala`
  - repo/service keyed by `GameId`;
  - load/save current omok snapshot plus move list;
  - use existing `lila.omok.PositionSnapshot` and `Move`.

Edit:

- `modules/round/src/main/Env.scala`
  - wire `OmokRoundRepo`;
  - pass it to round JSON and move services.

Recommendation:

- do not thread omok through `lila.core.game.Game` in this wave;
- use presence of `OmokRoundRepo.get(game.id)` as the omok detector.

### Wave 2: initial round JSON + replay payload

Add:

- `modules/round/src/main/OmokStepBuilder.scala`
  - build omok replay/history from `lila.omok.Replay.scan(...)`;
  - emit coordinate-based steps, not SAN/FEN steps.

Edit:

- `modules/api/src/main/RoundApi.scala`
  - add a `withOmok(pov)` composition used by both `player(...)` and `watcher(...)`;
  - when omok state exists, append:
    - `omok.position`
    - `omok.steps`
    - `omok.ruleset`
    - `omok.boardSize`
  - do not overload existing chess `steps`.

- `modules/round/src/main/JsonView.scala`
  - on omok rounds, omit chess-only `possibleMoves`, `possibleDrops`, `crazyhouse`, `check` derivation;
  - keep generic player/opponent/clock/status data.

- `app/views/round/player.scala`
- `app/views/round/watcher.scala`
  - detect `data.omok`;
  - pass that flag into round preload rendering.

- `modules/round/src/main/ui/RoundUi.scala`
  - add an omok preload branch that renders an empty board mount, not `povChessground(pov)`.

Important:

- do not use `modules/round/src/main/StepBuilder.scala` for omok;
- keep chess `steps` untouched for chess rounds.

### Wave 3: live omok move path

Add:

- `modules/round/src/main/OmokEvent.scala`
  - define `OmokMove` implementing `lila.core.game.Event`;
  - `typ = "omokMove"`.

- `modules/round/src/main/OmokMovePlayer.scala`
  - load current omok snapshot from `OmokRoundRepo`;
  - parse coordinates with `lila.omok.CoordinateNotation.parse`;
  - validate/apply with `lila.omok.Game.play`;
  - save the next snapshot;
  - emit:
    - `OmokMove`
    - optional existing `lila.game.Event.EndData`
    - optional existing clock event if clocks stay active.

Edit:

- `modules/round/src/main/actorApi.scala`
  - add `HumanPlace(playerId, pos, moveMetrics, promise)`.

- `modules/round/src/main/RoundSocket.scala`
  - add `Protocol.In.PlayerPlace`;
  - parse `r/place`.

- `modules/round/src/main/RoundAsyncActor.scala`
  - route `HumanPlace` to `OmokMovePlayer`;
  - publish `OmokMove` exactly like existing chess events.

Keep unchanged in this wave:

- `modules/round/src/main/MovePlayer.scala`
  - chess only.

- `modules/game/src/main/Event.scala`
  - chess move/drop only.

This split avoids coupling the omok move path to chess `MoveOrDrop`.

## Post-Landing Follow-Up Rule

The next waves should preserve the working round boundary:

- start-flow work creates a real game and seeds omok state before first load
- persistence work adds a durable `GameId`-keyed omok store under the same boot/socket contract

If a follow-up requires changing `data.omok`, `place`, or `omokMove`, treat that as a higher-risk design review, not as routine incremental work.

## Frontend Patch Plan

### Recommendation: parallel omok controller, not a mutated chess controller

Do not make the first omok slice by branching through all of `ui/round/src/ctrl.ts`.

Verified reason:

- `ctrl.ts` is saturated with `CgApi`, UCI, promotion, predrop, crazyhouse, SAN speech, `renderMaterialDiffs`, keyboard/voice hooks.

The safer path is a parallel omok controller.

### Wave 1: boot switch and payload types

Edit:

- `ui/round/src/interfaces.ts`
  - add:
    - `OmokRoundData`
    - `OmokStep`
    - `SocketPlace`
    - `ApiOmokMove`
  - extend `RoundData` with optional `omok`.

- `ui/round/src/round.ts`
  - if `opts.data.omok` is present, boot `OmokRoundController`;
  - otherwise keep current `RoundController`.

Add:

- `ui/round/src/omokCtrl.ts`
  - own local live-play state for board rows, steps, current ply, clocks, loading, end state.

- `ui/round/src/omokGround.ts`
  - 15x15 board widget;
  - click-to-place only;
  - last-move highlight;
  - no premove/promotion/drop support.

- `ui/round/src/view/omokMain.ts`
  - board + replay + generic control panel.

- `ui/round/src/view/omokReplay.ts`
  - coordinate move list (`H8`, `J10`, etc.).

### Wave 2: socket handling

Edit:

- `ui/round/src/socket.ts`
  - add an `omokMove` handler;
  - keep all generic handlers (`reload`, `endData`, `gone`, `rematchOffer`, chat) shared.

Frontend event handling should be:

- chess controller listens to `move` / `drop`;
- omok controller listens to `omokMove`.

### Wave 3: view cleanup for omok path

Edit only if reusing shared views:

- `ui/round/src/view/main.ts`
  - skip material diff and crazyhouse sections on omok.

- `ui/round/src/view/table.ts`
  - hide analysis/draw/takeback controls for omok MVP.

- `ui/round/src/view/replay.ts`
  - do not render SAN on omok;
  - either branch or keep this chess-only and use `view/omokReplay.ts`.

Do not block the first omok live path on:

- `ui/round/src/keyboard.ts`
- `ui/round/src/round.nvui.ts`
- `ui/round/src/view/nvuiView.ts`

Just disable keyboard/voice/nvui for `data.omok` in the first omok round controller.

## Exact File List For The Next Coding Wave

### Must edit

- `build.sbt`
- `modules/round/src/main/Env.scala`
- `modules/api/src/main/RoundApi.scala`
- `modules/round/src/main/JsonView.scala`
- `modules/round/src/main/RoundSocket.scala`
- `modules/round/src/main/RoundAsyncActor.scala`
- `modules/round/src/main/actorApi.scala`
- `modules/round/src/main/ui/RoundUi.scala`
- `app/views/round/player.scala`
- `app/views/round/watcher.scala`
- `ui/round/src/interfaces.ts`
- `ui/round/src/round.ts`
- `ui/round/src/socket.ts`

### Recommended new backend files

- `modules/round/src/main/OmokRoundRepo.scala`
- `modules/round/src/main/OmokStepBuilder.scala`
- `modules/round/src/main/OmokEvent.scala`
- `modules/round/src/main/OmokMovePlayer.scala`

### Recommended new frontend files

- `ui/round/src/omokCtrl.ts`
- `ui/round/src/omokGround.ts`
- `ui/round/src/view/omokMain.ts`
- `ui/round/src/view/omokReplay.ts`

### Explicitly defer for this wave

- `modules/round/src/main/MovePlayer.scala`
- `modules/game/src/main/Event.scala`
- `ui/round/src/ctrl.ts`
- `ui/round/src/ground.ts`
- `ui/round/src/crazy/*`
- `ui/round/src/premove.ts`
- `ui/round/src/keyboard.ts`
- `ui/round/src/round.nvui.ts`

## Order Of Execution

1. Add the round-side omok repo and JSON seam.
2. Make player/watcher pages boot from `data.omok`.
3. Add `place` -> `HumanPlace` -> `OmokMove` socket flow.
4. Boot a parallel omok controller and 15x15 board.
5. Verify two-player completion path.
6. Only after that, decide whether any generic controls can be re-enabled.

## Bottom Line

The minimum safe round integration is not “teach the chess round stack about omok everywhere”.

It is:

- separate omok move messages,
- separate omok replay payload,
- separate omok controller,
- shared route/chat/clock/socket shell.

That gives the next coding wave a direct path to a real playable round without destabilizing the existing chess runtime.
