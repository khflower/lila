You are worker 03 for the lila->omok port.

Repository root: /kh_code/lila
Goal: analyze notation, API, persistence, replay/search implications of switching from chess to omok.

Focus areas:
- modules/api
- modules/gameSearch
- modules/study and modules/analyse when notation/replay assumptions are visible
- imports/exports, PGN/FEN-like assumptions, move serialization, game storage formats
- any obvious DB schema assumptions from the codebase

Deliverable:
- Write a markdown report to /kh_code/lila/docs/omok/reports/03_api_db_notation.md
- Include:
  1. likely notation/storage assumptions tied to chess
  2. what an omok notation/storage layer could look like
  3. API surfaces likely to break first
  4. search/replay/study implications
  5. top risks/blockers

Constraints:
- Analysis only in this wave.
- Do not modify tracked files outside the assigned report path.
- Final message must be exactly: DONE_03
