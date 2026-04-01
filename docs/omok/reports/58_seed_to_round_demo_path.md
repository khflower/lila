# Seed To Round Demo Path

Current `omok/mvp` internal demo path is:

1. Run the local lila server with the dev CLI endpoint available on `http://localhost:9663/run/cli`, and have `LILA_CLI_TOKEN_DEV` in the shell. This is required because `bin/omok-demo` goes through `bin/cli` into `controllers.Dev.command`. `OmokRoundRepo` is in-memory, so a server restart clears the seeded omok state.
2. Seed omok sidecar state onto an existing started 8-character `GameId`:
   `bin/omok-demo seed <gameId> [renju|freestyle] [move ...]`
   Example: `bin/omok-demo seed demo1234 renju H8 A1 I8`
3. Open the matching round page for that same game:
   - live input: `/$fullId` on the real player page for that game
   - spectator view: `/$gameId/white` or `/$gameId/black`
4. On page boot, `RoundApi.withOmok(...)` reads `OmokRoundRepo` and injects `data.omok`; the round views detect that field and boot the omok placeholder board/state instead of the chess preload.
5. On a player page, clicking an empty point sends socket `place`; `RoundSocket` -> `RoundAsyncActor` -> `OmokMovePlayer` applies it; success emits `omokMove`; the frontend redraws from the updated omok position. If the move wins, normal round finish runs immediately after.

Operationally, this is still a sidecar demo on top of an existing lila round. There is no omok-native game creation/start flow yet, and if the game id/fullId does not resolve to a real round, or the tab was opened before seeding and not refreshed, the demo path stops there.
