# Omok Worker Assignments

## Coordinator rules
- Each worker gets its own git worktree and branch.
- Workers should avoid touching files owned by another worker unless explicitly reassigned.
- Every worker must leave a short result note in its final response.

## Wave 1 branches
- `worker/demo-cli` -> demo CLI wrapper, routing test, seed-to-round operability docs
- `worker/start-flow` -> omok-native game creation/start flow plan and first implementation slice proposal
- `worker/round-result` -> broader round finish/result/status integration analysis and actionable patch list
- `worker/persistence` -> storage/notation/persistence seams and next integration slice

## Log locations
- Root orchestration dir: `/kh_code/codex-orch`
- Per-worker prompt: `/kh_code/codex-orch/prompts/<worker>.md`
- Per-worker final message: `/kh_code/codex-orch/results/<worker>.txt`
- Per-worker raw session log: `/kh_code/codex-orch/logs/<worker>.log`
