# Omok Rules Engine And Core Model Strategy

## 1. Recommended Architecture Choice

Recommendation: replace the scalachess-style domain library with a dedicated omok domain library, but keep the outer integration surface lila-friendly.

That means:

- Do not hard-fork chess domain code and keep extending it.
- Do build a small, pure omok library that owns board state, move legality, result detection, and serialization.
- Do preserve familiar lila layering where it still helps integration, for example `Board`, `Situation`, `Game`, `Move`, `Status`, and serializers/adapters.

Why this is the better long-term choice:

- Chess abstractions are the wrong center of gravity. Omok has no piece hierarchy, no check/checkmate, no captures, no castling, and no move generation tree shaped like chess.
- The one genuinely hard rule area in omok is forbidden-move analysis. That logic should be explicit and first-class, not encoded as a variant patch over chess concepts.
- A dedicated library reduces accidental coupling to chess-only assumptions such as FEN/PGN/UCI semantics, attack maps, and piece-based legality.
- A thin adapter layer is cheaper than carrying permanent conceptual debt in the core rules engine.

Practical recommendation:

- Build the omok domain library as a new module, not as a deep mutation of existing chess code.
- Match only the lila-facing concepts that matter operationally: turn ownership, move application, legal/illegal move result, game status, replay, and serialization.
- Treat ruleset differences as configuration in the omok library, not as ad hoc conditionals spread across lila.

## 2. Minimal Domain Model Proposal

The first version should stay narrow and model only what the server must answer correctly.

Core types:

- `Pos`
  - Board coordinate on a fixed 15x15 board.
  - Store a linear index internally for fast scans and compact serialization.

- `Color`
  - `Black | White`

- `Cell`
  - Usually derived, not stored separately.
  - Empty or occupied by one color.

- `Move`
  - Placement-only move: `Place(pos)`.
  - Do not add chess-like move richness.
  - Add `Pass` only if a concrete omok ruleset or protocol requires it.

- `Board`
  - Occupancy only.
  - Keep representation simple first: two occupancy sets/bitsets or a flat indexed structure.
  - Expose directional scans for the four line axes needed by win and forbidden checks.

- `RuleSet`
  - Explicit ruleset enum/config.
  - At minimum: freestyle gomoku vs omok/renju-style black forbidden rules.
  - This avoids hard-coding one local convention into the whole engine.

- `Situation`
  - `board + turn + ruleset`
  - Pure legality queries should operate here.

- `Status`
  - `Ongoing | Win(color) | Draw`

- `Game`
  - `situation + status + ply + lastMove`
  - Add move history only if required for replay/export; omok legality itself mostly depends on current board.

- `Legality`
  - `Legal`
  - `Occupied`
  - `OutOfBounds`
  - `GameAlreadyOver`
  - `Forbidden(reason)`

- `ForbiddenReason`
  - `DoubleThree`
  - `DoubleFour`
  - `Overline`
  - Keep this explicit; do not collapse all forbidden outcomes into a generic illegal flag.

- `Serialization`
  - Define one canonical server format early.
  - Suggested fields: board occupancy, side to move, ruleset, ply, status, last move.
  - Import/export adapters for other formats should sit outside the core model.

What should not be in the first cut:

- Opening book logic
- AI search logic
- Rich notation beyond what storage and API boundaries need
- UI-oriented concepts
- Any reuse of chess-specific attack/check semantics

## 3. Forbidden-Move Implementation And Testing Plan

The forbidden logic should be staged as a separate pure subsystem. Correctness matters more than micro-optimization in the first wave.

### Phase 0: Freeze The Rule Spec

Before implementation, explicitly decide:

- Which ruleset is authoritative for ranked play.
- Whether black-only forbidden rules are exactly renju-style or a site-specific omok variant.
- Precedence for edge cases, especially exact-five versus overline and interactions with double-three/double-four detection.

This is the main blocker. "Omok" is not precise enough by itself.

### Phase 1: Geometry And Win Detection

Implement and test:

- Board bounds
- Occupancy checks
- Move placement
- Line extraction in four axes
- Contiguous run counting
- Exact five detection
- Overline detection

This phase should work without any forbidden rules enabled.

### Phase 2: Pattern Classifier

Build a pure analysis layer that classifies the board after a hypothetical move:

- open three candidates
- open four candidates
- exact five
- overline

Important design point:

