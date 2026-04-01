# Omok coordinator notes

## Current objective
Port lila toward an omok-focused site while preserving as much platform infrastructure as practical.

## Working assumption
- Target ruleset is renju-like or at least gomoku with black forbidden-move awareness.
- Exact product decision still pending, but forbidden move handling is a first-class concern.

## Initial parallel lanes
1. Backend game domain mapping
2. Frontend board/UI mapping
3. API/DB/notation mapping
4. Rules engine strategy
5. Engine/AI option scan

## Constraints for workers
- Prefer analysis and documentation first.
- Write only to assigned report file.
- Do not modify production code in this wave.

## Confirmed product decisions from user
- Prefer using an existing strong engine over training from scratch for now.
- Rapfi is the default engine candidate unless a lighter alternative is clearly better.
- No immediate training wave; prioritize integration first.
- GPU 3 may be used later if engine/training experiments actually need it.

## Current status snapshot
- Analysis lanes were recovered into report files early on, but `omok/mvp` is no longer analysis-only.
- Implemented on this branch so far:
  - `build.sbt` contains a dedicated `omok` module in the main compile graph.
  - `modules/core/src/main/omok.scala` defines a portable omok API surface for snapshots, engine requests/responses, and shared contracts.
  - `modules/omok/src/main/core.scala` implements the standalone 15x15 rules engine with win detection plus Renju black forbidden-move checks (`overline`, `double-four`, `double-three`).
  - `modules/omok/src/main/CoordinateNotation.scala` and `modules/omok/src/main/replay.scala` provide canonical `A1`..`O15` notation and deterministic replay helpers.
  - `modules/omok/src/main/bridge.scala` and `modules/omok/src/main/coreApi.scala` bridge local omok state into JSON DTOs and the shared `lila.core.omok` types.
  - `modules/omok/src/main/rapfi.scala` and `modules/omok/src/main/rapfiProcess.scala` add Gomocup/Rapfi protocol formatting plus process-client scaffolding.
  - Test coverage exists in `modules/core/src/test/OmokApiTest.scala` and `modules/omok/src/test/*` for rules, replay, notation, DTOs, core-api conversion, and Rapfi adapters/parsers.
- Small runtime placeholder already landed outside the omok module:
  - `modules/api/src/main/RoundApi.scala` reserves an empty `omok` namespace for future analyse boot payloads.
- Not implemented yet:
  - no `round` dependency on `omok`, so live round/socket play is still unwired;
  - no `GameId`-keyed omok persistence/session repo;
  - no `game`, `tree`, `study`, or database notation/storage integration;
  - no frontend round board/controller path for omok;
  - no production caller that actually launches Rapfi or uses omok engine state in real gameplay.
- Current rule-engine scope is still intentionally narrow:
  - wins and Renju forbidden moves are implemented and tested;
  - board-full draw handling and broader product flows are not wired yet.

## Recommended next checkpoint focus
- Treat report `43_round_integration_plan.md` as the current live-play seam document.
- Future implementation waves should assume the reusable omok core exists already and should start at round/session integration, not from rules-engine design.

## Execution plan to 95%

### Operating mode
- Keep parallel Codex workers busy with narrow, low-conflict tasks.
- Prefer 6-12 active lanes during integration, with more only for docs/research/checkpoint work.
- Reassign new work immediately when a lane completes; avoid idle workers.
- Push clean checkpoints after meaningful integration milestones.

### 70% checkpoint target
- Omok core + Rapfi scaffolding already stable.
- Add analyse/server boot seams for omok payloads.
- Add conservative frontend board abstraction seam.
- Lock down round/socket/live-play integration checklist.
- Keep repo pushable and tested.

### 80% checkpoint target
- First real analyse-path omok payload wiring in server + frontend boot path.
- Minimal frontend omok board placeholder or injection seam merged.
- Round/live-play server-side integration helpers started.
- Rapfi process adapter hardened enough for a controlled first real invocation path.

### 90% checkpoint target
- End-to-end internal playable path for a narrow prototype:
  - construct omok game state,
  - send/update moves through a lila-facing path,
  - render/replay on a board path,
  - invoke Rapfi on demand in a controlled stub/real hybrid path.
- Persistence / serialization seams identified and minimally wired.

### 95% checkpoint target
- Pushable MVP branch with:
  - tested omok core,
  - server boot payloads,
  - frontend board seam in place,
  - round/live-play path partially wired,
  - Rapfi bridge callable,
  - docs/checkpoints current enough for final integration wave.
- Remaining work should be mostly polish / full UI completion / wider site integration, not greenfield core design.

### Immediate next waves
1. analyse payload types and server boot seam
2. frontend analyse board abstraction seam
3. round/socket/live integration checklist -> then code
4. pushable checkpoint maintenance
5. additional omok edge-case tests during every wave
