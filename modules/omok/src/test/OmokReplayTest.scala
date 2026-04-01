package lila.omok

class OmokReplayTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("scan collects the initial and intermediate game states"):
    val initial = Game.initial(RuleSet.Freestyle)
    val moves = List(
      Move(pos(7, 7)),
      Move(pos(7, 8)),
      Move(pos(8, 8))
    )

    val states = Replay.scan(initial, moves).toOption.get

    assertEquals(states.map(_.ply), Vector(0, 1, 2, 3))
    assertEquals(states.map(_.lastMove), Vector(None, Some(moves(0)), Some(moves(1)), Some(moves(2))))
    assertEquals(states.last.situation.board(pos(7, 7)), Some(Color.Black))
    assertEquals(states.last.situation.board(pos(7, 8)), Some(Color.White))
    assertEquals(states.last.situation.board(pos(8, 8)), Some(Color.Black))

  test("apply returns the final game after replaying all moves"):
    val initial = Game.initial(RuleSet.Freestyle)
    val moves = List(
      Move(pos(7, 5)),
      Move(pos(0, 0)),
      Move(pos(7, 6)),
      Move(pos(0, 1)),
      Move(pos(7, 7)),
      Move(pos(0, 2)),
      Move(pos(7, 8)),
      Move(pos(0, 3)),
      Move(pos(7, 9))
    )

    val replayed = Replay(initial, moves).toOption.get

    assertEquals(replayed.ply, moves.size)
    assertEquals(replayed.lastMove, moves.lastOption)
    assertEquals(replayed.status, Status.Win(Color.Black))

  test("scan stops on the first invalid move and returns the failing context"):
    val initial = Game.initial(RuleSet.Freestyle)
    val moves = List(
      Move(pos(7, 7)),
      Move(pos(7, 7)),
      Move(pos(7, 8))
    )

    val error = Replay.scan(initial, moves).left.toOption.get

    assertEquals(error.index, 1)
    assertEquals(error.move, moves(1))
    assertEquals(error.cause, MoveError.Occupied)
    assertEquals(error.game.ply, 1)
    assertEquals(error.game.lastMove, Some(moves.head))
    assertEquals(error.game.situation.board(pos(7, 8)), None)
