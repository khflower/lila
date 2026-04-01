# Omok Demo CLI Quickref

## Current Entry Point

On `omok/mvp`, the smallest operator-facing wrapper is:

```text
bin/omok-demo
```

It is a thin wrapper over the existing internal CLI transport:
- `bin/cli`
- `POST http://localhost:9663/run/cli`

So this path only works when:
- the lila server process is running locally and exposing the dev CLI port;
- `LILA_CLI_TOKEN_DEV` is available in the shell environment.

`OmokRoundRepo` is in-memory, so a server restart removes seeded omok state.

For a wrapper-only smoke check that does not require the server, run:

```text
bin/check-omok-demo-wrapper
```

That script stubs `LILA_OMOK_CLI_BIN` and verifies three things:
- `bin/omok-demo` usage gating;
- forwarded argv shape for `seed`, `show`, and `clear`;
- `doctor` output when the helper is overridden or missing.

For the real local runtime check, run:

```text
bin/omok-demo doctor
```

That command keeps the wrapper thin, but it removes the usual ambiguity:
- `FAIL CLI helper is missing or not executable` means fix `LILA_OMOK_CLI_BIN` or restore `bin/cli`.
- `FAIL LILA_CLI_TOKEN_DEV is not set` means the server path may be fine, but the shell environment is not ready.
- `FAIL http://localhost:9663/run/cli is unreachable ...` means local server wiring is the missing piece.
- `PASS ... responded with HTTP ...` means the wrapper can at least see the local dev CLI transport.

## Supported Commands

```text
bin/omok-demo seed <gameId> [renju|freestyle] [move ...]
bin/omok-demo show <gameId>
bin/omok-demo clear <gameId>
```

Examples:

```text
bin/omok-demo seed demo1234
bin/omok-demo seed demo1234 renju H8 A1 I8
bin/omok-demo show demo1234
bin/omok-demo clear demo1234
```

Moves may be space-separated or comma-separated.

## Expected Helper Output

Representative outputs from the helper logic:

```text
seeded omok round demo1234: ruleSet=renju ply=0 turn=black lastMove=- moves=-
reseeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
cleared omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
ERROR invalid game id 'bad'; expected 8 characters matching [A-Za-z0-9_-]
```

## Fastest Demo Flow

1. Seed the round id before opening tabs:

```text
bin/omok-demo doctor
bin/omok-demo seed demo1234 renju H8 A1 I8
```

2. Verify state exists:

```text
bin/omok-demo show demo1234
```

3. Open player/watcher tabs for that round id.
4. If the page misses omok state, reseed the same id and refresh the tabs.

## Reset / Recovery

Fast reset:

```text
bin/omok-demo seed demo1234
```

Explicit clear + restart:

```text
bin/omok-demo clear demo1234
bin/omok-demo seed demo1234
```

## Important Caveat

If `bin/cli` cannot reach `localhost:9663/run/cli`, the wrapper will fail even though the omok helper code exists. In that case, the missing piece is environment/runtime wiring, not omok seed logic itself.

If the wrapper prints `omok-demo: CLI helper is missing or not executable: ...`, fix `LILA_OMOK_CLI_BIN` or restore the executable bit on `bin/cli` before debugging the server path.
