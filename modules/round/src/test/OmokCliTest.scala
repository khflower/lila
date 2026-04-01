package lila.round

import scala.concurrent.Await

class OmokCliTest extends munit.FunSuite:

  private def run(handler: PartialFunction[List[String], Fu[String]], args: List[String]): String =
    Await.result(handler(args), 1.second)

  test("seed show and clear route through the omok demo helper"):
    val repo = OmokRoundRepo()
    val handler = OmokCli.handler(OmokDemoSeed(repo))

    assert(handler.isDefinedAt(List("omok", "seed", "demo1234", "freestyle", "H8, A1")))
    assertEquals(
      run(handler, List("omok", "seed", "demo1234", "freestyle", "H8, A1")),
      "seeded omok round demo1234: ruleSet=freestyle ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(
      run(handler, List("omok", "show", "demo1234")),
      "omok round demo1234: ruleSet=freestyle ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(
      run(handler, List("omok", "clear", "demo1234")),
      "cleared omok round demo1234: ruleSet=freestyle ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(run(handler, List("omok", "show", "demo1234")), "no omok round state for demo1234")

  test("only the supported omok command shapes are routed"):
    val handler = OmokCli.handler(OmokDemoSeed(OmokRoundRepo()))

    assert(!handler.isDefinedAt(Nil))
    assert(!handler.isDefinedAt(List("omok")))
    assert(!handler.isDefinedAt(List("omok", "seed")))
    assert(!handler.isDefinedAt(List("omok", "show", "demo1234", "extra")))
    assert(!handler.isDefinedAt(List("omok", "clear", "demo1234", "extra")))
    assert(!handler.isDefinedAt(List("omok", "unknown", "demo1234")))

  test("comma-split cli argv still seeds the expected omok moves"):
    val repo = OmokRoundRepo()
    val handler = OmokCli.handler(OmokDemoSeed(repo))
    val parsedArgs = List("omok", "seed", "demo1234", "freestyle", "H8,", "A1", ",", "I8")

    assert(handler.isDefinedAt(parsedArgs))
    assertEquals(
      run(handler, parsedArgs),
      "seeded omok round demo1234: ruleSet=freestyle ply=3 turn=white lastMove=I8 moves=H8,A1,I8"
    )
    assertEquals(
      run(handler, List("omok", "show", "demo1234")),
      "omok round demo1234: ruleSet=freestyle ply=3 turn=white lastMove=I8 moves=H8,A1,I8"
    )
