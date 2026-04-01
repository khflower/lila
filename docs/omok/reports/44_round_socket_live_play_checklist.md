# Omok Round / Socket / Live-Play Integration Checklist

## Purpose

This report turns the existing round integration plan into a concrete implementation checklist for the first omok live-play slice.

Scope is intentionally narrow:

- reuse the existing round routes, page shell, chat, and clock transport;
- add an omok-specific state seam and move socket path;
- boot a parallel omok round controller when `data.omok` is present;
- defer chess-only affordances and non-MVP features.

## Verified Current State

The checklist below is based on the current `omok/mvp` branch layout.

Confirmed facts:

- `build.sbt` defines `omok` and `round` as separate modules, and `round` does not currently depend on `omok`.
- `modules/api/src/main/RoundApi.scala` always appends chess `steps` via `lila.round.StepBuilder(...)`.
- `modules/round/src/main/JsonView.scala` always emits chess-specific fields such as `possibleMoves`, `possibleDrops`, `crazyhouse`, and `initialFen`-derived game data.
- `modules/round/src/main/RoundSocket.scala` only parses chess move traffic through `Protocol.In.PlayerMove`.
- `modules/round/src/main/actorApi.scala` only defines the internal `HumanPlay` path for chess UCI moves.
- `modules/round/src/main/MovePlayer.scala` is fully chess-specific and cannot be reused directly for omok placement.
- `ui/round/src/round.ts` always boots the standard chess round controller.
- `ui/round/src/socket.ts` only handles chess `move` and `drop`.
- `ui/round/src/ground.ts` always mounts `Chessground`.
- `modules/omok/src/main/bridge.scala` already provides `OmokPositionDto` and `OmokMoveDto`, which should be reused instead of inventing a second board JSON shape.

## Implementation Checklist

### 1. Backend dependency seam

- [ ] Edit `build.sbt` so `round` depends on `omok`.
- [ ] Confirm the `round` module can import `lila.omok.PositionSnapshot`, `Move`, `Game`, `CoordinateNotation`, and `OmokPositionDto`.
- [ ] Do not thread omok through the global chess `Game` model in this wave.

Acceptance:

- [ ] `round` compiles with direct imports from `modules/omok`.

### 2. Round-side omok state repository

- [ ] Add `modules/round/src/main/OmokRoundRepo.scala`.
- [ ] Store active omok state keyed by `GameId`.
- [ ] Persist at minimum:
  - current `lila.omok.PositionSnapshot`;
  - move list or enough data to rebuild replay steps;
  - ruleset and board size through the snapshot, not separate ad hoc fields.
- [ ] Expose read/write methods needed by round JSON boot and live move application.
- [ ] Decide whether missing repo state is the only omok detector for round MVP; document that in code comments if used.

Acceptance:

- [ ] One `GameId` lookup returns enough data to build both initial `data.omok` and incremental `omokMove` events.

### 3. Environment wiring

- [ ] Edit `modules/round/src/main/Env.scala`.
- [ ] Wire `OmokRoundRepo`.
- [ ] Pass it into the services that build round JSON and execute moves.
- [ ] Keep existing chess wiring untouched for non-omok rounds.

Acceptance:

- [ ] Env construction compiles without changing unrelated round features.

### 4. Initial round JSON contract

- [ ] Edit `modules/api/src/main/RoundApi.scala`.
- [ ] Add a `withOmok(...)` composition for both `player(...)` and `watcher(...)`.
- [ ] Only append `data.omok` when omok round state exists for the current `GameId`.
- [ ] Keep existing top-level chess `steps` for chess rounds.
- [ ] Do not overload chess `steps` with omok coordinates.

Recommended `data.omok` shape:

```json
{
  "position": {
    "boardSize": 15,
    "boardRows": ["..............."],
    "turn": "black",
    "ruleSet": "renju",
    "ply": 0,
    "lastMove": null,
    "moves": []
  },
  "steps": [],
  "ruleset": "renju",
  "boardSize": 15
}
```

Checklist:

- [ ] Reuse `lila.omok.OmokPositionDto` for `omok.position`.
- [ ] Add an omok replay builder instead of reusing chess `StepBuilder`.
- [ ] Keep the omok namespace isolated under `data.omok`.

Acceptance:

- [ ] Player and watcher JSON both include `data.omok` on omok rounds.
- [ ] Chess rounds produce byte-for-byte equivalent payloads apart from unrelated serialization noise.

### 5. Omok replay builder

