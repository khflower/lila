# Omok Demo CLI Quickref

## Scope

This is the shortest operator note for the omok demo helper on `omok/mvp`.

It is based on the actual CLI wiring in the running lila process:

- `omok seed <gameId> [renju|freestyle] [move ...]`
- `omok show <gameId>`
- `omok clear <gameId>`

Run it from the existing internal CLI surface in the live server JVM, not from a separate `sbt` shell:

- `/dev/cli`
- `POST /run/cli`

`OmokRoundRepo` is in-memory, so a server restart removes the seeded state.

## Fastest Live Flow

Use one known round id and keep reseeding that same id.

Empty-board seed:

```text
omok seed demo1234
```

Expected output:

```text
seeded omok round demo1234: ruleSet=renju ply=0 turn=black lastMove=- moves=-
```

Verify before opening tabs:

```text
omok show demo1234
```

Expected output:

```text
omok round demo1234: ruleSet=renju ply=0 turn=black lastMove=- moves=-
```

Preplayed seed:

```text
omok seed demo1234 renju H8 A1 I8
```

Expected output on first seed:

```text
seeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

Expected output if you run another seed for the same id:

```text
reseeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

Notes:

- if the ruleset is omitted, the helper defaults to `renju`
- moves can be space-separated or comma-separated
- move notation is the normal omok coordinate format, for example `H8`, `A1`, `O15`

## Reset And Recovery

The easiest reset during a live session is usually:

```text
omok seed demo1234
```

That overwrites any existing omok state for `demo1234` with a fresh empty renju board. After that, hard refresh the player and watcher tabs.

If you want to prove the repo was cleared first:

```text
omok clear demo1234
```

Expected output when state exists:

```text
cleared omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

Expected output when nothing is cached:

```text
no omok round state to clear for demo1234
```

Then reseed:

```text
omok seed demo1234
```

If a page opens without the omok board/state, the usual cause is that the page was opened before the seed existed in the running server process. Rerun `omok seed <gameId>` and refresh the tabs.

## Quick Checks

Show current state:

```text
omok show demo1234
```

Expected output when present:

```text
omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

Expected output when missing:

```text
no omok round state for demo1234
```

Typical bad input:

```text
omok seed bad H8
```

Expected output:

```text
ERROR invalid game id 'bad'; expected 8 characters matching [A-Za-z0-9_-]
```
