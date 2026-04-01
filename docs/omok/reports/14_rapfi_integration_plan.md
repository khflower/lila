The environment blocked all filesystem and shell operations with the same `bwrap` namespace error, so I could not write `/kh_code/lila/docs/omok/reports/14_rapfi_integration_plan.md`. I’m pasting the exact report content here so it can be applied out-of-band to that path.

```md
# Rapfi Phase-1 Integration Plan

## Scope and assumptions

This phase assumes:

- We want to use the existing Rapfi engine as-is.
- No model training, no opening-book generation, and no engine-core changes are required for phase 1.
- The immediate product goals are:
  - bot move generation for live games
  - asynchronous post-hoc analysis for completed or in-progress games
- The safest first integration is to treat Rapfi as an external engine process behind a narrow Omok-specific adapter.

## 1. Minimal integration architecture

Recommended phase-1 shape:

```text
lila-omok app
  -> OmokEngineFacade
      -> RapfiAdapter
          -> Rapfi process supervisor
              -> rapfi engine binary
```

Minimal responsibilities:

- `OmokEngineFacade`
  - single internal interface used by bot play and analysis jobs
  - accepts Omok game state in lila format
  - returns normalized engine outputs
- `RapfiAdapter`
  - translates between lila Omok position/move representation and whatever command/state protocol Rapfi already supports
  - handles board size, side-to-move, move encoding, limits, and error normalization
- `Rapfi process supervisor`
  - starts engine processes
  - applies timeouts and memory limits
  - restarts unhealthy processes
  - serializes requests per engine instance

Minimal normalized API:

- `play(position, limits) -> bestMove, optionalPv, optionalScore, metadata`
- `analyze(position, limits) -> pv, score, depth/nodes/time, metadata`
- `health() -> ready | degraded | failed`

This keeps all Rapfi-specific assumptions in one place and avoids phase-1 coupling between lila game logic and engine protocol details.

## 2. Process/service boundary recommendation

Recommendation: run Rapfi out-of-process, supervised by the Omok application or a small local engine worker service.

Phase-1 preference:

- If deployment is simple and single-hosted, use a local worker/process boundary first.
- If Omok already has a job/executor layer, put Rapfi behind that layer instead of embedding it directly in the request path.

Why this boundary is pragmatic:

- Rapfi is an existing engine, so process isolation is the cheapest way to contain crashes, leaks, and protocol deadlocks.
- Engine lifecycle, timeouts, and concurrency are operational concerns, not game-domain concerns.
- Swapping Rapfi later for another engine or for a wrapped library stays cheap if the app talks only to `OmokEngineFacade`.

Recommended boundary rules:

- Never call the engine directly from a synchronous web request that must stay low-latency under load.
- Use a dedicated engine worker pool for bot games.
- Use a separate lower-priority worker pool or queue for analysis.
- Limit each engine instance to one active request at a time unless Rapfi explicitly supports safe multiplexing.

## 3. Data flow for bot move and async analysis

### Bot move flow

```text
game event / bot turn
  -> build current Omok position from game state
  -> OmokEngineFacade.play(position, moveTime or node budget)
  -> RapfiAdapter encodes position and limits
  -> supervised Rapfi instance searches
  -> adapter parses best move
  -> app validates move against legal moves
  -> move is submitted to game actor/state machine
  -> result and telemetry are recorded
```

Operational notes:

- Validate the returned move before applying it.
- If the engine times out or fails, fall back to a simpler legal-move policy rather than stalling the game.
- Record enough metadata to debug strength and latency:
  - engine version
  - requested limit
  - elapsed time
  - optional depth/nodes/score

### Async analysis flow

```text
game completed or analysis requested
  -> enqueue analysis job with game id and target ply set
  -> worker reconstructs position(s)
  -> OmokEngineFacade.analyze(position, analysis budget)
  -> RapfiAdapter issues search request(s)
  -> worker stores normalized analysis output
  -> UI/API reads cached analysis when available
```

Recommended analysis output for phase 1:

- best move
- principal variation if available
- score or win-rate surrogate if Rapfi exposes one
- engine metadata
- timestamped cache entry keyed by game id plus ply

Important separation:

- Bot play is latency-sensitive and should use short bounded searches.
- Analysis is throughput-oriented and can use larger budgets, retries, and queueing.

## 4. What to prototype first

Prototype the thinnest end-to-end slice that proves the boundary:

1. Launch Rapfi as a supervised child process.
2. Send one position and receive one legal best move.
3. Normalize the result into a tiny internal response object.
4. Wire that response into a single bot-move call path.
5. Add a basic async job that analyzes one stored position and writes a cached result.

Acceptance criteria for the first prototype:

- Engine startup and shutdown are reliable.
- A legal Omok position can be translated to Rapfi and back.
- The engine returns a legal move within a fixed timeout.
- Bot move fallback works when the engine crashes or exceeds budget.
- Analysis jobs do not block live play capacity.

What not to optimize yet:

- absolute playing strength
- rich multi-PV output
- distributed engine scheduling
- deep observability beyond basic latency/error counters

## 5. What to postpone

Postpone these until the phase-1 boundary is stable:

- In-process/library integration.
- Any Rapfi training or evaluation pipeline.
- Opening books, self-play data generation, or tuning loops.
- Multi-engine arbitration or strength tiering.
- Streaming live analysis during active games.
- Complex score calibration for UI if Rapfi scores are not already user-meaningful.
- Horizontal engine microservices unless local worker saturation becomes a real bottleneck.
- Persistent engine sessions tied to individual games beyond what is needed for simple performance.

## Practical recommendation

The most pragmatic phase-1 plan is:

- keep Rapfi external
- build one Omok-specific adapter
- separate live bot play from queued analysis
- prove legal move generation first
- defer strength and scale work until the integration boundary is stable

That gives lila->omok a reversible path to ship engine-backed bot play and basic analysis without committing early to engine-core changes or training infrastructure.
```
