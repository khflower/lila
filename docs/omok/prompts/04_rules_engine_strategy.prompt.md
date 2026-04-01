You are worker 04 for the lila->omok port.

Repository root: /kh_code/lila
Reference materials:
- /kh_code/omok_refs/forbidden_move_reference.py
- /kh_code/RenLib

Goal: propose the best strategy for the omok rules engine and core model layer.

Questions to answer:
- Should the project fork/replace the scalachess-style domain library with a dedicated omok library?
- What should the core omok model include first? (board, move, state, turn, result, forbidden moves, serialization)
- How should forbidden move logic be staged for correctness and testability?
- How useful is RenLib as a reference versus direct reuse?

Deliverable:
- Write a markdown report to /kh_code/lila/docs/omok/reports/04_rules_engine_strategy.md
- Include:
  1. recommended architecture choice
  2. minimal domain model proposal
  3. forbidden-move implementation/testing plan
  4. how to use the provided reference code safely
  5. top risks/blockers

Constraints:
- Analysis only in this wave.
- Do not modify tracked files outside the assigned report path.
- Final message must be exactly: DONE_04
