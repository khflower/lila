package lila.omok

class OmokTerminalStateTest extends munit.FunSuite:

  private def move(row: Int, col: Int) = Move(Pos.unsafe(row, col))

  private val winningLine = List(
    move(7, 5),
    move(0, 0),
    move(7, 6),
    move(0, 1),
    move(7, 7),
    move(0, 2),
    move(7, 8),
    move(0, 3),
    move(7, 9)
  )

  test("games reject further moves once a win has been reached"):
    val won = Replay(Game.initial(RuleSet.Freestyle), winningLine).toOption.get

    assertEquals(won.status, Status.Win(Color.Black))
    assertEquals(won.play(move(0, 4)), Left(MoveError.GameAlreadyOver))

  test("replay reports game-already-over when moves continue after a win"):
    val moves = winningLine :+ move(0, 4)

    val error = Replay.scan(Game.initial(RuleSet.Freestyle), moves).left.toOption.get

    assertEquals(error.index, winningLine.size)
    assertEquals(error.move, move(0, 4))
    assertEquals(error.cause, MoveError.GameAlreadyOver)
    assertEquals(error.game.ply, winningLine.size)
    assertEquals(error.game.status, Status.Win(Color.Black))
    assertEquals(error.game.lastMove, winningLine.lastOption)
    assertEquals(error.game.situation.board(Pos.unsafe(0, 4)), None)
