# Backend Game-Domain Analysis for lila -> omok

## Scope and confidence

This wave is analysis only.

Verified directly in repo-facing sources:

- `build.sbt`
- `modules/game/src/main/Game.scala`
- `modules/game/src/main/GameRepo.scala`
- `modules/game/src/main` directory layout
- `modules/round/src/main` directory layout

Some details for `setup`, `challenge`, `lobby`, `pool`, `swiss`, and `tournament` are inferred from module boundaries, file naming, and their role in the lila backend, because direct file-content fetches for those modules were incomplete in this environment. I call those out as inferred where relevant.

## 1. Key Scala modules and responsibilities

### `build.sbt`

`build.sbt` is the first backend choke point because it wires module dependencies and pulls in the chess rules engine through the shared `Dependencies`.

Important verified couplings:

- `game = module("game").dependsOn(db, memo, opening, rating).enablePlugins(BuildInfoPlugin).settings(libraryDependencies ++= Seq(chess.testKit))`
- `round = module("round").dependsOn(game, user, playban, pref, chat)`
- `setup = module("setup").dependsOn(lobby)`
- `challenge = module("challenge").dependsOn(game, room, oauth)`
- `lobby = module("lobby").dependsOn(game, pool, relation, msg)`
- `pool = module("pool").dependsOn(rating)`
- `swiss = module("swiss").dependsOn(gathering, room, memo)`
- `tournament = module("tournament").dependsOn(gathering, room, memo)`

Read literally, the playable path is:

`setup/challenge/lobby -> round -> game`

with `pool` feeding lobby matchmaking, and `swiss` / `tournament` sitting on top as higher-level organizers.

### `project/Dependencies.scala`

This is where the external rules engine alias is defined. Even without relying on higher-level code, this file is the place that decides whether the backend builds against `scalachess` or an omok rules library / adapter layer.

Practical implication:

- replacing chess at the backend starts here
- a temporary compatibility shim is likely cheaper than trying to rename all `chess.*` imports at once

### `modules/game`

This is the central game-domain module.

Verified files and likely responsibilities:

- `Game.scala`: core game aggregate extensions, game-state helpers, rule-dependent checks, variant policy
- `GameRepo.scala`: persistence, denormalization, BSON reads/writes, stored rule-state fields
- `BSONHandlers.scala`: BSON codecs for game-domain data
- `BinaryFormat.scala`: binary move-time / compact state encoding
- `Importer.scala`: imports rule-state from external notation / stored records
- `JsonView.scala`: serializes game state to API / socket consumers
- `PgnDump.scala`, `PgnStorage.scala`: chess export pipeline
- `UciMemo.scala`: move notation caching around UCI
- `Player.scala`, `Pov.scala`, `Progress.scala`, `LightGame.scala`: domain projections built on top of the game aggregate

This module is where omok needs a replacement for the actual board rules, notation, position serialization, and legal-move application.

### `modules/round`

This is the live play runtime around a game.

Verified directory entries strongly indicate these responsibilities:

- `MovePlayer.scala`: validates and applies player moves to game state
- `StepBuilder.scala`: builds the incremental client-facing game step after each move
- `Finisher.scala`: translates rule outcomes / timeout / resign / mate-equivalent into final game state
- `Drawer.scala`: handles draw offer / auto-draw logic
- `Takebacker.scala`: handles takeback flow
- `RoundGame.scala`: round-scoped projection of a game
- `Env.scala`: wires the live round services together

For omok, `round` is the second major choke point after `game`: even if persistence is ported, live play is blocked until move application and finish-state generation stop assuming chess semantics.

### `modules/setup`

Inferred responsibility:

- turns a creation form, API request, or accepted challenge into a new game configuration
- likely owns initial variant / mode / clock / color / rating constraints before a `Game` is inserted

For omok, setup matters because it decides which configuration fields are still meaningful and which are chess-only.

### `modules/challenge`

Inferred responsibility:

- stores and validates direct invitations between players
- carries game options that later become a created `Game`

It is likely coupled to chess through variant, mode, clock, color choice, from-position support, and possibly rating / perf compatibility checks.

### `modules/lobby`

Inferred responsibility:

- public seek / hook creation and matching
- matchmaking requests before a round exists

