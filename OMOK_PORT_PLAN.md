# Omok port plan for lila

## Goal
Turn lila into an omok-focused site while reusing as much of the platform layer as possible:
- accounts / auth
- lobby / matchmaking
- chat / social / moderation
- tournaments / scheduling patterns
- study / replay ideas where reusable

## Core technical reality
lila is deeply chess-centric. The main server depends on `scalachess` artifacts:
- scalachess
- scalachess-play-json
- scalachess-rating
- scalachess-tiebreak

So this is not a skin/theme job. We need a game-domain replacement strategy.

## Recommended architecture direction
1. Keep lila platform modules that are mostly game-agnostic.
2. Introduce an omok domain layer (board, moves, outcomes, rules).
3. Replace or adapt chess-specific notation / analysis / replay paths.
4. Decide the ruleset early:
   - freestyle gomoku
   - renju-style forbidden moves for black (33, 44, overline)
5. Treat engine/analysis integration as a separate layer from the core rules engine.

## Immediate references collected
- `/kh_code/omok_refs/forbidden_move_reference.py`
- `/kh_code/RenLib`

## Likely high-impact areas to inspect next
- game representation and move validation
- round/play flow
- setup / challenge / matchmaking assumptions
- notation / replay / import-export
- rating and performance categories
- puzzle / study / analysis features that assume chess concepts

## Short-term execution plan
1. Finish dependency bootstrap so the repo builds in the container.
2. Map chess-domain entry points in the lila codebase.
3. Create a minimal omok rules module and test suite outside the main path first.
4. Decide whether to fork `scalachess` into an `scalaomok`-style library or embed rules directly.
5. Start with board + move legality + win detection + banned-move detection.

## Rule workstream
The provided forbidden-move reference is useful for:
- overline detection
- double-four detection
- open-three / double-three detection

Need to formalize:
- board coordinates and notation
- exact win condition under renju rules
- whether black-only forbidden moves are required site-wide
- whether variants should exist (freestyle / standard renju)
