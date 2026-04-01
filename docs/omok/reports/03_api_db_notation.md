# Omok API / DB / Notation Integration Note

## Current Checkpoint

The live omok round path now works, but its state is still primarily a round-local cache:

- seed writes `OmokRoundRepo`
- live `place` mutates `OmokRoundRepo`
- `RoundApi.withOmok(...)` boots from `OmokRoundRepo`

That is enough for the current internal demo. It is not yet a durable product persistence model.

## Recommendation

The next serious persistence wave should add a durable omok sidecar record keyed by `GameId`.

Keep the current sidecar architecture. Do not try to fold omok into the existing chess SAN/FEN/UCI fields in `GameRepo`.

Canonical stored notation should be:

- ordered coordinate moves such as `H8`, `A1`, `J10`
- replayed through `lila.omok.Replay`
- exposed through the existing omok DTO layer

Do not make `boardRows`, `OmokAnalyseDto`, or `PositionSnapshot` the long-term source of truth. They are derived shapes.

## Why This Cut Is Safer

What already exists and should stay authoritative:

- `modules/omok/src/main/CoordinateNotation.scala`
- `modules/omok/src/main/replay.scala`
- `modules/omok/src/main/bridge.scala`

What is still chess-shaped and should stay out of this wave:

- `modules/game/src/main/PgnStorage.scala`
- `modules/game/src/main/UciMemo.scala`
- `modules/game/src/main/Event.scala`
- `modules/round/src/main/StepBuilder.scala`
- tree / study / PGN import-export code

Trying to push omok into those chess notation seams now would turn a small persistence wave into a multi-module model rewrite.

## Durable Record Shape

Recommended stored shape:

```scala
final case class StoredOmokGame(
    _id: GameId,
    ruleSet: String,
    boardSize: Int,
    moves: Vector[String],
    ply: Int,
    lastMove: Option[String],
    status: String,
    winner: Option[String],
    rev: Int,
    createdAt: Instant,
    updatedAt: Instant
)
```

Guidance:

- `moves` is the canonical field
- each move string comes from `CoordinateNotation.format`
- `ruleSet` stays explicit even if early games are mostly `renju`
- `status`, `winner`, `ply`, and `lastMove` are denormalized convenience fields
- snapshots and DTOs are rebuilt from replay

## Ownership Split

### `modules/omok`

Keep as the pure rules and notation layer:

- coordinate parse/format
- replay from move list
- DTO generation

### `modules/game`

Own the durable omok sidecar:

- new omok collection keyed by `GameId`
- lifecycle alignment with game creation / deletion

Recommended additions:

- `modules/game/src/main/OmokGameSidecar.scala`
- `modules/game/src/main/OmokGameRepo.scala`

### `modules/round`

Keep a hot-cache and live adapter role:

- `OmokRoundRepo` remains an in-memory mirror
- seed and live move write durable state, then warm the cache
- boot reads cache first, then durable storage as fallback

### `modules/api`

Keep the frontend contract stable:

- `RoundApi.withOmok(...)` should still emit `data.omok`
- when cache is cold, rebuild from the durable record instead of failing cold boot

## Practical File Plan

Add:

- `modules/game/src/main/OmokGameSidecar.scala`
- `modules/game/src/main/OmokGameRepo.scala`

Edit:

- `modules/game/src/main/Env.scala`
- `modules/round/src/main/Env.scala`
- `modules/round/src/main/OmokMovePlayer.scala`
- `modules/round/src/main/OmokDemoSeed.scala`
- `modules/api/src/main/RoundApi.scala`
- `modules/round/src/main/RoundSocket.scala`
- `modules/round/src/main/Titivate.scala`

## Suggested Execution Order

1. Add the durable omok repo in `modules/game`.
2. Make CLI seed write that durable record and warm the cache.
3. Make live `place` write durable state before updating the cache.
4. Make `RoundApi.withOmok(...)` fall back to the durable record on cache-cold boot.
5. Delete durable omok rows only when the underlying game is actually deleted, not on ordinary finish.

## Bottom Line

Keep `Game` as the generic lifecycle record, add a durable omok sidecar keyed by the same `GameId`, store canonical coordinate moves, and continue deriving snapshots/DTOs from replay.

That preserves the working live-round contract while giving start flow, reload, and later product work a real persistence base.
