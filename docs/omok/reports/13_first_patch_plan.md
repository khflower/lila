# First Omok Start-Flow Patch Plan

## Recommendation

The first omok-native creation patch should not start in lobby, challenge, or setup taxonomy code.

The smallest viable slice is an internal direct-create flow:

1. create a real lila `Game`
2. seed initial omok state for that same `GameId`
3. return black, white, and watcher links
4. reuse the existing omok round page and socket flow unchanged

This removes the current `bin/omok-demo seed <gameId>` dependency without reopening the larger chess-centric setup stack.

## Scope Boundary

This patch is about native creation, not durable persistence.

Keep the current `OmokRoundRepo` path for the first patch. The durable `GameId`-keyed sidecar repo is the next wave after this lands.

Do not combine:

- direct-create start flow
- durable persistence
- lobby / challenge integration

into one patch.

## Proposed User Flow

1. open `GET /omok/start`
2. choose `renju` or `freestyle`
3. submit
4. server creates a fresh started game with two anonymous human seats
5. server seeds omok state for that same `GameId`
6. result page shows black, white, and watcher URLs
7. opening either player URL boots omok mode on first load

Why anonymous seats first:

- avoids challenge acceptance and invite state
- still produces real player `fullId` links
- is enough for internal demos and end-to-end start-flow proof

## Backend Shape

Add a small orchestration service in `modules/round`, for example:

- `modules/round/src/main/OmokStarter.scala`

Responsibility:

- create a fresh lila `Game`
- insert it through `gameRepo`
- trigger `onStart`
- seed initial omok state for the same `GameId`
- return the created game plus player/watcher links

Critical ordering:

- do not return links until game creation, `onStart`, and initial omok seeding have all completed

Otherwise the first page load can race and boot as chess.

## HTTP Surface

Recommended minimal surface:

- `GET /omok/start`
- `POST /omok/start`

Likely controller home:

- `app/controllers/Setup.scala`

Reason:

- it already owns game-start entrypoints and rate-limit patterns
- it keeps the first patch smaller than adding a whole new controller stack

The `POST` action can stay very small:

- parse `ruleSet`
- call `env.round.omokStarter.create(...)`
- render a tiny result page with the three URLs

## Concrete File List

Must touch:

- `conf/routes`
- `app/controllers/Setup.scala`
- `modules/round/src/main/Env.scala`
- `modules/round/src/main/OmokStarter.scala`
- `modules/round/src/test/OmokStarterTest.scala`

Optional small UI:

- `app/views/omok/start.scala`

Should stay untouched:

- `modules/setup/src/main/SetupForm.scala`
- `modules/challenge/src/main/Challenge.scala`
- `modules/challenge/src/main/ChallengeJoiner.scala`
- `ui/lobby/**`
- `ui/challenge/**`

## Acceptance Criteria

The patch is good enough when:

- an operator can create a new omok round without knowing an existing `GameId`
- no CLI seeding is required
- first load of the returned player URL already boots omok mode
- black and white can both place moves through the existing round socket path
- watcher URL shows the same omok state
- finished-state reload still works exactly as it does in the seeded flow today

## Explicit Non-Goals

Do not include these in the first patch:

- durable omok storage
- lobby modal integration
- challenge page integration
- rating / perf support
- broader result/export cleanup

## Bottom Line

The first patch should solve exactly one missing seam: create a real game and initial omok state together, then hand off to the already-working round runtime.
