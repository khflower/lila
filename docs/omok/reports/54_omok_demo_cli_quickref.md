# Omok Demo Helper Quickref

## Current Reality

On `omok/mvp`, the helper exists as code:
- `lila.round.OmokDemoSeed`

It supports three operations conceptually:
- `seed <gameId> [renju|freestyle] [move ...]`
- `show <gameId>`
- `clear <gameId>`

But **there is not yet a real operator-facing entrypoint wired into the running lila server process**.

That means:
- there is no confirmed `/dev/cli` or `/run/cli` path on this branch for omok seeding;
- a separate `sbt` JVM or ad-hoc compile does **not** mutate the live server's in-memory `OmokRoundRepo`;
- today this helper is best treated as a developer/internal helper and test-time utility, not a production-ready live command.

## What The Helper Does

The helper?s behavior is still useful and stable:

- default ruleset is `renju`
- moves can be space-separated or comma-separated
- move notation is normal omok coordinates like `H8`, `A1`, `O15`
- re-seeding the same `GameId` overwrites prior cached omok state for that id
- `clear` removes cached state so the same `GameId` can restart fresh

Representative outputs from the helper logic:

```text
seeded omok round demo1234: ruleSet=renju ply=0 turn=black lastMove=- moves=-
reseeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
cleared omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
ERROR invalid game id 'bad'; expected 8 characters matching [A-Za-z0-9_-]
```

## Honest Operator Guidance

For a live internal demo, the missing piece is still an entrypoint that can run **inside the live server process**.

Until that exists, the safest guidance is:

1. treat `OmokDemoSeed` as a code-level helper;
2. use `OmokDemoSeedTest` as the canonical usage examples;
3. if a live demo absolutely needs deterministic seeding, add a tiny dev-only server-process hook rather than relying on a separate `sbt` shell.

## Fastest Next Step

The smallest remaining usability improvement here is not the helper logic itself ? that already exists.

The real gap is a thin, dev-only operator entrypoint that invokes `OmokDemoSeed` in the running process.
