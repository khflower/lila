package lila.omok

class OmokRulesTest extends munit.FunSuite:

  private def board(rows: String*) = Board.fromRows(rows.toList)
  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)
  private val nearDrawRows = List(
    ".WBWWBBWBBWWBBW",
    "BBWWWBWWBBBBWBW",
    "BWWBWBWBWWBWBBB",
    "WBWWWBWBBWWBBBB",
    "BBBWBWWBWBWBWWB",
    "BWBBWWBBWWWBWWW",
    "WBWWWBWWBBWWBWB",
    "WWBWWWBWBWBWWBB",
    "BBBWWWWBBBWBBWB",
    "WBBWBBWWWWBWWWB",
    "WWBBBWBBBWBBWWW",
    "BBWWWWBWWWBBWBW",
    "WBWWWBWWBWWBBBW",
    "BBBWBBWWWBWBWWW",
    "BBWWWBBWWBBWWWB"
  )

  test("freestyle exact five is a win"):
    val g = Game(
      Situation(
        board(
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "....BBBB.......",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "..............."
        ),
        Color.Black,
        RuleSet.Freestyle
      )
    )
    val next = g.play(Move(pos(7, 8))).toOption.get
    assertEquals(next.status, Status.Win(Color.Black))

  test("renju overline is forbidden for black"):
    val situation = Situation(
      board(
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "..BBBBB........",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "..............."
      ),
      Color.Black,
      RuleSet.Renju
    )
    assertEquals(
      situation.play(Move(pos(7, 7))),
      Left(MoveError.Forbidden(ForbiddenReason.Overline))
    )

  test("renju double-four is forbidden for black"):
    val situation = Situation(
      board(
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        ".......B.......",
        ".......B.......",
        ".....BB.B......",
        ".......B.......",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "..............."
      ),
      Color.Black,
      RuleSet.Renju
    )
    assertEquals(
      situation.play(Move(pos(7, 7))),
      Left(MoveError.Forbidden(ForbiddenReason.DoubleFour))
    )

  test("renju double-three is forbidden for black"):
    val situation = Situation(
      board(
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "........BB.....",
        ".......B.......",
        ".......B.......",
        "...............",
        "...............",
        "...............",
        "...............",
        "..............."
      ),
      Color.Black,
      RuleSet.Renju
    )
    assertEquals(
      situation.play(Move(pos(7, 7))),
      Left(MoveError.Forbidden(ForbiddenReason.DoubleThree))
    )

  test("renju exact five still wins for black"):
    val situation = Situation(
      board(
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        ".....BBBB......",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "..............."
      ),
      Color.Black,
      RuleSet.Renju
    )
    val next = Game(situation).play(Move(pos(7, 9))).toOption.get
    assertEquals(next.status, Status.Win(Color.Black))

  test("white is not blocked by renju forbidden rules"):
    val situation = Situation(
      board(
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        ".......W.......",
        ".......W.......",
        ".....WW.W......",
        ".......W.......",
        "...............",
        "...............",
        "...............",
        "...............",
        "...............",
        "..............."
      ),
      Color.White,
      RuleSet.Renju
    )
    assert(situation.play(Move(pos(7, 7))).isRight)

  test("freestyle full board without five is a draw"):
    val situation = Situation(Board.fromRows(nearDrawRows), Color.White, RuleSet.Freestyle)

    val next = Game(situation).play(Move(pos(0, 0))).toOption.get

    assertEquals(next.status, Status.Draw)
    assert(next.situation.board.isFull)
    assertEquals(next.lastMove, Some(Move(pos(0, 0))))

  test("drawn games reject further moves"):
    val situation = Situation(Board.fromRows(nearDrawRows), Color.White, RuleSet.Freestyle)
    val drawn = Game(situation).play(Move(pos(0, 0))).toOption.get

    assertEquals(drawn.play(Move(pos(1, 1))), Left(MoveError.GameAlreadyOver))