- [ ] Add `modules/round/src/main/OmokStepBuilder.scala`.
- [ ] Rebuild history from stored omok moves using `lila.omok.Replay.scan(...)`.
- [ ] Emit coordinate-based replay data from omok state, not SAN/FEN-derived steps.
- [ ] Keep failure behavior explicit if stored moves cannot be replayed cleanly.

Acceptance:

- [ ] Omok replay generation succeeds from persisted moves alone.
- [ ] Replay build failure does not silently fall back to chess `StepBuilder`.

### 6. Round JSON filtering for omok

- [ ] Edit `modules/round/src/main/JsonView.scala`.
- [ ] On omok rounds, suppress chess-only fields:
  - `possibleMoves`
  - `possibleDrops`
  - `crazyhouse`
  - chess-only `check`/destination derivation
- [ ] Keep generic round data:
  - players
  - clocks
  - game status
  - correspondence flags where still valid
  - chat and spectator metadata

Acceptance:

- [ ] `data.omok` rounds do not require fake FEN/UCI/crazyhouse data to render.

### 7. Server-rendered preload branch

- [ ] Edit `modules/round/src/main/ui/RoundUi.scala`.
- [ ] Add an omok preload branch that renders a neutral board mount, not `povChessground(pov)`.
- [ ] Edit `app/views/round/player.scala` and `app/views/round/watcher.scala` so the preload path can branch from the JSON payload or an explicit omok flag.
- [ ] Preserve the existing page shell, side column, underboard, and chat layout.

Acceptance:

- [ ] Omok round pages no longer server-render a chessboard placeholder.

### 8. Socket protocol: client to server

- [ ] Edit `modules/round/src/main/RoundSocket.scala`.
- [ ] Add a new socket event `place` at the round socket boundary.
- [ ] Parse `r/place` into a new protocol message such as `Protocol.In.PlayerPlace(fullId, pos, blur, lag)`.
- [ ] Keep existing chess `move` and `drop` parsing unchanged.
- [ ] Extend `modules/round/src/main/actorApi.scala` with a separate internal message such as `HumanPlace`.

Recommended payload:

```ts
type SocketPlace = {
  pos: string; // "A1".."O15"
  b?: 1;
};
```

Acceptance:

- [ ] A `place` packet reaches the round actor without passing through UCI parsing.

### 9. Socket protocol: server to client

- [ ] Add `modules/round/src/main/OmokEvent.scala`.
- [ ] Implement an event conforming to `lila.core.game.Event` with `typ = "omokMove"`.
- [ ] Emit payload shaped for full board redraw plus clocks/status, not chess move notation.
- [ ] Keep generic existing events unchanged:
  - `reload`
  - `endData`
  - `gone`
  - `goneIn`
  - `rematchOffer`
  - `rematchTaken`
  - `drawOffer`
  - `takebackOffers`
  - chat events

Recommended payload:

