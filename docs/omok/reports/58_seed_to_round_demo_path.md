# Seed To Round Demo Path

Current `omok/mvp` demo path is still a sidecar on top of an existing lila round.

1. Run the local lila server with the dev CLI endpoint available on `http://localhost:9663/run/cli`, and have `LILA_CLI_TOKEN_DEV` in the shell. This is required because `bin/omok-demo` goes through `bin/cli` into `controllers.Dev.command`. `OmokRoundRepo` is in-memory, so a server restart clears the seeded omok state.
   Before checking server reachability, make sure `bin/omok-demo` can see an executable helper at `bin/cli` or via `LILA_OMOK_CLI_BIN`; the wrapper now fails fast with `omok-demo: CLI helper is missing or not executable: ...` when that local prerequisite is broken.
2. Seed omok sidecar state onto an existing started 8-character `GameId`:
   `bin/omok-demo seed <gameId> [renju|freestyle] [move ...]`
   Example: `bin/omok-demo seed demo1234 renju H8 A1 I8`
3. Open the matching round page for that same game:
   - live input: `/$fullId` on the real player page for that game
   - spectator view: `/$gameId/white` or `/$gameId/black`
4. On page boot, `RoundApi.withOmok(...)` reads `OmokRoundRepo` and injects `data.omok`; the round views detect that field and boot the omok placeholder board/state instead of the chess preload.
5. On a player page, clicking an empty point sends socket `place`; `RoundSocket` -> `RoundAsyncActor` -> `OmokMovePlayer` applies it; success emits `omokMove`; the frontend redraws from the updated omok position. If the move wins, normal round finish runs immediately after.

- Local server is running and exposing `http://localhost:9663/run/cli`.
- `LILA_CLI_TOKEN_DEV` is set in the shell.
- You already have a real started 8-character `GameId` plus at least one real player `fullId` for that round.
- `OmokRoundRepo` is in-memory, so any server restart wipes seeded omok state.

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
- The page shows the 15x15 omok placeholder board plus the Omok state card with turn, ply, last move, and rule set.
- On the player page, only the side to move can click an empty point. That sends socket `place`; backend applies it through `OmokMovePlayer`; success returns `omokMove`; all open tabs redraw from the new omok position.
- If the move ends the game, the page shows finished/winner state and the same finished omok snapshot survives reload.
- If the tab was opened before seeding, or the id does not map to a real round, you do not get the omok path until you seed the real round id and refresh.

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
