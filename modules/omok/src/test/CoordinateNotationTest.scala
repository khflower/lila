package lila.omok

class CoordinateNotationTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("format uses canonical A1 to O15 coordinates"):
    assertEquals(CoordinateNotation.format(pos(0, 0)), "A1")
    assertEquals(CoordinateNotation.format(pos(0, 14)), "O1")
    assertEquals(CoordinateNotation.format(pos(7, 7)), "H8")
    assertEquals(CoordinateNotation.format(pos(14, 0)), "A15")
    assertEquals(CoordinateNotation.format(pos(14, 14)), "O15")

  test("parse accepts canonical coordinates case-insensitively"):
    assertEquals(CoordinateNotation.parse("A1"), Some(pos(0, 0)))
    assertEquals(CoordinateNotation.parse("h8"), Some(pos(7, 7)))
    assertEquals(CoordinateNotation.parse("o15"), Some(pos(14, 14)))
    assertEquals(CoordinateNotation.parse("  C10 "), Some(pos(9, 2)))

  test("format and parse round-trip across the full board"):
    for
      row <- 0 until Pos.Size
      col <- 0 until Pos.Size
    do
      val expected = pos(row, col)
      assertEquals(CoordinateNotation.parse(CoordinateNotation.format(expected)), Some(expected))

  test("parse rejects malformed or out-of-range coordinates"):
    List("", "A", "A0", "A01", "A16", "P1", "AA1", "1A", "A 1").foreach: input =>
      assertEquals(CoordinateNotation.parse(input), None)

  test("pos key uses canonical coordinate notation"):
    assertEquals(pos(3, 4).key, "E4")
