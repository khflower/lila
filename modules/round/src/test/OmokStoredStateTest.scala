package lila.round

import lila.core.id.GameId
import lila.omok.{ CoordinateNotation, Game as OmokGame, Move as OmokMove, Replay, RuleSet }

class OmokStoredStateTest extends munit.FunSuite:

  private def move(key: String) = OmokMove(CoordinateNotation.parse(key).get)

  test("stored state round-trips canonical rule set and moves"):
    val moves = Vector("H8", "A1", "I8").map(move)
    val game = Replay(OmokGame.initial(RuleSet.Freestyle), moves).toOption.get
    val state = OmokRoundState.fromGame(game, moves)
    val stored = OmokStoredState.fromState(GameId("demo1234"), state)

    assertEquals(stored._id, GameId("demo1234"))
    assertEquals(stored.ruleSet, "freestyle")
    assertEquals(stored.moves, Vector("H8", "A1", "I8"))

    val hydrated = stored.toRoundState.toOption.get
    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(hydrated.position.ply, 3)

  test("stored state rejects invalid rule sets and coordinates"):
    assertEquals(
      OmokStoredState(GameId("demo1234"), "bad-rules", Vector.empty).toRoundState,
      Left("invalid stored omok rule set 'bad-rules'")
    )
    assertEquals(
      OmokStoredState(GameId("demo1234"), "renju", Vector("Z99")).toRoundState,
      Left("invalid stored omok move 'Z99'")
    )

  test("stored state rejects illegal move sequences"):
    val stored = OmokStoredState(GameId("demo1234"), "renju", Vector("H8", "H8"))
    val result = stored.toRoundState

    assert(result.isLeft)
    assert(result.left.exists(_.contains("invalid stored omok sequence at move 2 H8")))
