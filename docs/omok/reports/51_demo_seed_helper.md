# Demo Seed Helper

## Purpose

This is the smallest practical internal helper path for showing the current `omok/mvp` branch without building a public omok-game creation surface.

Today there are two useful layers:
- the code helper: `lila.round.OmokDemoSeed`
- the operator wrapper: `bin/omok-demo`

The helper logic seeds `OmokRoundRepo` for a known `GameId`; the wrapper is the thinnest current way to invoke that logic through the existing internal CLI transport.

## Current Reality

- `modules/api/src/main/RoundApi.scala` reads `roundApi.omokRoundRepo.get(game.id)` and only adds `data.omok` if the repo already has state.
- `modules/round/src/main/Env.scala` wires `omokRoundRepo`, `omokMovePlayer`, and `omokDemoSeed`.
- `modules/round/src/main/OmokDemoSeed.scala` already supports:
  - `seed`
  - `show`
  - `clear`
- `bin/omok-demo` is now the smallest operator-facing wrapper, via `bin/cli`.

## Best Current Path

For a real internal demo, use:

```text
bin/omok-demo seed <gameId> [renju|freestyle] [move ...]
```

This is still dev-oriented because it depends on local runtime wiring (`bin/cli`, internal CLI port, dev token), but it is much better than manual code edits or ad-hoc test-only seeding.

## Suggested Seed Recipes

### Safest first seed

```text
bin/omok-demo seed demo1234 renju H8
```

Why:
- visible non-empty board immediately;
- turn becomes white;
- last move marker is obvious;
- reload is easy to verify.

### Slightly richer demo seed

```text
bin/omok-demo seed demo1234 renju H8 A1 I8
```

Why:
- shows move history and last-move marker;
- keeps the state easy to explain live;
- still small enough to debug quickly.

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

The remaining missing piece is not helper logic anymore.

The real operational dependency is that `bin/omok-demo` must reach the internal CLI transport in the running server environment. If that runtime path is unavailable, the next fix should be environment wiring ? not more seed logic.
