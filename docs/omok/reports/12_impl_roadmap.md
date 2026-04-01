# Omok Implementation Roadmap

## Current Checkpoint

`omok/mvp` is past the original rules-engine and live-play bootstrap stage.

Already working:

- `data.omok` round boot
- omok board/state rendering on player and watcher pages
- live `place -> omokMove` move flow
- terminal omok status in the live path
- finished-state retention across reload
- CLI seeding through `bin/omok-demo`

The remaining work is no longer "make omok playable at all". It is now about replacing demo-only seams with coordinator-safe product seams.

## Recommended Phase Order

### Phase 0: Prove The Current Demo Path On Real Runtime

Before changing architecture again, prove the seeded flow on the actual host/runtime:

- `bin/omok-demo` reaches the internal CLI path
- seed -> round boot -> move -> finish -> reload works once end to end
- runtime prerequisites are documented clearly

Exit criteria:

- one reliable internal demo path
- operator failures are separated from code failures

### Phase 1: Add A Native Internal Start Flow

This is the smallest next patch.

Build a narrow omok-specific create/start path that:

- creates a real lila `Game`
- seeds initial omok state for that same `GameId`
- returns black, white, and watcher links
- lands on the already-working omok round runtime

Recommended shape:

- internal direct-create flow
- anonymous seats first
- one ruleset selector at most

Do not start with lobby or challenge integration.

Exit criteria:

- no pre-existing `GameId` required
- first load of returned player links already boots omok mode

### Phase 2: Add Durable Omok Sidecar Persistence

This is the next serious integration wave, not part of the first start-flow patch.

Add a durable omok record keyed by `GameId` with canonical coordinate move storage, then use it to support:

- cache-cold boot
- restart survival
- cleaner follow-on start flow and result integration

Exit criteria:

- live move and seed paths write durable omok state
- `RoundApi.withOmok(...)` can boot from durable state when cache is cold

### Phase 3: Broader Round / Result Cleanup

After start flow and persistence both exist, choose one narrow follow-up:

- broader round-status/result integration
- creator-bound or challenge-oriented start flow
- wider platform surfacing

Exit criteria:

- the next slice is smaller than "make the whole site omok-aware"

## Coordination Rule

Do not combine Phase 1 and Phase 2 into one patch unless there is a hard blocker.

The clean split is:

- Phase 1 removes the seed-first operator dependency
- Phase 2 replaces the cache-only storage seam under the same live-round contract

That keeps each change reviewable and avoids reopening chess-shaped setup and notation code at the same time.

## What To Defer

Still defer these until after native start flow and durable sidecar persistence exist:

- lobby integration
- challenge taxonomy changes
- rating / perf support
- AI opponent
- study / analysis / tree parity
- PGN / import-export work
- tournament / swiss / pool support
- deeper search / profile / moderation integration

## First 3 Coding Milestones From Here

### Milestone 1: Internal Demo Proof

- prove `bin/omok-demo` on the real runtime
- finish the seed -> move -> finish -> reload script once

### Milestone 2: Omok Starter Service

- create a real `Game`
- seed initial omok state for the same `GameId`
- return player and watcher links

### Milestone 3: Durable Omok Sidecar Repo

- persist canonical coordinate moves keyed by `GameId`
- fall back to durable state on cache-cold boot

## Recommended Execution Summary

Default order:

1. prove the current seeded demo path
2. land the small native start-flow patch
3. land durable `GameId`-keyed omok persistence
4. only then choose the next broader integration seam

If the goal shifts from demo momentum to storage durability, swap steps 2 and 3. Do not try to do both at once.
