package lila.round

import lila.core.id.GameId
import lila.game.OmokGameSidecar
import lila.omok.{ Color, CoordinateNotation, Game as OmokGame, Move as OmokMove, Pos, Replay, RuleSet, Status }

class OmokStoredStateTest extends munit.FunSuite:

  private def move(key: String) = OmokMove(CoordinateNotation.parse(key).get)
  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  private val winningMoves = Vector(
    pos(7, 7),
    pos(0, 0),
    pos(7, 8),
    pos(0, 1),
    pos(7, 9),
    pos(0, 2),
    pos(7, 10),
    pos(0, 3),
    pos(7, 11)
  )

  test("stored state round-trips canonical rule set and moves"):
    val moves = Vector("H8", "A1", "I8").map(move)
    val game = Replay(OmokGame.initial(RuleSet.Freestyle), moves).toOption.get
    val state = OmokRoundState.fromGame(game, moves)
    val stored = OmokStoredState.fromState(GameId("demo1234"), state)

    assertEquals(stored._id, GameId("demo1234"))
    assertEquals(stored.ruleSet, "freestyle")
    assertEquals(stored.moves, Vector("H8", "A1", "I8"))

    val hydrated = OmokStoredState.toRoundState(stored).toOption.get
    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(hydrated.position.ply, 3)

  test("stored state rejects invalid rule sets and coordinates"):
    assertEquals(
      OmokStoredState.toRoundState(OmokGameSidecar(GameId("demo1234"), "bad-rules", Vector.empty)),
      Left("invalid stored omok rule set 'bad-rules'")
    )
    assertEquals(
      OmokStoredState.toRoundState(OmokGameSidecar(GameId("demo1234"), "renju", Vector("Z99"))),
      Left("invalid stored omok move 'Z99'")
    )

  test("stored state rejects illegal move sequences"):
    val stored = OmokGameSidecar(GameId("demo1234"), "renju", Vector("H8", "H8"))
    val result = OmokStoredState.toRoundState(stored)

    assert(result.isLeft)
    assert(result.left.exists(_.contains("invalid stored omok sequence at move 2 H8")))

  test("stored state round-trips terminal omok state through repo helpers"):
    val sourceRepo = OmokRoundRepo()
    val sourceGameId = GameId("source01")
    val player = OmokMovePlayer(sourceRepo)

    winningMoves.foreach: move =>
      player.place(PlaceRequest(sourceGameId, move)).toOption.get

    val original = sourceRepo.get(sourceGameId).get
    val stored = sourceRepo.getStored(sourceGameId).get
    val restoredRepo = OmokRoundRepo()
    val restoredGameId = GameId("restore1")

    val restored = restoredRepo.putStored(restoredGameId, stored).toOption.get

    assertEquals(stored.ruleSet, "renju")
    assertEquals(stored.moves, Vector("H8", "A1", "I8", "B1", "J8", "C1", "K8", "D1", "L8"))
    assertEquals(restored, original)
    assertEquals(restored.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals(restoredRepo.get(restoredGameId), Some(original))
    assertEquals(restoredRepo.getStored(restoredGameId), Some(stored.copy(_id = restoredGameId)))


  test("getOrHydrate restores cold cache from stored state and normalizes the target game id"):
    val repo = OmokRoundRepo()
    val gameId = GameId("cold1234")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = "freestyle",
      moves = Vector("H8", "A1", "I8")
    )

    val hydrated = repo.getOrHydrate(gameId)(Some(stored)).toOption.flatten.get

    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(repo.get(gameId), Some(hydrated))
    assertEquals(repo.getStored(gameId), Some(stored.copy(_id = gameId)))

  test("getOrHydrate returns cached state without touching the stored fallback"):
    val repo = OmokRoundRepo()
    val gameId = GameId("cache123")
    val cached = OmokRoundState.initial(RuleSet.Freestyle)
    var fetches = 0

    repo.put(gameId, cached)

    val result = repo.getOrHydrate(gameId):
      fetches += 1
      Some(OmokGameSidecar(gameId, "renju", Vector("H8")))

    assertEquals(result, Right(Some(cached)))
    assertEquals(fetches, 0)
    assertEquals(repo.get(gameId), Some(cached))

  test("putStored rejects invalid durable state without mutating the repo"):
    val repo = OmokRoundRepo()
    val gameId = GameId("invalid01")
    val stored = OmokGameSidecar(
      _id = gameId,
      ruleSet = "freestyle",
      moves = Vector("H8", "H8")
    )

    val result = repo.putStored(stored)

    assertEquals(result, Left("invalid stored omok sequence at move 2 H8: occupied"))
    assertEquals(repo.get(gameId), None)
    assertEquals(repo.getStored(gameId), None)


  test("getOrHydrate rejects invalid stored state without caching it"):
    val repo = OmokRoundRepo()
    val gameId = GameId("invalid02")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = "freestyle",
      moves = Vector("H8", "H8")
    )

    val result = repo.getOrHydrate(gameId)(Some(stored))

    assertEquals(result, Left("invalid stored omok sequence at move 2 H8: occupied"))
    assertEquals(repo.get(gameId), None)
    assertEquals(repo.getStored(gameId), None)
