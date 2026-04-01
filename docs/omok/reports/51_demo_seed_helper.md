# Demo Seed Helper

## Purpose

This is the smallest practical internal helper plan for showing the current `omok/mvp` branch without building a public omok-game creation surface.

The branch already supports omok round boot only when `OmokRoundRepo` contains state for the target `GameId`.

So the demo seeding problem is not database schema or socket wiring anymore; it is simply **how to put one known snapshot into `OmokRoundRepo` before opening the round page**.

## Current Reality

- `modules/api/src/main/RoundApi.scala` reads `roundApi.omokRoundRepo.get(game.id)` and only adds `data.omok` if the repo already has state.
- `modules/round/src/main/Env.scala` already wires `omokRoundRepo` and `omokMovePlayer`.
- `modules/round/src/main/OmokMovePlayer.scala` already has the easiest state mutation surface:
  - `ensure(gameId, ruleSet)`
  - `place(PlaceRequest(...))`
  - `remove(gameId)`

That means the smallest safe seed path is an **internal-only startup/dev helper** that calls `omokMovePlayer.ensure(...)` plus a few `place(...)` calls for a known `GameId`.

## Best Low-Risk Approach

### Option A ? Dev-only one-shot helper object

Recommended first.

Add one tiny internal helper under a clearly non-production path, for example:
- `modules/round/src/test/OmokDemoSeed.scala`, or
- `docs/omok/scripts/seed_demo_round.scala.txt` as an operator recipe.

The helper should:
1. take a known `GameId`;
2. call `omokMovePlayer.remove(gameId)` first;
3. call `omokMovePlayer.ensure(gameId)`;
4. replay a short move list through `place(...)`;
5. print the resulting board/ply/turn so the operator can verify the seed succeeded.

Why this is the best current option:
- no new public route;
- no new admin endpoint;
- no change to production gameplay permissions;
- reuses the same code path the live round already depends on.

## Suggested Seed Recipe

For the safest internal demo, seed exactly one black move:
- `H8`

Why one move is the sweet spot:
- boot/reload shows a visible omok board immediately;
- turn becomes white after the seed;
- watcher/player state is easy to inspect;
- it minimizes confusion while testing click gating and live redraw.

For a slightly richer demo seed, use:
- `H8`, `A1`, `I8`

That gives:
- visible move history;
- non-empty `moves` array;
- a clear last-move marker;
- a reconnect state that is easy to validate.

## Operator Runbook

1. Create or choose a known round `GameId` that already has a normal round page.
2. Stop any prior stale omok seed for that id:
   - `omokMovePlayer.remove(gameId)`
3. Seed a fresh omok snapshot:
   - `omokMovePlayer.ensure(gameId)`
   - replay the chosen moves with `place(...)`
4. Open:
   - black player page,
   - white player page,
   - watcher page.
5. Confirm `data.omok` appears on boot and the overlay board matches the seeded moves.

## Minimal Example Logic

Pseudocode only:

```scala
val gameId = GameId("abcdefgh")
omokMovePlayer.remove(gameId)
omokMovePlayer.ensure(gameId)
List("H8", "A1", "I8").foreach { key =>
  val pos = lila.omok.Pos.fromKey(key).get
  omokMovePlayer.place(PlaceRequest(gameId, pos)).toOption.get
}
println(omokMovePlayer.get(gameId))
```

## What Not To Do

Avoid these for the current branch:
- public HTTP seed endpoint;
- permanent admin API unless demo usage proves it is needed;
- DB-backed seeding for now;
- wiring demo seeding into normal game creation before the omok finish/status story is ready.

## Recommendation

If we need the fastest path to a repeatable internal demo, build a **tiny dev-only seed helper that reuses `OmokMovePlayer`**. That is the smallest practical step still missing for reliable demos.
