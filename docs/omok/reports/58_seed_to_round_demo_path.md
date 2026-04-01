# Seed To Round Demo Path

Current `omok/mvp` demo path is still a sidecar on top of an existing lila round.

## Runtime Prerequisites

- Local server is running and exposing `http://localhost:9663/run/cli`.
- `LILA_CLI_TOKEN_DEV` is set in the shell.
- `bin/omok-demo` / `bin/omok-start` can reach an executable CLI helper (`bin/cli` by default, or `LILA_OMOK_CLI_BIN`).
- You already have a real started 8-character `GameId` plus at least one real 12-character player `fullId` for that round.
- That `fullId` starts with the `gameId`, so `demo1234abcd` maps back to `demo1234`.
- `OmokRoundRepo` is in-memory, so any server restart wipes seeded omok state.

Wrapper-only smoke checks:

```text
bin/check-omok-demo-wrapper
bin/check-omok-start-wrapper
```

## Fastest Start Path

If you already know a real player fullId, the quickest path is now:

```text
bin/omok-start demo1234abcd freestyle
```

This should return a message like:

```text
started omok scaffold demo1234 -> /demo1234abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-
```

That gives you a playable omok scaffold without manually seeding moves first.

## Seed / Inspect Path

If you want a deterministic non-empty board instead, use:

```text
bin/omok-demo seed demo1234 renju H8 A1 I8
bin/omok-demo show demo1234
```

## Open The Round

- Player page: `/<fullId>`
- Watcher page: `/<gameId>/white` or `/<gameId>/black`

## Expected Page Behavior

- If the round was started or seeded before page load, `RoundApi` injects `data.omok` and the round boots in omok mode.
- The page shows the 15x15 omok placeholder board plus the omok state card with turn, ply, last move, rule set, and finished metadata when present.
- On the player page, only the side to move can click an empty point. That sends socket `place`; backend applies it through `OmokMovePlayer`; success returns `omokMove`; all open tabs redraw from the new omok position.
- If the move ends the game, the final `omokMove` payload already carries terminal `status` / `winner`, the local omok state updates immediately, and the normal round finish path follows.
- Reload returns to the same finished omok snapshot because finished omok state is retained until explicit cleanup.
- If the tab was opened before start/seed, or the id does not map to a real round, you do not get the omok path until you initialize the real round id and refresh.

## Fast Fallback / Reset

Fastest recovery with a fresh scaffold:

```text
bin/omok-start demo1234abcd
```

Clean reset with explicit sidecar wipe:

```text
bin/omok-demo clear demo1234
bin/omok-start demo1234abcd freestyle
```

If the wrappers cannot reach `localhost:9663/run/cli`, the problem is runtime wiring, not the omok path on this branch.