It is likely coupled to chess through seek options such as variant, time control, rated/casual mode, color preference, and perf category.

### `modules/pool`

Inferred responsibility:

- predefined auto-match pools keyed by speed/perf/variant
- creates or brokers pairings into `lobby` / `round`

This is likely not required for a first playable omok prototype, but it will need adaptation once automatch exists.

### `modules/swiss` and `modules/tournament`

Only direct game-domain coupling matters for this wave.

Likely direct couplings:

- creating rounds/games with specific clock/variant/mode settings
- relying on `Game` outcomes and player colors
- perf/rating assumptions that are chess-centric

These should be treated as downstream consumers, not part of the minimum playable slice.

## 2. Exact places where scalachess / chess-specific types are central

### Verified in `modules/game/src/main/Game.scala`

The file imports chess types directly at the top and uses them pervasively:

- `import chess.MoveOrDrop.{ color, fold }`
- `import chess.format.Uci`
- `import chess.format.pgn.SanStr`
- `import chess.variant.Variant`
- `import chess.{ ByColor, Castles, Centis, Clock, Color, Game as ChessGame, Mode, MoveOrDrop, Ply, Speed, Status }`

This is not cosmetic. These imports shape the game model itself.

Central verified usages:

- `def computeMoveTimes(g: Game, color: Color): Option[List[Centis]]`
- `def withClock(c: Clock)`
- draw-offer logic keyed by `Color`
- berserk logic mutating `Clock`
- `Game.analysableVariants: Set[Variant]`
- hardcoded chess variants:
  - `chess.variant.Standard`
  - `chess.variant.Crazyhouse`
  - `chess.variant.Chess960`
  - `chess.variant.KingOfTheHill`
  - `chess.variant.ThreeCheck`
  - `chess.variant.Antichess`
  - `chess.variant.FromPosition`
  - `chess.variant.Horde`
  - `chess.variant.Atomic`
  - `chess.variant.RacingKings`
- chess-specific policy:
  - `isOldHorde`
  - `isBoardCompatible`
  - `isBotCompatible`

Conclusion: `Game.scala` is still structurally chess-shaped, not just parameterized by a pluggable rules engine.

### Verified in `modules/game/src/main/GameRepo.scala`

This file directly imports chess serialization and status types:

- `import chess.{ ByColor, Color, Status }`
- `import chess.rating.IntRatingDiff`

Central verified usages:

- hold-alert storage keyed by `chess.White` and `chess.Black`
- `insertDenormalized(g: Game, initialFen: Option[Fen.Full] = None)`
- persistence of `F.initialFen`
- special handling of `g2.variant.fromPosition || g2.variant.chess960`
- `Fen.write(g2.chess)`
- fallback from rated to `chess.Mode.Casual`
- `allowRated(g.variant, g.clock.map(_.config))`
- `findRandomStandardCheckmate`

Conclusion: persistence is tied not only to generic game state, but specifically to FEN, white/black, checkmate, chess variants, and chess rating-diff semantics.

### Verified in `build.sbt`

The `game` module explicitly pulls in chess test support:

- `libraryDependencies ++= Seq(chess.testKit)`

This suggests the module boundary already treats chess as a foundational library, not an optional backend plugin.

### Highly likely but not directly verified in content fetches

These files are the next places to inspect or adapt first in code, based on module role and naming:

- `modules/game/src/main/BSONHandlers.scala`
- `modules/game/src/main/BinaryFormat.scala`
- `modules/game/src/main/Importer.scala`
- `modules/game/src/main/JsonView.scala`
- `modules/game/src/main/PgnDump.scala`
- `modules/game/src/main/PgnStorage.scala`
- `modules/game/src/main/UciMemo.scala`
- `modules/round/src/main/MovePlayer.scala`
- `modules/round/src/main/StepBuilder.scala`
- `modules/round/src/main/Finisher.scala`
- `modules/round/src/main/Drawer.scala`
- `modules/round/src/main/Takebacker.scala`

Expected chess-specific dependencies in that set:

- UCI / SAN / PGN notation
- FEN or equivalent position serialization
- white/black color assumptions
- check/checkmate/stalemate draw semantics
- chess clocks, berserk, takeback, draw offer workflow
- variant/perf mapping

