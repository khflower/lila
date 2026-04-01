# Omok Frontend Implementation Starter

## Purpose

This report turns the earlier frontend analysis into a concrete first implementation slice for Omok without attempting a full UI rewrite. The intent is to reuse the existing analyse page shell, tree navigation, and redraw cycle, then replace the chess-specific board and move plumbing behind narrow seams.

The shortest safe starter is:

1. keep the existing `main.analyse` shell;
2. add an Omok-specific board implementation behind the analyse board seam;
3. keep local move replay and tree navigation;
4. hide or disable chess-only features for Omok;
5. defer round/study/live-play integration until the Omok analyse path is stable.

## Exact Files To Edit First

### Wave 1: Analyse-only starter

1. `ui/analyse/src/ground.ts`

- This is the current board adapter seam for analyse.
- Today it instantiates `Chessground` and wires `events.move` and `events.dropNewPiece` directly to `AnalyseCtrl` callbacks.
- First Omok change should branch here by variant and delegate to a new Omok board module rather than changing the rest of the view tree.
- Keep the exported `render(ctrl)` shape stable so `ui/analyse/src/view/components.ts` does not need structural changes.

2. `ui/analyse/src/ctrl.ts`

- This is the main move pipeline for analyse.
- The first Omok-specific branches belong in:
  - `makeCgOpts`
  - `setChessground`
  - `userMove`
  - `sendMove`
  - `addNodeLocally`
  - `playUci`
- These methods currently assume chess move semantics, promotion, captures, and `chessops` move normalization.
- For Omok, keep the rest of the controller intact and replace only the move encoding / local node creation path.

3. `ui/lib/src/tree/node.ts`

- This is the deepest existing chess-only seam in the analyse path.
- `completeNode` currently derives `pos`, `dests`, `drops`, `check`, and `outcome` from chess FEN plus `chessops`.
- Omok should not overload this with fake chess FEN.
- First real abstraction should happen here: chess keeps the current path, Omok gets its own node completion path based on Omok board state.

4. `ui/analyse/src/interfaces.ts`

- Add Omok-specific payload fields here once backend wiring is ready.
- Do not force Omok into existing chess-only fields like `fen`, `analysis`, or promotion-shaped move data unless it is strictly transitional and isolated.
- This is the right place for an optional Omok analyse payload such as serialized board state, move list, ruleset, and status.

5. `ui/analyse/src/view/components.ts`

- Leave the board wrapper and page layout mostly intact.
- Use small Omok branches here only to hide chess-only panels in the starter:
  - FEN input
  - PGN import/export
  - captured material
  - ceval-only affordances if no Omok engine is wired
- Avoid structural layout rewrites in the first pass.

6. `modules/analyse/src/main/ui/AnalyseUi.scala`
7. `modules/analyse/src/main/ui/ReplayUi.scala`

- These are the server-rendered shells that boot the analyse frontend.
- They should stay mostly unchanged.
- Only touch them if Omok needs extra boot payload fields or an Omok-specific variant class name for styling/feature gating.

### Wave 2: Only after analyse starter works

1. `ui/round/src/ground.ts`
2. `ui/round/src/ctrl.ts`
3. `ui/round/src/interfaces.ts`

- This is the live-play path.
- Do not start here.
- Round has more chess-specific assumptions around premoves, clocks, promotions, drops, and socket move payloads than analyse does.

### Do Not Edit In The First Omok UI Patch

- `ui/analyse/src/study/*`
- `ui/analyse/src/study/relay/*`
- `ui/analyse/src/study/multiBoard.ts`
- `ui/analyse/src/explorer/*`
- `ui/analyse/src/motif/*`
- `ui/round/src/crazy/*`

These areas multiply the state surface and add chess-specific features that are not required for the first Omok playable path.

## Current Event Flow

### Analyse boot flow

1. Server page shell is rendered from `modules/analyse/src/main/ui/AnalyseUi.scala` or `modules/analyse/src/main/ui/ReplayUi.scala`.
2. The page boots the analyse module through `analyse.user`.
3. `ui/analyse/src/start.ts` creates `AnalyseCtrl`, patches the initial view, and stores the controller on `site.analysis`.
4. `ui/analyse/src/view/main.ts` selects the standard analyse view when not in study/relay mode.
5. `ui/analyse/src/view/components.ts` calls `renderBoard`.
6. `renderBoard` calls `ui/analyse/src/ground.ts`.
7. `ground.ts` instantiates the board and passes interaction events back to `AnalyseCtrl`.

### Analyse move flow today

1. Board interaction calls `AnalyseCtrl.userMove`.
2. `userMove` runs promotion handling first.
3. `sendMove` builds an `AnaMove` payload and optionally sends it through the study socket.
4. `addNodeLocally` applies the move locally, creates a new tree node, and calls `addNode`.
5. `addNode` inserts into the shared move tree and jumps to the new path.
6. `jump` updates current node state and calls `showGround`.
7. `showGround` pushes the new board config back into the board renderer and redraws.

### Analyse tree navigation flow today

