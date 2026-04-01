# Omok Finish Surface Plan

## Goal

Show a believable omok finish/winner state in an internal demo without refactoring the global chess-oriented round/game model.

## Current State

- `OmokMovePlayer` updates `OmokRoundRepo` with a new `PositionSnapshot` after each accepted place.
- `RoundAsyncActor` emits `omokMove` on success.
- `RoundApi` boot/reload exposes `data.omok` through `OmokAnalyseDto.fromPosition(...)`.
- The current frontend omok surfaces redraw from `data.omok.position` only.

What is still missing:
- no visible omok status/winner path on boot or live redraw;
- no explicit frontend handling for omok terminal outcome;
- no internal demo surface for "this side won" beyond raw board state.

## Smallest Realistic Path

### Step 1 ? Extend the omok sidecar, not the global round model

Recommended.

Add optional terminal metadata to the omok payload family rather than trying to force it through the existing chess round status path immediately.

Suggested shape:

```ts
omok: {
  position: OmokRoundPosition,
  status?: string,
  winner?: 'white' | 'black'
}
```

Why this is the smallest path:
- current omok UI already reads from `data.omok`;
- boot and live redraw already have an omok-specific side channel;
- it avoids broad changes to generic round clocks/status/winner assumptions.

### Step 2 ? Populate terminal metadata from the omok domain snapshot

Backend source of truth should stay in the omok domain.

Minimal implementation route:
- derive `status` / `winner` from the omok `Game` reconstructed in `OmokMovePlayer` after an accepted move;
- carry that into the persisted omok sidecar state or compute it when building DTOs;
- expose it in both:
  - live `omokMove` payload, and
  - boot/reload `data.omok` payload.

### Step 3 ? Render a tiny omok-finished banner in the current overlay UI

Do not wait for full round-end integration.

The smallest demo-ready surface is:
- a small state pill/banner above or inside the omok overlay;
- text like:
  - `Black wins (omok)`
  - `White wins (omok)`
  - `Finished: renju forbidden move` if applicable.

That is enough for a convincing internal demo without reworking the full round status stack.

## Why Not Force It Through Generic Round Status First

That route is heavier because:
- generic round status/winner is currently chess-oriented;
- clocks, finish events, and rating/perf consequences are still bound to the normal game model;
- we do not yet have a full omok-specific finish lifecycle wired through all of round.

For a demo, the omok sidecar can carry the truth while the generic round shell remains mostly unchanged.

## Minimal Implementation Order

1. add optional `status` / `winner` to the omok DTO shape;
2. compute terminal values from the accepted omok move result;
3. include them in `omokMove` payload;
4. include them in boot/reload `data.omok` payload;
5. render a tiny frontend banner/state pill from those fields.

## Demo Pass Condition

For internal demo purposes, finish support is good enough if:
- the final accepted move updates the board;
- the winner is visible without needing logs or DB inspection;
- reload/reconnect shows the same winner and finished board state.

That is the smallest convincing finish story from the current branch.
