# Native Start Runtime Path

Current branch state: the new native internal start path has landed, but it is still a scaffold, not full omok game creation.

- `GET /dev/omok/start/<fullId>?ruleSet=renju|freestyle` exists and calls `controllers.Round.omokStart(...)`.
- That controller only works if `<fullId>` already resolves to a real playable round POV.
- The scaffold seeds or resets blank `OmokRoundRepo` state for that round's `gameId`, then redirects to `/<fullId>`.

## Exact Runtime Prerequisites

- A lila server process must be running for the web route to exist.
- You need a real 12-character player `fullId` for an already-started playable round.
- Your browser session must have `Cli` permission; the route is `Secure(_.Cli)`.
- Omok state is only the in-memory `OmokRoundRepo` sidecar.
- Server restart wipes that state, and round lifecycle cleanup removes it on `FinishGame` / `DeleteUnplayed`.

Not required for the direct native route:

- `LILA_CLI_TOKEN_DEV`
- `bin/cli`
- `POST /run/cli`

## Fastest Operator Path

1. Get a real player `fullId` for the round you want to reuse.
2. Open:

```text
/dev/omok/start/<fullId>?ruleSet=freestyle
```

3. Let the redirect land on:

```text
/<fullId>
```

4. Open watcher tabs for the same `gameId` if needed.

Expected result: the round boots with blank omok state at ply 0 for the selected ruleset.

## Shell Fallback

If you do not have a `Cli`-privileged browser session, the current fallback is still:

```text
bin/omok-start <fullId> [renju|freestyle]
```

That path is slower operationally because it still depends on the dev CLI transport (`bin/cli`, `LILA_CLI_TOKEN_DEV`, and `http://localhost:9663/run/cli`).
