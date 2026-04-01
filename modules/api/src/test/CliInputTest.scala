package lila.api

class CliInputTest extends munit.FunSuite:

  test("parse keeps omok demo commands as argv-style tokens"):
    assertEquals(
      CliInput.parse("omok seed demo1234 renju H8 A1 I8"),
      List("omok", "seed", "demo1234", "renju", "H8", "A1", "I8")
    )

  test("parse tolerates surrounding whitespace in dev cli requests"):
    assertEquals(
      CliInput.parse(" \tomok \n show   demo1234  "),
      List("omok", "show", "demo1234")
    )
    assertEquals(CliInput.parse(" \n\t "), Nil)
