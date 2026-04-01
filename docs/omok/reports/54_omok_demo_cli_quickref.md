# Omok Demo CLI Quickref

## Current Entry Points

Operator-facing wrappers on `omok/mvp` are now:

```text
bin/omok-demo
bin/omok-start
```

- `bin/omok-demo` handles `seed`, `start`, `show`, `clear`, and `doctor`.
- `bin/omok-start` is the narrow convenience alias for `omok start <fullId> [renju|freestyle]`.
- `fullId` means the 12-character player id; the matching 8-character `gameId` is its first 8 characters.

Both are thin wrappers over the existing internal CLI transport:
- `bin/cli`
- `POST http://localhost:9663/run/cli`

So this path only works when:
- the lila server process is running locally and exposing the dev CLI port;
- `LILA_CLI_TOKEN_DEV` is available in the shell environment.

`OmokRoundRepo` is in-memory, so a server restart removes seeded omok state.

For wrapper-only smoke checks that do not require the server, run:

```text
bin/check-omok-demo-wrapper
bin/check-omok-start-wrapper
```

For the real local runtime check, run:

```text
bin/omok-demo doctor
```

## Supported Commands

```text
bin/omok-demo seed <gameId> [renju|freestyle] [move ...]
bin/omok-demo start <fullId> [renju|freestyle]
bin/omok-demo show <gameId>
bin/omok-demo clear <gameId>
bin/omok-demo doctor
bin/omok-start <fullId> [renju|freestyle]
```

`bin/omok-demo start` and `bin/omok-start` fail locally if the `fullId` is not 12 characters matching `[A-Za-z0-9_-]`, so the operator gets a wrapper error before the call reaches `bin/cli`.

Examples:

```text
bin/omok-demo seed demo1234
bin/omok-demo seed demo1234 renju H8 A1 I8
bin/omok-demo start demo1234abcd
bin/omok-demo start demo1234abcd freestyle
bin/omok-start demo1234abcd
bin/omok-start demo1234abcd freestyle
bin/omok-demo show demo1234
bin/omok-demo clear demo1234
```

Moves may be space-separated or comma-separated.

## Expected Helper Output

Representative outputs from the helper logic:

```text
seeded omok round demo1234: ruleSet=renju ply=0 turn=black lastMove=- moves=-
reseeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
started omok scaffold demo1234 -> /demo1234abcd: ruleSet=renju ply=0 turn=black lastMove=- moves=-
restarted omok scaffold demo1234 -> /demo1234abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-
omok round demo1234: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-
cleared omok round demo1234: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-
ERROR invalid game id 'bad'; expected 8 characters matching [A-Za-z0-9_-]
```

## Fastest Demo Flow

1. Doctor the local runtime path:

```text
bin/omok-demo doctor
```

2. Start a fresh omok scaffold for an existing player fullId:

```text
bin/omok-start demo1234abcd freestyle
```

3. Optionally inspect the seeded omok sidecar by game id:

```text
bin/omok-demo show demo1234
```

4. Open the player URL from the scaffold result, then watcher tabs for the matching game id.

## Reset / Recovery

Fast reset to a fresh started scaffold:

```text
bin/omok-start demo1234abcd
```

Explicit clear + seed path:

```text
bin/omok-demo clear demo1234
bin/omok-demo seed demo1234 renju H8 A1 I8
```

## Important Caveat

If `bin/cli` cannot reach `localhost:9663/run/cli`, the wrappers will fail even though the omok helper code exists. In that case, the missing piece is environment/runtime wiring, not omok seed/start logic itself.

If either wrapper prints `CLI helper is missing or not executable`, fix `LILA_OMOK_CLI_BIN` or restore the executable bit on `bin/cli` before debugging the server path.
