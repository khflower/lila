# Finished Omok Boot Retention

## Goal

Find the smallest retention change that lets a finished omok round survive page reload and boot back with its final board.

## Verified Current State

- `RoundApi.withOmok(...)` only emits `data.omok` when `omokRoundRepo.get(gameId)` returns state, and it currently serializes that state with `OmokAnalyseDto.fromPosition(state.position)` only.
- `ui/round/src/xhr.ts` reloads by refetching the normal player or watcher round JSON, and `ui/round/src/ctrl.ts` replaces the whole `RoundData` from that response. There is no separate omok replay-on-reload path.
- `OmokRoundRepo` is the only current boot source for omok round state, and it stores `PositionSnapshot` plus moves.
- `OmokMovePlayer.preview(...)` already knows when a move is terminal and exposes that as `PlaceAccepted.terminalStatus`, but that terminal result is not retained in `OmokRoundState`.
- `RoundSocket` removes omok state on `FinishGame` and `DeleteUnplayed`.

## Implication

Finished-round reload is blocked by retention, not by frontend reload mechanics.

If a real omok finish eventually publishes `FinishGame`, the current branch will immediately drop the only source that can rebuild `data.omok`. After that:

- generic round JSON may still have `game.status` and `game.winner`;
- the omok sidecar disappears;
- reload comes back without the final omok board snapshot.

## Narrowest Plan

### 1. Treat this as a boot-data problem

Do not add a new reload endpoint or socket replay path. The existing reload flow is already correct once round JSON can still expose `data.omok`.

### 2. Stop clearing finished omok state on `FinishGame`

The smallest retention cut is:

- keep `RoundSocket` cleanup for `DeleteUnplayed`;
- keep cleanup when the underlying game lookup is actually missing;
- do not remove omok state on ordinary `FinishGame`.

That is enough to let later player/watcher boot requests keep seeing `data.omok`.

This also means actor lifetime does not need to change. Reload boot reads `RoundApi`, not a live round actor.

### 3. Retain a terminal omok summary with the cached state

Retention of the final board alone is almost enough, but a finished-round boot should also know whether the result was a win or draw.

The narrow additive seam is to extend retained omok state with terminal outcome derived from the data already available in `PlaceAccepted.terminalStatus`.

That is smaller than storing a full omok game object, and it avoids reconstructing terminal truth later from generic chess fields.

### 4. Make `RoundApi.withOmok(...)` terminal-aware

For ongoing rounds, keep the current `position` boot contract.

For retained finished rounds, emit:

- the same `position`;
- `omok.status`;
- `omok.winner` when applicable.

The DTO layer already supports this shape through `OmokAnalyseDto`.

## Recommended Cut Line

The narrowest finished-round reload patch should do only this:

1. keep omok repo entries after `FinishGame`;
2. preserve terminal outcome alongside the final position;
3. have `RoundApi.withOmok(...)` include terminal metadata for retained finished rounds;
4. leave frontend reload code unchanged.

## What Not To Do

- Do not keep round actors alive just for reload.
- Do not add a second reconnect protocol for finished omok rounds.
- Do not broaden cleanup changes beyond `FinishGame` unless a real leak shows up.

## Bottom Line

On the current branch, finished omok reload fails because `RoundSocket` erases `OmokRoundRepo` before `RoundApi` can reuse it for `data.omok`.

The narrowest fix is finished-only retention: keep the cached omok snapshot after `FinishGame`, attach terminal outcome to that retained state, and let the existing round JSON reload path boot from it.
