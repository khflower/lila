# Omok Demo Seed Helper

## Scope

This checkpoint adds the smallest practical internal helper for demo seeding on the current `omok/mvp` branch.

The goal is narrow:

- seed `OmokRoundRepo` for one known `GameId`;
- keep the helper behind existing internal tooling;
- avoid adding a new public round endpoint or broader production wiring.

## Why This Shape

On this branch, live omok round state is held in the in-memory `TrieMap[GameId, OmokRoundState]` inside `modules/round/src/main/OmokRoundRepo.scala`.

That makes two constraints important:

1. `modules/api/src/main/RoundApi.scala` only exposes `data.omok` when `omokRoundRepo.get(gameId)` already returns state.
2. `build.sbt` sets `Compile / run / fork := true`, so an external `sbt console` or standalone script would seed a different JVM and would not affect the running server.

Because of that, the safest practical helper is an internal CLI command that runs inside the already-running lila server process.

## Current Helper

Current branch addition:

- `modules/round/src/main/OmokDemoSeed.scala`
- hooked from `modules/round/src/main/Env.scala` through the existing `lila.common.Cli.handle` path

Supported commands:

- `omok seed <gameId>`
- `omok seed <gameId> <renju|freestyle>`
- `omok seed <gameId> <renju|freestyle> <move...>`
- `omok show <gameId>`
- `omok clear <gameId>`

Move notation uses the existing omok coordinate parser from `modules/omok/src/main/CoordinateNotation.scala`, so examples like `H8`, `A1`, and `O15` work.

If no ruleset is provided, the helper defaults to `renju`, which matches `OmokRoundState.initial(...)` on this branch.

## Exact Seeding Flow For A Known Game Id

### 1. Start the normal local stack

Minimum for a real round page:

- MongoDB
- Redis
- lila via `./lila.sh` then `run`
- `lila-ws` if you want the socket round path, not just page boot

### 2. Use the existing internal CLI

Use either:

- the `/dev/cli` page as a user with the existing `Cli` permission, or
- the existing `/run/cli` internal route if you already have an authenticated way to hit it

No new route was added for omok seeding.

### 3. Seed the live repo entry

Example for known game id `demo1234`:

```text
omok seed demo1234 renju H8 A1 I8
```

Expected CLI output:

```text
seeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

What this does internally on the current branch:

1. validates `demo1234` through `GameId.from(...)`;
2. parses `H8 A1 I8` through `CoordinateNotation.parse(...)`;
3. replays those moves with `Replay(Game.initial(ruleSet), moves)`;
4. builds `OmokRoundState(PositionSnapshot.fromGame(game, moves), moves)`;
5. writes it to `env.round.omokRoundRepo.put(gameId, state)`.

That exact write is what makes subsequent player and watcher boot paths include `data.omok` for that round.

### 4. Verify the seed before opening pages

Use:

```text
omok show demo1234
```

Expected output:

```text
omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8
```

### 5. Open the round

After the seed is present in the live server process, load the normal player or watcher round URL for that same `GameId`.

Because `RoundApi.withOmok(...)` is read-only on this branch, the page will only boot into omok mode if this repo entry already exists.

## Resetting Or Reseeding

To clear one demo round entry:

```text
omok clear demo1234
```

To reset it to a fresh empty renju state:

```text
omok seed demo1234
```

To overwrite with a different scripted move list, just run `omok seed ...` again for the same game id.

## Safety Notes

- This does not add any public player or watcher API.
- This does not seed on read paths such as `RoundApi.withOmok(...)`.
- This does not change normal round creation.
- This stays scoped to the existing internal CLI permission surface.
- Repo state is still in-memory only, so a process restart removes the seed.

## Known Limits

- The helper is demo-only. It is not a production omok round creation path.
- The target `GameId` still has to correspond to a real round page you can open normally.
- If you seed moves that leave omok turn on white, but the generic round state still thinks black should move, current UI turn gating issues on this branch still apply.
- `RoundSocket` cleanup on `FinishGame` and `DeleteUnplayed` still removes the repo entry after the round lifecycle advances.
