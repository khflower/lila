# Omok Frontend Live Redraw Contract

## Scope

This note records the current live redraw contract on `omok/mvp` after the frontend payload-shape fix.

It is intentionally narrow:

- exact `omokMove` socket payload shape;
- exact client mutation expected from that payload;
- how that mutation reaches the visible omok overlay;
- caveats that still remain outside the redraw contract.

## Verified Current State

- `modules/round/src/main/OmokEvent.scala` defines `omokMove` as a `{ move, position }` payload.
- `modules/omok/src/main/bridge.scala` defines the canonical DTOs for both the incremental socket payload and the boot/reload snapshot.
- `ui/round/src/interfaces.ts` now matches that envelope with `ApiOmokMove { move, position }`.
- `ui/round/src/socket.ts` forwards `omokMove` unchanged to `RoundController.apiOmokMove(...)`.
- `ui/round/src/ctrl.ts` mutates `data.omok.position` from `o.position` and also updates generic round turn metadata from that same position snapshot.
- `ui/round/src/view/omokPlaceholder.ts` and `ui/round/src/view/omokState.ts` both redraw from `ctrl.data.omok.position`.
- `modules/api/src/main/RoundApi.scala` boot/reload still expose `data.omok` from `OmokAnalyseDto.fromPosition(...)`, so initial load and reconnect use the same `position` DTO shape as live `omokMove`.

## Contract

### 1. Socket payload

The current server contract for a live omok move is:

```ts
type OmokRoundMove = {
  key: string;
  row: number;
  col: number;
};

type OmokRoundPosition = {
  boardSize: number;
  boardRows: string[];
  turn: string;
  ruleSet: string;
  ply: number;
  lastMove?: OmokRoundMove;
  moves?: OmokRoundMove[];
};

type ApiOmokMove = {
  move: OmokRoundMove;
  position: OmokRoundPosition;
};
```

Notes:

- `position.boardRows` is the authoritative full-board redraw snapshot.
- `position.turn` is the authoritative next-to-move value.
- `position.lastMove` and `position.moves` are move objects, not strings.
- `move` duplicates the just-played point, but current frontend redraw does not depend on it directly.

### 2. Boot and reconnect payload

The current boot/reload contract is:

```ts
type RoundData = {
  omok?: {
    position: OmokRoundPosition;
  };
};
```

The important constraint is that `data.omok.position` and `omokMove.position` are produced from the same backend DTO family. A client that reloads should see the same board snapshot shape that a connected client receives incrementally.

## Expected Client Mutation

The current `apiOmokMove(...)` contract in `ui/round/src/ctrl.ts` is:

1. Ignore the event if `data.omok` is absent.
2. Replace `data.omok.position` with `o.position`.
3. Derive generic round turn metadata from that same snapshot:
   - `data.game.turns = position.ply`
   - `data.game.player = position.turn` when it is `white` or `black`
4. Call `setTitle()`.
5. Trigger `redraw()`, `onChange()`, and `server.alive()`.

The live redraw contract is therefore full-snapshot replacement, not local incremental patching.

The frontend currently treats `o.position` as authoritative for:

- board stones via `position.boardRows`;
- last-move highlight via `position.lastMove`;
- state pill text via `position.turn`, `position.ply`, `position.lastMove`, `position.ruleSet`;
- placement clickability indirectly through generic `data.game.player`, because `renderOmokPlaceholder.ts` still gates `canPlace` with `isPlayerTurn(ctrl.data)`.

## Verification Anchors

Existing lightweight tests already pin the contract that matters here:

- `modules/round/src/test/OmokSocketTest.scala`
  - asserts the exact JSON payload shape for `OmokEvent.MovePayload`;
  - asserts the versioned round-socket `omokMove` envelope preserves that JSON;
  - asserts the accepted payload after a rejected move still matches the expected redraw snapshot.
- `modules/round/src/test/OmokReconnectStateTest.scala`
  - asserts reconnect JSON from `OmokAnalyseDto.fromPosition(...)` matches the latest accepted live payload `position`.

Together, those tests verify the key invariant for this report:

- live `omokMove.position` and reload `data.omok.position` stay aligned.

## Remaining Caveats

- The current redraw contract is board-and-turn only. `apiOmokMove(...)` does not update generic round status, winner, clocks, or draw flags.
- `move` is present in the socket payload but is not currently used by the redraw path. The frontend redraw depends on `position`.
- `renderOmokPlaceholder.ts` still decides clickability from generic round state, not directly from `data.omok.position.turn`. This works only because `apiOmokMove(...)` now mirrors `position.turn` into `data.game.player`.
- `modules/api/src/main/RoundApi.scala` currently builds `data.omok` with `OmokAnalyseDto.fromPosition(...)`, not the fuller `OmokGameDto`. That means boot/reload still does not carry omok status or winner through the `omok` sidecar.
- If a page boots without `data.omok`, incoming `omokMove` is effectively ignored by the current controller branch.

## Bottom Line

On the current branch, the frontend live redraw contract is:

- server sends `omokMove` as `{ move, position }`;
- client replaces `data.omok.position` with `position`;
- client mirrors `position.ply` and `position.turn` into generic round metadata;
- the visible omok board and state pill redraw entirely from that replaced snapshot.
