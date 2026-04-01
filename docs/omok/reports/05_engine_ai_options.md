# Engine / AI Options for an Omok Site

## Scope

This is a phase-planning note. I could not inspect `/kh_code/RenLib` or `/kh_code/rapfi` directly in this session because local command and write tools failed at sandbox startup, so protocol/licensing specifics below should be verified before implementation.

## Recommendation Summary

For the first site prototype, use **Rapfi as the initial engine** and keep it **outside lila as an external process/service**. Use **RenLib as data**, not as a live engine component.

Phase 1 should focus on:
- bot play,
- async post-game analysis,
- offline puzzle generation,
- a small opening/reference corpus.

Phase 2 should expand to:
- a dedicated engine service,
- worker pools and caching,
- deeper analysis,
- RenLib-backed opening/problem pipelines,
- larger-scale puzzle mining.

## 1. Recommended engine strategy for phase 1 / phase 2

### Phase 1

Use Rapfi as the only engine in the stack.

Recommended scope:
- one or two fixed-strength bots,
- synchronous `best move` for bot play with strict time limits,
- async game/position analysis via background jobs,
- offline puzzle generation and validation,
- no embedded engine code in lila.

Why:
- a prototype needs a credible tactical engine more than a broad AI stack,
- one engine reduces complexity,
- Rapfi is well-suited to move choice, tactical verification, and forced-line checks,
- keeping it external avoids coupling the web app to engine lifecycle and failure modes.

### Phase 2

Promote the wrapper into a small engine platform.

Add:
- separate bot and deep-analysis workers,
- analysis cache keyed by normalized position + ruleset + engine version,
- offline generation workers,
- RenLib-derived opening trees and puzzle seeds,
- support for multiple engine profiles or a second engine later if needed.

## 2. Integration shape

## Recommendation: external service/process, not embedded in lila

Rapfi should be integrated as a **separate process boundary** rather than linked into lila.

Reasons:
- fault isolation,
- CPU and concurrency control,
- easier upgrades and tuning,
- simpler deployment rollback,
- no need to expose engine internals inside the app runtime,
- easier future replacement if the engine choice changes.

### Best near-term shape

Two acceptable options:

1. **CLI/process wrapper**
- lila submits `best move` / `analyze` jobs,
- a worker launches or reuses Rapfi subprocesses,
- the wrapper normalizes output into a stable internal API.

2. **Local engine service**
- lila calls a narrow API such as `bestMove`, `analyzePosition`, `solveTactic`,
- the service owns process pooling, time controls, retries, and parsing.

If Rapfi already has a stable machine protocol, keep that inside the wrapper. Do not let lila depend directly on raw engine commands.

### What not to do in Phase 1
- do not embed the engine in the main server,
- do not run deep analysis on the request path,
- do not hard-code engine-specific output into product contracts too early,
- do not commit to a ruleset implementation before foul/opening-rule behavior is verified.

## 3. What role Rapfi could play

### Analysis
Rapfi can provide:
- best move,
- candidate lines / PVs,
- tactical win/loss pressure,
- forced-win checks such as VCF/VCT-style verification,
- blunder detection from missed tactical lines or eval swings.

For a prototype, that is enough for useful post-game review.

### Bot play
Rapfi can power:
- fixed-strength bots via depth/time/node limits,
- training bots with opening seeds,
- tactical sparring modes.

This is the fastest route to a credible site bot.

### Puzzle generation
Rapfi is especially valuable as a **validator and scorer**:
- confirm whether a position has a forced win,
- verify uniqueness or near-uniqueness of the solution,
- measure line depth and branching,
- reject noisy positions with many equivalent first moves,
- estimate difficulty from forcing depth and defensive resources.

## 4. What RenLib can contribute

RenLib is most useful as a **reference corpus / opening library / problem seed source**.

Potential contributions:
- opening-tree data,
- common human continuations,
- curated tactical motifs,
- candidate positions for puzzles,
- example forced-line positions,
- regression material to compare engine output against known lines.

Best division of labor:
- **RenLib proposes candidates and structure**
- **Rapfi verifies and scores them**

RenLib should not be treated as the authoritative live solver. Its value is as curated source material.

## 5. Puzzle / VCF generation ideas

### Practical generation pipeline
1. Collect seed positions from site games, RenLib branches, and known tactical examples.
2. Filter for positions with forcing potential.
3. Run Rapfi to detect winning/critical tactical continuations.
4. Keep only positions where:
- there is one clearly best move,
- wrong moves fail clearly,
- the line is instructional rather than arbitrary,
- the rule logic is understandable for users.
5. Deduplicate by normalized board state and line similarity.
6. Assign difficulty from depth, branching, and defense quality.
7. Store proof/analysis data for later revalidation.

### Good early puzzle categories
- find the winning attack,
- find the only defense,
- continuation of a forcing line,
- opening trap punishment,
- VCF-style forced-win trainer,
- refutation of a tempting but losing move.

### VCF-specific value
If the target omok ruleset makes VCF-style content relevant, this can become a strong early mode:
- "find the forced win",
- "continue the threat chain",
- "does this position contain a forced win?",
- "find the only defense".

RenLib can seed those positions; Rapfi can certify them.

## 6. Minimum viable engine plan for an early prototype site

The smallest credible plan is:
- one Rapfi-based external worker,
- one narrow internal API for `bot move`, `best move`, and `analyze position`,
- async analysis queue,
- offline puzzle generation,
- optional small opening seed set derived from RenLib.

Concrete product outcome:
- play vs bot,
- simple post-game analysis,
- initial tactics section.

That is enough for an early prototype without overcommitting the architecture.

## 7. Top risks / blockers

### 1. Ruleset mismatch
Biggest blocker:
- freestyle vs forbidden-move variants,
- overline handling,
- opening rules,
- board-size assumptions,
- result adjudication.

This must be verified before integration begins.

### 2. Protocol / operability uncertainty
Need to confirm:
- how Rapfi is invoked,
- whether it supports a stable machine-readable protocol,
- what search controls are available,
- how persistent processes behave,
- whether PV/score/node data are easy to parse.

### 3. License and redistribution
Both Rapfi and RenLib need license review for:
- server-side use,
- binary redistribution,
- bundled data reuse,
- publication of derived puzzle content.

This is a release blocker.

### 4. Performance and cost
Risks:
- CPU-heavy analysis,
- concurrency spikes,
- competition between live bot play and batch jobs,
- noisy-neighbor impact on the main app host.

This supports the case for an external engine service.

### 5. Puzzle quality
Engine-found wins do not automatically make good puzzles.
Common failure modes:
- multiple equivalent moves,
- ugly or non-instructive lines,
- duplicated motifs,
- positions that depend on obscure rule details.

### 6. RenLib ingestion complexity
Possible work:
- format conversion,
- move-tree normalization,
- metadata extraction,
- deduplication,
- line validation against the engine.

## Final recommendation

- **Phase 1:** Rapfi only, wrapped externally. Use it for bot play, async analysis, and offline puzzle validation. Use RenLib only as a seed/reference corpus.
- **Phase 2:** turn the wrapper into a small engine platform with caching, worker pools, and a RenLib-backed opening/problem pipeline.

If only one architectural decision is made now, it should be this: **keep the engine boundary external to lila until the omok ruleset, engine protocol, and operating profile are proven.**
