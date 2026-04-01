# First Omok Start Scaffold Plan

## Narrow Seam

The narrowest mergeable seam on this branch is no longer in `setup`, `challenge`, or `lobby`.

Those paths would immediately drag in broader pairing and product rules. The repo already has a workable live omok sidecar path once a real round id exists:

- `OmokRoundRepo` can hold authoritative in-memory omok round state
- `RoundApi` already exposes `data.omok` when that state exists
- round pages already boot the omok shell from that preload
- `RoundSocket` and `OmokMovePlayer` already support live `place` -> `omokMove`

The missing piece is the first practical bridge from "real round exists" to "operator can start omok on that round without separate ad hoc seeding steps".

## First Patch

This patch lands a deliberately internal scaffold instead of a full product flow:

- add `OmokStartScaffold` in `modules/round`
- add CLI support for `omok start <fullId> [renju|freestyle]`
- add protected `GET /dev/omok/start/:fullId?ruleSet=...`
- seed a blank omok root state against the real `GameId` derived from that `fullId`
- redirect directly to the real player page for that same round

That gives the branch one operator-facing start entry that is closer to an omok-native round boot, while staying out of challenge creation and lobby seeks.

## Guardrails

The scaffold intentionally does not:

- create games
- accept challenges
- publish seeks
- persist long-term omok state
- infer omok state from preload/read paths

It only seeds active real rounds explicitly and keeps the source of truth on the write path.

## Exit Condition

After this patch, a developer or operator with dev CLI permission can take a real active round and start an omok shell with one step:

- CLI: `omok start <fullId>`
- browser: `/dev/omok/start/<fullId>`

That is still an internal scaffold, but it is a concrete first start flow rather than a raw repo-only seed helper.
