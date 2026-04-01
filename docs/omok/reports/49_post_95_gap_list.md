# Omok Post-95% Gap List

## Scope

This is the current short checkpoint for the remaining gap between `omok/mvp` and a convincing internal demo / next integration cut.

It reflects the branch after the live omok loop, finished-state retention, live terminal payloads, the `bin/omok-demo` wrapper, and the dedicated dev CLI command path landed.

## What Is Already Good Enough

These are no longer the primary blockers:

- live round boot exposes `data.omok`
- omok board shell renders on player/watcher pages
- click-to-place sends `place`
- backend `HumanPlace -> OmokMovePlayer -> omokMove` works
- final move carries terminal omok status/winner in the live payload
- finished omok state survives reload until explicit cleanup
- a small operator wrapper exists: `bin/omok-demo`
- wrapper-only smoke validation exists: `bin/check-omok-demo-wrapper`
- the dev CLI path is now explicit and tested through `CliInput` + `OmokCli`

## Smallest Remaining Blockers

### 1. Demo seeding still depends on runtime environment, not just branch code

`bin/omok-demo` now has a real path, but it still depends on:
- a running local server exposing the internal CLI transport;
- `LILA_CLI_TOKEN_DEV` being available in the shell.

Why it still matters:
- helper logic exists;
- demo reliability still depends on environment wiring, not only repo state.

### 2. Omok finish is visible, but broader round-status integration is still thin

The current branch surfaces terminal omok state well enough for an internal demo, but it is still an omok-sidecar story more than a full round/game-model integration.

Why it matters:
- this is fine for internal demos;
- it is not yet the final product shape for all round/status/result surfaces across the site.

### 3. Game creation is still seed-first, not native

We can now seed and demo a known round id, but there is still no narrow omok-native way to create a fresh round and land in omok mode on first load.

Why it matters:
- this is the smallest user-visible missing seam;
- it is the cleanest way to retire the seed-first demo dependency.

### 4. Omok persistence and notation are still cache-only

Current omok state still lives primarily in `OmokRoundRepo`, an in-memory `TrieMap[GameId, OmokRoundState]`.

Why it matters:

- server restart still wipes omok state;
- cache-cold boot still has no durable fallback;
- start flow and later site integration still lack a real omok storage model.

### 5. Wider site integration is still intentionally incomplete

The branch still does not fully integrate omok into:
- tree / study / analysis ecosystem beyond the current narrow seams;
- storage / notation / long-term persistence flows beyond the live round sidecar path;
- wider tournament / pairing / moderation / result workflows.

Why it matters:
- none of this blocks a convincing internal demo;
- all of it still matters for the next serious integration phase.

## Recommended Near-Term Priority Order

1. prove the demo environment path with `bin/omok-demo` on the actual host/server runtime;
2. run the full internal live demo once with seed -> move -> finish -> reload;
3. land the small native start-flow patch;
4. land durable `GameId`-keyed omok persistence;
5. only after that, choose the next broader round/result/site integration seam.

## Bottom Line

The branch is now past the old "can it work at all?" stage.

The biggest remaining gaps are no longer core gameplay seams. They are **demo operability, native start flow, durable persistence, and broader platform integration**.
