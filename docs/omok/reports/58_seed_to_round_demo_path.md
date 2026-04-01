# Seed To Round Demo Path

Current `omok/mvp` demo path is still a sidecar on top of an existing lila round.

## Runtime Prerequisites

- Local server is running and exposing `http://localhost:9663/run/cli`.
- `LILA_CLI_TOKEN_DEV` is set in the shell.
- `bin/omok-demo` can reach an executable CLI helper (`bin/cli` by default, or `LILA_OMOK_CLI_BIN`).
- You already have a real started 8-character `GameId` plus at least one real player `fullId` for that round.
- `OmokRoundRepo` is in-memory, so any server restart wipes seeded omok state.

Before checking server reachability, you can verify wrapper behavior alone with:

```text
bin/check-omok-demo-wrapper
```

## Seed

Recommended seed:

```text
bin/omok-demo seed <gameId> renju H8 A1 I8
bin/omok-demo show <gameId>
```

Expected helper output shape:

```text
seeded omok round <gameId>: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
omok round <gameId>: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

For a blank reset-to-start seed, use:

```text
bin/omok-demo seed <gameId>
```

## Open The Round

- Player page: `/<fullId>`
- Watcher page: `/<gameId>/white` or `/<gameId>/black`

## Expected Page Behavior

- If the round was seeded before page load, `RoundApi` injects `data.omok` and the round boots in omok mode.
- The page shows the 15x15 omok placeholder board plus the omok state card with turn, ply, last move, rule set, and finished metadata when present.
- On the player page, only the side to move can click an empty point. That sends socket `place`; backend applies it through `OmokMovePlayer`; success returns `omokMove`; all open tabs redraw from the new omok position.
- If the move ends the game, the final `omokMove` payload already carries terminal `status` / `winner`, the local omok state updates immediately, and the normal round finish path follows.
- Reload returns to the same finished omok snapshot because finished omok state is retained until explicit cleanup.
- If the tab was opened before seeding, or the id does not map to a real round, you do not get the omok path until you seed the real round id and refresh.

## What This Path Proves

This path proves:

- round boot from `data.omok`
- live `place -> omokMove`
- finish and reload behavior on the current sidecar architecture

This path does not yet prove:

- native omok game creation
- durable storage across restart
- cache-cold boot from a DB-backed omok record

## Fast Fallback / Reset

Fastest recovery:

```text
bin/omok-demo seed <gameId> renju H8 A1 I8
```

Then hard refresh the player and watcher tabs.

Clean reset:

```text
bin/omok-demo clear <gameId>
bin/omok-demo seed <gameId> renju H8 A1 I8
```

If the helper cannot reach `localhost:9663/run/cli`, the problem is runtime wiring, not the omok seed path on this branch.

## Planned Upgrade Order

Keep this operator flow shape, but change the seams beneath it in two separate steps:

1. native start flow:
   create a real game and initial omok state together so the operator no longer needs a pre-existing `GameId`
2. durable persistence:
   store canonical coordinate moves keyed by `GameId`, then fall back to that durable record when the live cache is cold

Those are distinct follow-up waves. Do not bundle them into one large rewrite.
