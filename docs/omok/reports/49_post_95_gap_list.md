# Omok Post-95% Gap List

## Scope

This is a short checkpoint for the remaining gap between the current `omok/mvp` branch and a convincing internal demo.

It is based on the branch as it exists now, not on earlier pre-live-play plans.

## Verified Current State

- `modules/api/src/main/RoundApi.scala` already exposes `data.omok` when `OmokRoundRepo` has state for the round.
- `modules/round/src/main/ui/RoundUi.scala`, `app/views/round/player.scala`, and `app/views/round/watcher.scala` already preload an omok shell instead of server-rendering a visible chess board when `data.omok` is present.
- `ui/round/src/view/omokPlaceholder.ts` already renders a visible 15x15 clickable overlay board and sends `place`.
- `modules/round/src/main/RoundSocket.scala` already parses `r/place`, and `modules/round/src/main/RoundAsyncActor.scala` already routes `HumanPlace` into `OmokMovePlayer.place(...)` and emits `omokMove` on success.
- `modules/round/src/main/RoundSocket.scala` already removes omok repo state on `FinishGame` and `DeleteUnplayed`.

## Smallest Remaining Blockers

### 1. Fix the live `omokMove` payload mismatch

- The server emits `omokMove` as `{ move, position }` in `modules/round/src/main/OmokEvent.scala`.
- The frontend still consumes a flatter top-level contract in `ui/round/src/interfaces.ts` and `ui/round/src/ctrl.ts`.
- Why this is first:
  the current happy path can broadcast a legal move, but the client does not have a reliable live redraw path without falling back to reload.

### 2. Make turn handoff and clickability follow omok state

- `ui/round/src/view/omokPlaceholder.ts` still gates placement from generic round turn via `isPlayerTurn(ctrl.data)`.
- `modules/round/src/main/RoundAsyncActor.scala` applies omok state changes, but it does not update the generic round turn/clock/status surface in lockstep.
- Why this is next:
  after Black places, White can remain non-clickable locally while Black still appears clickable and only learns via resync.

### 3. Add one deliberate seed path for demo rounds

- `modules/api/src/main/RoundApi.scala` only reads existing omok repo state; it does not seed it.
- There is still no production create/start hook on this branch that initializes `OmokRoundRepo` for a round before page boot.
- Why this is still a blocker:
  the current branch needs manual repo seeding, which is too fragile for a repeatable internal demo.

### 4. Carry omok terminal state into the normal round finish surface

- `modules/round/src/main/OmokMovePlayer.scala` persists the omok snapshot, but the accepted `HumanPlace` path currently stops at socket publish.
- `modules/api/src/main/RoundApi.scala` builds round boot omok JSON from `OmokAnalyseDto.fromPosition(...)`, so it does not currently expose omok status/winner on boot.
- Why it matters:
  the branch can show a partial move loop, but it still cannot tell a convincing end-to-end win/finish story.

### 5. Close the remaining stale-state cleanup hole

- Cleanup is already wired for `FinishGame` and `DeleteUnplayed`.
- `modules/round/src/main/RoundAsyncActor.scala` `Stop` still terminates the actor without owning omok repo cleanup directly.
- Why this is last:
  it is not needed for the first demo walk-through, but it is the main remaining integrity risk once the demo loop works.

## Recommended Demo Cut Line

The smallest convincing demo from here needs only four things beyond the current branch:

1. live redraw from `omokMove`;
2. correct turn handoff and clickability;
3. deterministic round seeding;
4. one visible finish path.

Everything else can remain explicitly MVP-only for the demo.