```ts
type ApiOmokMove = {
  pos: string;
  ply: number;
  turn: 'black' | 'white';
  boardRows: string[];
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

Acceptance:

- [ ] Omok clients can update from `omokMove` alone without synthesizing fake chess state.

### 10. Round actor dispatch path

- [ ] Edit `modules/round/src/main/RoundAsyncActor.scala`.
- [ ] Add dispatch for the new internal omok move message alongside the existing chess `HumanPlay`.
- [ ] Ensure lag recording, save/publish flow, and resync behavior are parallel to chess where appropriate.
- [ ] Keep bot/fishnet/chess move paths untouched in MVP unless omok explicitly needs them.

Acceptance:

- [ ] Illegal omok moves fail through the same resync/error boundary used for invalid chess moves.

### 11. Omok move executor

- [ ] Add `modules/round/src/main/OmokMovePlayer.scala`.
- [ ] Load the current snapshot from `OmokRoundRepo`.
- [ ] Parse coordinates with `lila.omok.CoordinateNotation.parse`.
- [ ] Apply moves with `lila.omok.Game.play(...)`.
- [ ] Save the next snapshot back to `OmokRoundRepo`.
- [ ] Update generic round game status and clocks in lockstep with the omok snapshot.
- [ ] Publish `OmokMove` events after persistence succeeds.
- [ ] Finish the game through the existing round/game finisher path when omok reaches a terminal state.

Acceptance:

- [ ] Occupied-cell, out-of-bounds, wrong-turn, forbidden-move, and finished-game cases are rejected server-side.
- [ ] A valid place updates both persisted omok state and outward socket state exactly once.

### 12. Frontend data contracts

- [ ] Edit `ui/round/src/interfaces.ts`.
- [ ] Add `SocketPlace` and `ApiOmokMove` types.
- [ ] Extend `EventsWithPayload` so the socket send layer knows about `place`.
- [ ] Extend `RoundData` with optional `omok`.
- [ ] Keep chess types unchanged for non-omok rounds.

Acceptance:

- [ ] TypeScript round code compiles with both chess and omok payloads.

### 13. Frontend boot path

- [ ] Edit `ui/round/src/round.ts`.
- [ ] If `opts.data.omok` is present, boot an omok controller instead of `RoundController`.
- [ ] Keep websocket connection URL and generic round bootstrap logic shared where possible.
- [ ] Do not fork chat boot or tournament clock boot unless omok actually requires it.

Acceptance:

- [ ] A page with `data.omok` never instantiates the chess round controller.

### 14. Frontend socket handling

- [ ] Edit `ui/round/src/socket.ts`.
- [ ] Add an `omokMove` handler.
- [ ] Route `place` sends through the same `RoundSocketSend` transport.
- [ ] Keep chess `move` and `drop` handlers unchanged.
- [ ] Preserve `reload`, `endData`, and generic side-channel events for omok.

Acceptance:

- [ ] Omok rounds can receive `omokMove` without touching `ctrl.apiMove`.

### 15. Parallel omok controller and board

- [ ] Add `ui/round/src/omokCtrl.ts`.
- [ ] Add `ui/round/src/omokGround.ts`.
- [ ] Add `ui/round/src/view/omokMain.ts`.
- [ ] Add `ui/round/src/view/omokReplay.ts`.
- [ ] Keep ownership local to omok:
  - board rows
  - current turn
  - last move
  - clocks
  - ply/replay cursor
  - loading/end state
- [ ] Do not branch through all of `ui/round/src/ctrl.ts`.
- [ ] Do not mount `Chessground` on the omok path.

Acceptance:

- [ ] Omok live play works without `CgApi`, promotion, premove, or crazyhouse state.

### 16. View cleanup for omok MVP

- [ ] Hide or skip chess-only UI on omok rounds:
  - material diff
  - crazyhouse pockets
  - SAN replay rendering
  - keyboard move
  - voice move
  - blindfold
  - analysis affordances that assume chess notation
- [ ] Edit the minimal shared view files only where necessary.

Acceptance:

- [ ] Omok round pages do not show broken chess widgets or empty SAN panels.

## Manual End-to-End Acceptance Checklist

### Initial boot

- [ ] Two players can open the same omok round URL.
- [ ] Watchers can open the watcher URL and see the same board state.
- [ ] Initial payload includes `data.omok.position.boardRows`, turn, ply, and ruleset.
- [ ] No chessboard preload flashes before hydration.

### Live move path

- [ ] Clicking an empty cell on your turn sends `place`.
- [ ] Clicking an occupied cell sends nothing or is rejected cleanly.
- [ ] Wrong-turn attempts are rejected server-side.
- [ ] Both players receive `omokMove` and redraw to the same board.
- [ ] Spectators receive the same live updates.

### End conditions

- [ ] Five in a row ends the round and emits normal `endData`.
- [ ] Resign still ends the game through existing round controls.
- [ ] Timeout still ends the game through existing round clock flow.
- [ ] Reload/reconnect rebuilds the same board state from persisted omok data.

### Regression guardrails

- [ ] Chess rounds still boot the existing `RoundController`.
- [ ] Chess `move` and `drop` packets are unchanged.
- [ ] Chess replay, premove, promotion, and crazyhouse behavior are unchanged.

## Explicit Non-Goals For The First Slice

Do not block MVP live play on:

- premove or predrop support;
- SAN/FEN compatibility;
- `Chessground` reuse;
- keyboard or voice move parity;
- takeback or draw-claim parity;
- AI play through the round socket;
- full analyse/study integration from the round controller.

## Recommended Execution Order

1. Add the `round -> omok` dependency and repo wiring.
2. Add `data.omok` boot payload and omok replay builder.
3. Add server socket parsing and omok move execution.
4. Add frontend types, socket handler, and omok controller boot.
5. Add omok board/rendering and hide chess-only widgets.
6. Run the manual acceptance checklist above on player, watcher, reconnect, and endgame flows.

## Bottom Line

The safe MVP path is still the same as in the earlier plan, but the codebase now gives one extra concrete shortcut: reuse `modules/omok/src/main/bridge.scala` as the canonical board DTO layer. The live-play checklist should treat `OmokPositionDto` as the source of truth for boot payloads and `omokMove` redraws, while keeping the existing chess round pipeline intact.