1. `ui/analyse/src/treeView/treeView.ts` listens for pointer events on move nodes.
2. Clicking a node calls `ctrl.userJump(path)`.
3. `userJump` delegates to `jump(path)`.
4. `jump` updates the active node, board state, auto-shapes, explorer state, and redraw.

This tree flow is worth preserving for Omok. It already gives a good move-history UX without a rewrite.

## Board Abstraction Seams

There are three real seams, not one.

### Seam A: board widget adapter

Files:

- `ui/analyse/src/ground.ts`
- `ui/round/src/ground.ts`

Responsibility:

- translate controller state into a concrete board widget config;
- translate UI pointer events into controller move callbacks.

Omok plan:

- keep chess on `Chessground`;
- add a separate Omok board module rather than bending `Chessground` into a 15x15 stone board;
- keep `render(ctrl)` stable so the rest of analyse does not know which widget is mounted.

### Seam B: variant move application and node completion

Files:

- `ui/analyse/src/ctrl.ts`
- `ui/lib/src/tree/node.ts`

Responsibility:

- validate / normalize local moves;
- create local tree nodes;
- compute per-node legal actions and status.

Omok plan:

- chess keeps `chessops`-based move application;
- Omok gets a variant branch that works from Omok board state and coordinate moves;
- do not fake chess SAN, FEN, promotion, or drops for Omok.

### Seam C: variant-specific shell affordances

Files:

- `ui/analyse/src/view/components.ts`
- `modules/analyse/src/main/ui/AnalyseUi.scala`
- `modules/analyse/src/main/ui/ReplayUi.scala`

Responsibility:

- decide which controls and underboard panels are visible.

Omok plan:

- leave the outer shell intact;
- hide panels whose data model is chess-specific;
- keep move tree, board area, and basic controls.

## Minimal Playable Path

The first Omok UI should be analyse-only, local-first, and intentionally thin.

### Scope

- 15x15 board
- click-to-place stones
- alternate black/white turns
- reject occupied cells
- display last move
- maintain move tree and replay navigation
- display game-over when backend/status data says win

### Explicitly out of scope for this patch

- study mode
- relay
- live round play
- premoves
- drops
- promotion
- PGN/FEN import/export
- ceval integration
- explorer
- voice / keyboard move parity

### Recommended implementation order

1. Add Omok variant gating in analyse boot/controller paths.
2. Mount an Omok board widget from `ui/analyse/src/ground.ts`.
3. Replace local move creation in `AnalyseCtrl` with Omok coordinate placement.
4. Add Omok node completion in `ui/lib/src/tree/node.ts`.
5. Hide chess-only underboard / analysis affordances.
6. Verify local replay and move-tree jumping.
7. Only then begin round/live-play work.

## Omok Move And State Shape

Backend Omok already gives two important constraints:

- board size is `15x15`;
- move notation is coordinate-based, formatted like `A1` through `O15`.

That means the frontend starter should use coordinate placement, not UCI-like `orig+dest` moves.

For the first Omok analyse slice, prefer a move payload shaped like:

```ts
type OmokMove = {
  pos: string; // "A1".."O15"
  path: string;
  ruleset: 'freestyle' | 'renju';
};
```

For board state, do not reuse chess FEN as a long-term representation. The safer direction is an Omok-specific serialized board field carried alongside existing analyse data and consumed only by the Omok node-completion branch.

## Concrete First Patch Plan

### Patch 1: analyse board seam only

- Edit `ui/analyse/src/ground.ts`.
- Add a variant switch:
  - chess variants continue using current `Chessground` path;
  - Omok variant delegates to a new Omok board renderer module.
- No other view files should need large structural changes.

### Patch 2: local Omok move pipeline

- Edit `ui/analyse/src/ctrl.ts`.
- Add Omok branches that:
  - accept a single coordinate placement;
  - skip promotion, drop, and capture logic;
  - create an Omok move payload;
  - create a local tree node from Omok state.

### Patch 3: Omok node completion

- Edit `ui/lib/src/tree/node.ts`.
- Split `completeNode` into:
  - chess node completion;
  - Omok node completion.
- Omok completion should compute:
  - legal empty intersections;
  - status / outcome if already finished;
  - any board metadata needed by the Omok widget.

### Patch 4: trim chess-only UI

- Edit `ui/analyse/src/view/components.ts`.
- Hide FEN/PGN and related inputs for Omok.
- Keep move tree and replay controls visible.

## Why This Is The Lowest-Risk Starter

- It keeps the server page shell unchanged.
- It keeps most of `AnalyseCtrl` intact.
- It reuses the existing tree and navigation UX.
- It avoids destabilizing `ui/round` before the Omok board and node model exist.
- It creates a real board abstraction where the code is already concentrated instead of scattering Omok conditionals through the whole frontend.

## Safe Stub Decision

I did not add a TypeScript stub interface in this patch.

A new unused adapter type would be easy to add, but it would not meaningfully reduce implementation risk by itself. The real risk is not naming the adapter, it is choosing the wrong seam. The seams above are now concrete enough that the next patch can add code directly where it matters.
