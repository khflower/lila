# Omok coordinator notes

## Current objective
Port lila toward an omok-focused site while preserving as much platform infrastructure as practical.

## Working assumption
- Target ruleset is renju-like or at least gomoku with black forbidden-move awareness.
- Exact product decision still pending, but forbidden move handling is a first-class concern.

## Initial parallel lanes
1. Backend game domain mapping
2. Frontend board/UI mapping
3. API/DB/notation mapping
4. Rules engine strategy
5. Engine/AI option scan

## Constraints for workers
- Prefer analysis and documentation first.
- Write only to assigned report file.
- Do not modify production code in this wave.
