You are worker 06 for the lila->omok port.

Repository root: /kh_code/lila
Goal: produce an inventory of where chess/scalachess coupling appears most strongly across the codebase.

Focus areas:
- all Scala sources
- dependency declarations
- imports/usages of scalachess or clearly chess-specific domain objects

Deliverable:
- Write a markdown report to /kh_code/lila/docs/omok/reports/06_scalachess_inventory.md
- Include:
  1. high-frequency coupling hotspots
  2. representative files/modules
  3. a rough severity ranking (hard to replace vs easy to wrap)
  4. recommendations for isolation seams

Constraints:
- Analysis only.
- Do not modify tracked files outside the assigned report path.
- Final message must be exactly: DONE_06
