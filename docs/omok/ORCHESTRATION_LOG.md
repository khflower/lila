# Omok Orchestration Log

## 2026-04-01 restart checkpoint

Coordinator restart performed after reviewing the prior Discord thread and the live repo state.

### Recovered facts
- Active branch: `omok/mvp`
- Repo has substantial omok MVP work already landed.
- Build currently compiles with `./lila.sh compile`.
- The previous session left a small dirty state focused on demo CLI hardening:
  - `docs/omok/reports/54_omok_demo_cli_quickref.md` updated
  - `bin/check-omok-demo-wrapper` added
  - `docs/omok/reports/58_seed_to_round_demo_path.md` added
  - `modules/api/src/test/OmokCliRoutingTest.scala` added

### Intent of this checkpoint
1. Preserve the dirty state as a recoverable commit.
2. Split new work into isolated worker branches/worktrees.
3. Keep coordinator-facing handoff docs current so a later session can resume quickly.

### Next orchestration wave
- Worker A: demo CLI hardening / seed-to-round path validation
- Worker B: productized omok start-flow design + concrete first patch plan
- Worker C: round/result/status integration follow-up
- Worker D: persistence/notation/storage integration plan

### Resume checklist
1. Read this file.
2. Read `docs/omok/WORKERS.md`.
3. Check `git branch -vv` and `git worktree list`.
4. Inspect worker output logs under `/kh_code/codex-orch/`.

## 2026-04-01 wave-1 merge checkpoint

### User reporting preference
- Keep detailed execution state in repo/server logs.
- User-facing updates can stay high-level.
- Keep Codex workers busy continuously; avoid idle workers when there is a clear next slice.

### Wave-1 worker harvest
Merged into `omok/mvp`:
- `e457493b29` Handle full-board draws in omok core
- `9c8c5040bc` round: avoid implicit omok live reseed
- `099171fb43` omok: reset stale rapfi adapter handles
- `fda6da8e45` omok: sync fresh rapfi sessions with board state
- `011d35b914` Harden omok demo CLI wrapper path
- `ac5063f25c` Polish omok live status surfaces

Deferred as reference branches for now:
- `worker/start-flow` docs
- `worker/persistence` docs

### Post-merge validation
- `./lila.sh compile`
- focused round tests
- focused omok/rapfi/core tests
- `api/testOnly lila.api.OmokCliRoutingTest`
- Result: all targeted checks passed after merge.

### Next execution bias
Prioritize narrow, mergeable slices that reduce demo risk or unlock the first omok-native start/persistence wave:
1. round lifecycle cleanup ownership
2. frontend turn gating from omok state
3. snapshot/core API terminal status propagation
4. engine session reset keyed by game/ruleset
5. first omok-native start flow scaffold
6. persistence bridge scaffolding or merged planning doc cut

## 2026-04-01 docs synthesis checkpoint

Deferred planning output from `worker/start-flow` and `worker/persistence` has now been folded into the main omok docs.

Canonical coordinator takeaways:

- smallest next patch: native internal omok start flow that creates a real game and seeds initial omok state together
- next serious infrastructure wave: durable `GameId`-keyed omok sidecar persistence with canonical coordinate move storage
- keep the existing round contract stable:
  - `data.omok`
  - `place`
  - `omokMove`
- do not bundle start-flow, persistence, and broader round/result cleanup into one rewrite

Primary doc landing points:

- `docs/omok/reports/12_impl_roadmap.md`
- `docs/omok/reports/13_first_patch_plan.md`
- `docs/omok/reports/03_api_db_notation.md`
- `docs/omok/reports/49_post_95_gap_list.md`
- `docs/omok/reports/53_internal_demo_runbook.md`
- `docs/omok/reports/58_seed_to_round_demo_path.md`
