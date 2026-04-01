# Omok Finish Surface Plan

## Goal

Find the narrowest backend path that makes omok finish and winner visible for an internal demo, without broad refactors to the chess-oriented round/game model.

## Verified Current State

- `OmokMovePlayer.preview(...)` reconstructs the next omok game and therefore has terminal truth at move time, but it throws that truth away and only persists `PositionSnapshot` plus moves in `OmokRoundState`.
- `RoundAsyncActor` `HumanPlace` currently stops after `socketSend.exec(Protocol.Out.omokMove(...))`; it does not call `Finisher`, publish `EndData`, or mutate generic round status.
- `RoundApi.withOmok(...)` builds boot/reload omok JSON from `OmokAnalyseDto.fromPosition(...)`, so the omok sidecar currently carries only `position`.
- The generic round finish surface already exists:
  - `lila.game.Event.EndData` carries `winner` and `status`;
  - `ui/round/src/ctrl.ts` already applies that payload to `data.game.winner` and `data.game.status`.
- `RoundSocket` currently clears `OmokRoundRepo` immediately on `FinishGame`.

## Implication

There are really two different targets:

1. live finish visibility for an already-connected client;
2. boot/reload visibility for a finished omok round.

Those are not the same problem on the current branch.

## Narrowest Live Path

The smallest realistic live path is to drive omok terminal results into the existing round finish pipeline.

Why this is the narrowest live path:

- the frontend already understands generic `endData`;
- generic round views already know how to show `game.status` and `game.winner`;
- this avoids inventing a second terminal-state UI contract if the goal is just a believable demo finish.

Minimal backend seam:

1. keep omok terminal detection in the omok domain;
2. expose terminal result from `OmokMovePlayer.preview(...)` or `PlaceAccepted`;
3. in `RoundAsyncActor` `HumanPlace`, when the accepted omok move is terminal:
   - map omok winner color to chess color;
   - call the existing `Finisher` path with a low-commitment status such as `UnknownFinish`;
   - let normal `EndData` publish update the round UI.

This is the smallest path to a live winner banner because `EndData` is already wired end to end.

## Why This Is Not Enough For Boot

Boot/reload cannot rely on generic finish alone, because finished omok board state currently lives only in `OmokRoundRepo`, and that repo is cleared on `FinishGame`.

Current blockers:

- `RoundApi.withOmok(...)` only reads from `omokRoundRepo.get(gameId)`.
- `RoundSocket` removes that state as soon as `FinishGame` fires.
- Generic game boot JSON has `game.status` and `game.winner`, but it does not contain the omok board position.

So if omok is wired into `Finisher` today without anything else:

- live clients can receive `endData` and see a winner;
- refresh/reconnect will lose the omok board sidecar entirely.

That makes generic finish alone a live-only solution, not a complete finish surface.

## Narrowest Boot Path

The smallest backend path for boot/reload is additive omok sidecar metadata, not global round-model work.

The branch already has the DTO support for this:

- `OmokAnalyseDto` already supports optional `status` and `winner`;
- `OmokGameDto.fromGame(...)` already derives both fields from omok `Game.status`.

Minimal backend seam:

1. preserve terminal omok game truth long enough to build boot JSON;
2. switch boot/reload omok JSON from `OmokAnalyseDto.fromPosition(...)` to `OmokAnalyseDto.fromGame(...)` or equivalent terminal-aware state;
3. only clean `OmokRoundRepo` after the final board is no longer needed for reconnect/demo, not immediately on `FinishGame`.

Without that retention step, boot support is not real.

## Recommendation

For the narrowest believable demo path, split the work like this:

### Option A: live-only finish story

Take this if the demo can stay on one connected page.

- add terminal outcome to `PlaceAccepted`;
- route terminal `HumanPlace` through `Finisher` so existing `endData` drives the winner/status UI;
- do not try to solve finished-round reload in the same patch.

This is the cheapest path to "someone wins and the page says so".

### Option B: live plus reload finish story

Take this if the demo must survive refresh/reconnect after the last move.

- do Option A;
- also keep finished omok state available for `RoundApi.withOmok(...)`;
- expose omok `status` and `winner` in the sidecar boot payload;
- delay or relocate `OmokRoundRepo` cleanup for finished rounds.

This is still much smaller than refactoring the global game model, but it is not a one-line seam.

## Recommended Cut Line

The smallest realistic backend plan is:

1. make `PlaceAccepted` carry terminal omok outcome;
2. on terminal `HumanPlace`, call `Finisher` so live `endData` works immediately;
3. if reload matters for the demo, stop clearing finished omok state before boot JSON can read it;
4. then expose boot `omok.status` and `omok.winner` from terminal-aware DTOs.

## Tiny Seam Worth Considering

One compile-safe additive seam is obviously low-risk:

- extend `PlaceAccepted` to retain the reconstructed `nextGame.status`, or a smaller terminal summary, because `OmokMovePlayer.preview(...)` already has that information before it is discarded.

That seam by itself does not make the feature visible, but it cleanly enables either live-path recommendation above.

## Bottom Line

The narrowest path to visible omok finish is:

- live: use the existing generic `EndData` finish pipeline;
- boot: keep terminal omok state alive long enough to build `data.omok`.

Trying to solve boot purely through generic round status fails on the current branch because `FinishGame` cleanup erases the only omok board source.
