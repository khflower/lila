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

### 3. Game creation / seeding is still dev-oriented, not productized

We can now seed and demo a known round id, but there is still no normal user-facing omok game creation/start flow.

Why it matters:
- internal demos are realistic;
- product readiness still needs a real create/start path.

### 4. Wider site integration is still intentionally incomplete

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
3. only after that, decide whether the next cut is:
   - productized omok game creation, or
   - deeper finish/result integration across the generic round model.

## Bottom Line

The branch is now past the old ?can it work at all?? stage.

The biggest remaining gaps are no longer core gameplay seams ? they are demo operability, productized start flow, and broader platform integration.
