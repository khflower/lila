You are worker 01 for the lila->omok port.

Repository root: /kh_code/lila
Goal: analyze the backend game-domain architecture and identify the most important chess-specific dependencies that must be replaced or adapted for omok.

Focus areas:
- build.sbt and project/Dependencies.scala
- modules/game
- modules/round
- modules/setup
- modules/challenge
- modules/lobby
- modules/pool
- modules/swiss and modules/tournament only if they have direct game-domain coupling

Deliverable:
- Write a markdown report to /kh_code/lila/docs/omok/reports/01_backend_game_domain.md
- Include:
  1. key Scala modules and responsibilities
  2. exact places where scalachess/chess-specific types appear to be central
  3. the minimum backend slice needed for a playable omok prototype
  4. recommended migration order
  5. top risks/blockers

Constraints:
- Analysis only in this wave.
- Do not modify tracked files outside the assigned report path.
- If you accidentally touch anything else, revert it before finishing.
- Final message must be exactly: DONE_01
