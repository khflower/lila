You are worker 02 for the lila->omok port.

Repository root: /kh_code/lila
Goal: analyze the frontend/UI path for board rendering and move interaction, and determine what must change for a 15x15 omok board.

Focus areas:
- package.json
- ui/
- any chessground usage
- board rendering, move input, coordinates, replay UI, clocks if relevant
- public/ assets only if directly relevant

Deliverable:
- Write a markdown report to /kh_code/lila/docs/omok/reports/02_frontend_board_ui.md
- Include:
  1. current board/UI stack and major entry points
  2. chessground coupling and alternatives
  3. minimum UI slice needed for a playable omok board
  4. which UI features can be reused vs replaced
  5. top risks/blockers

Constraints:
- Analysis only in this wave.
- Do not modify tracked files outside the assigned report path.
- Final message must be exactly: DONE_02