## 3. Minimum backend slice needed for a playable omok prototype

The minimum slice is smaller than the full lila backend.

### Required

1. `project/Dependencies.scala`
   - swap `scalachess` for either:
   - an omok rules library, or
   - a compatibility adapter that exposes the minimal API lila needs

2. `modules/game`
   - replace the internal board/rules state now stored in `g.chess`
   - replace move application primitives
   - replace status/winner/turn tracking
   - replace persistence formats that store FEN/UCI/SAN/PGN-derived data
   - keep only the data needed for omok round-trip persistence and client state

3. `modules/round`
   - move submission
   - legality checking
   - step/event generation after each move
   - finish detection for five-in-a-row / resignation / timeout
   - optional clocks if the prototype includes timed play

4. one game-creation path
   - either a very small `setup` path
   - or a very small `challenge` / `lobby` path
   - not all of them

### Can be deferred

- `pool`
- `swiss`
- `tournament`
- PGN/export/import features
- analysis-related variant handling
- most draw/takeback/bot compatibility rules

### Practical prototype recommendation

The smallest useful backend is:

- direct game creation
- persisted omok game state
- live move application in `round`
- websocket/API step updates
- basic finish-state handling

That can ship before public lobby seeks, automatch pools, or tournament support.

## 4. Recommended migration order

### Order

1. Define the omok rules boundary.
   - Decide whether to preserve a chess-shaped adapter API or introduce a clean `omok.*` domain and translate at the lila edge.
   - For the first prototype, an adapter is probably faster.

2. Replace the `modules/game` core state.
   - Remove dependence on `ChessGame`, `Variant`, `Fen`, `Uci`, `SanStr`, `Status`, and white/black-specific helpers where possible.
   - Make persistence work with omok board state and move history.

3. Port `modules/round`.
   - `MovePlayer.scala`
   - `StepBuilder.scala`
   - `Finisher.scala`
   - then disable or stub chess-only flows such as draw offers / takebacks if they are not part of the prototype rules.

4. Reintroduce one creation surface.
   - simplest path first: direct setup or direct challenge acceptance
   - lobby seeks only after game creation is stable

5. Port `lobby` and `pool`.
   - only after basic games can be created and played end to end

6. Port `swiss` / `tournament` last.
   - these are downstream schedulers over the game domain

## 5. Top risks / blockers

### 1. Chess is embedded as the domain language, not just the rules engine

The backend does not only call a chess library. It stores and reasons in chess-native terms:

- `Variant`
- `Color`
- `Status`
- `Uci`
- `SanStr`
- `Fen`
- `ChessGame`
- white/black keyed maps

This means a simple dependency swap will not be enough.

### 2. Persistence is chess-specific

`GameRepo.scala` stores:

- `initialFen`
- white/black keyed fields
- checkmate-oriented queries
- rated-mode logic tied to chess variant and clock compatibility

A prototype can work only if storage is simplified early.

### 3. Round flow likely assumes chess move semantics everywhere

Even without full direct file inspection, the `round` filenames show likely chess-heavy logic around:

- move application
- draw handling
- takebacks
- finish states

If omok does not need some of those features, stubbing them out early will reduce the port size substantially.

### 4. Variant / perf / matchmaking taxonomy is chess-centric

`Game.scala` hardcodes multiple chess variants. `lobby`, `pool`, `swiss`, and `tournament` almost certainly build on the same taxonomy. Omok should start with a single ruleset and delay any variant/perf taxonomy until later.

### 5. Export/import and notation systems are likely large hidden costs

Files such as:

- `PgnDump.scala`
- `PgnStorage.scala`
- `Importer.scala`
- `UciMemo.scala`

suggest a large amount of backend surface area exists only because chess has PGN/UCI/FEN/SAN ecosystems. Omok does not need parity here for a first playable backend.

## Bottom line

The port should treat `modules/game` and `modules/round` as the real chess-to-omok migration core.

For a first playable prototype, the backend can ignore most of `pool`, `swiss`, and `tournament`, and can keep `setup` / `challenge` / `lobby` minimal. The main blocker is that lila currently models game state, persistence, notation, and lifecycle in chess-native types rather than behind a clean game-agnostic interface.
