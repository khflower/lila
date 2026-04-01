You are worker 08 for the lila->omok port.

Repository root: /kh_code/lila
Goal: determine the minimum runtime/services needed to get a local omok prototype of lila actually running during development.

Focus areas:
- README
- lila.sh
- conf/
- any docker/nix/devenv/bootstrap clues
- service dependencies visible from config or code (redis, mongodb, websocket server, etc.)

Deliverable:
- Write a markdown report to /kh_code/lila/docs/omok/reports/08_dev_runtime_bootstrap.md
- Include:
  1. required services
  2. optional services
  3. likely blockers to first boot
  4. minimal prototype runtime recommendation

Constraints:
- Analysis only.
- Do not modify tracked files outside the assigned report path.
- Final message must be exactly: DONE_08