- Separate pattern classification from final legality resolution.
- First answer "what patterns does this move create?"
- Then answer "under this ruleset, is the move legal?"

That separation makes the code easier to reason about and much easier to test.

### Phase 3: Forbidden Resolver

Add a resolver that takes:

- current `Situation`
- candidate `Move`
- derived pattern classification
- active `RuleSet`

and returns:

- `Legal`
- or `Forbidden(reason)`

This layer should own precedence rules and black/white asymmetry. White legality should stay simple in forbidden-rules variants.

### Phase 4: Tests

Testing should be split into four independent layers:

- Geometry tests
  - index/coordinate conversion
  - directional scans
  - symmetry cases at edges and corners

- Pattern tests
  - exact five
  - overline
  - open three
  - open four
  - cases that look similar but must not classify the same way

- Forbidden resolution tests
  - black forbidden cases
  - white never forbidden under black-only rules
  - exact-five precedence cases
  - occupied and out-of-bounds rejection

- Differential/golden tests
  - compare the Scala implementation against the provided Python forbidden-move reference on a curated corpus of board states
  - record any intentional divergences as explicit test fixtures, not tribal knowledge

High-value additional tests:

- rotation/reflection invariance
- replay tests from real move sequences
- regression fixtures for every historical bug

## 4. How To Use The Provided Reference Code Safely

### `forbidden_move_reference.py`

Best use: treat it as an executable oracle and test-fixture source, not as production code to embed.

Safe usage pattern:

- Read it as behavioral specification for forbidden-move outcomes.
- Extract or hand-build board fixtures from it.
- Run differential tests against it during early implementation.
- Stop depending on it at runtime once the Scala engine is verified.

Why not direct reuse:

- Cross-language embedding adds operational complexity for a core rules path.
- Mutable control flow in a reference implementation often obscures the clean domain boundaries needed in production.
- A literal transliteration tends to preserve reference-code quirks instead of producing a clear model.

### RenLib

Best use: reference corpus, notation/file-format guidance, and edge-case discovery.

Usefulness:

- good for examples of real renju/omok positions
- good for validating import/export assumptions if RenLib compatibility matters
- good for stress-testing forbidden logic against historical positions

Direct reuse value is low unless all of the following are true:

- license is confirmed compatible
- the code is already in the right language/runtime
- the implementation cleanly separates core rules from UI/storage concerns

In most ports, those conditions are not met. RenLib is more useful as a source of fixtures and format knowledge than as a dependency in the server rules engine.

## 5. Top Risks And Blockers

- Rules ambiguity
  - The biggest blocker is lack of a single authoritative definition of "omok" for this project.
  - Forbidden-move semantics vary between freestyle gomoku, Korean omok conventions, and formal renju.

- Hidden chess assumptions in lila integration
  - Existing layers may expect chess-shaped serializers, statuses, notation, or replay semantics.
  - The omok library should be insulated behind adapters early.

- Forbidden-move false positives
  - Double-three and double-four logic is easy to get almost right and still be wrong at edges, blocked lines, and compound-pattern cases.

- Premature optimization
  - It is easy to over-design board representation before the rule semantics are correct.
  - Start with clarity and testability, then optimize hot paths later.

- Reference-code overfitting
  - If the project copies reference implementation structure too closely, it may inherit unclear assumptions or non-canonical behavior.

- Format sprawl
  - If serialization is not fixed early, import/export concerns can leak into the core domain model and make later refactors painful.

## Recommended First Execution Order

1. Lock the authoritative ruleset and forbidden precedence rules.
2. Build the dedicated omok domain library with `Pos`, `Color`, `Move`, `Board`, `RuleSet`, `Situation`, `Game`, `Status`, `Legality`, and `ForbiddenReason`.
3. Ship a legality engine without forbidden rules first, but with exact five/overline detection already tested.
4. Add forbidden pattern classification as a separate pure module.
5. Add ruleset-aware forbidden resolution.
6. Validate with differential tests against the Python reference and curated RenLib-derived positions.
7. Only then wire serializers, API adapters, and broader lila integration.

## Bottom Line

The project should not keep stretching a chess domain library into omok. The clean strategy is a dedicated omok core with a thin lila adapter layer. The first milestone should be a small, pure, well-tested rules model centered on placement legality, win detection, and explicit forbidden-move reasoning. The provided Python reference and RenLib are valuable as specification and test assets, but they should guide the implementation rather than become the implementation.
