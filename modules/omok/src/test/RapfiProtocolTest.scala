package lila.omok

import scala.concurrent.duration.*

class RapfiProtocolTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("formatCoord and parseCoord round-trip between Pos and Gomocup coordinates"):
    val center = pos(7, 7)
    assertEquals(RapfiProtocol.formatCoord(center), "7,7")
    assertEquals(RapfiProtocol.parseCoord("7,7"), Some(center))
    assertEquals(RapfiProtocol.parseCoord("14,14"), Some(pos(14, 14)))
    assertEquals(RapfiProtocol.parseCoord("15,15"), None)

  test("board command emits BOARD ... DONE with stone ids"):
    val snapshot = PositionSnapshot.fromGame(
      Game.initial(RuleSet.Renju)
        .play(Move(pos(7, 7))).toOption.get
        .play(Move(pos(7, 8))).toOption.get,
      Vector(Move(pos(7, 7)), Move(pos(7, 8)))
    )
    val lines = RapfiProtocol.board(snapshot)
    assertEquals(lines.head, "BOARD")
    assertEquals(lines.last, "DONE")
    assert(lines.contains("7,7,1"))
    assert(lines.contains("8,7,2"))

  test("adapter emits BEGIN for empty positions and BOARD for fresh non-empty move requests"):
    val initial = PositionSnapshot.fromGame(Game.initial())
    val afterOne = PositionSnapshot.fromGame(
      Game.initial().play(Move(pos(7, 7))).toOption.get,
      Vector(Move(pos(7, 7)))
    )
    assertEquals(RapfiAdapter.moveRequest(initial), Vector("BEGIN"))
    assertEquals(RapfiAdapter.moveRequest(afterOne), Vector("BOARD", "7,7,1", "DONE"))

  test("incremental adapter requests use TURN only when the engine state is already synchronized"):
    val firstMove = Move(pos(7, 7))
    val secondMove = Move(pos(7, 8))
    val afterOne = PositionSnapshot.fromGame(
      Game.initial().play(firstMove).toOption.get,
      Vector(firstMove)
    )
    val afterTwo = PositionSnapshot.fromGame(
      Replay(Game.initial(), Vector(firstMove, secondMove)).toOption.get,
      Vector(firstMove, secondMove)
    )

    assertEquals(
      RapfiAdapter.incrementalMoveRequest(afterOne, syncedMoves = Some(Vector.empty)),
      Vector("TURN 7,7")
    )
    assertEquals(
      RapfiAdapter.incrementalMoveRequest(afterTwo, syncedMoves = Some(Vector(firstMove))),
      Vector("TURN 8,7")
    )
    assertEquals(
      RapfiAdapter.incrementalMoveRequest(afterTwo, syncedMoves = None),
      Vector("BOARD", "7,7,1", "8,7,2", "DONE")
    )

  test("analysis request includes budget hints before BOARD payload"):
    val snapshot = PositionSnapshot.fromGame(Game.initial())
    val lines = RapfiAdapter.analysisRequest(snapshot, EngineLimits(depth = Some(10), moveTime = Some(2.seconds), nodes = Some(1000)))
    assert(lines.contains("INFO max_depth 10"))
    assert(lines.contains("INFO time_left 2000"))
    assert(lines.contains("INFO max_node 1000"))
    assert(lines.contains("BOARD"))
    assertEquals(lines.last, "DONE")
