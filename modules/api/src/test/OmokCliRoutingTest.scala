package lila.api

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

import lila.common.{ Bus, CliCommand }
import lila.round.{ OmokDemoSeed, OmokRoundRepo }

class OmokCliRoutingTest extends munit.FunSuite:

  private val omokCliModuleClass = getClass.getClassLoader.loadClass("lila.round.OmokCli$")
  private val omokCliModule = omokCliModuleClass.getField("MODULE$").get(null)
  private val omokCliHandler = omokCliModuleClass.getMethod("handler", classOf[OmokDemoSeed])

  private def handler(seed: OmokDemoSeed): PartialFunction[List[String], Fu[String]] =
    omokCliHandler.invoke(omokCliModule, seed).asInstanceOf[PartialFunction[List[String], Fu[String]]]

  private def runRaw(handler: PartialFunction[List[String], Fu[String]], command: String): String =
    val subscription = lila.common.Cli.handle(handler)
    try
      val promise = Promise[scalalib.data.LazyFu[String]]()
      Bus.pub(CliCommand(CliInput.parse(command), promise))
      Await.result(promise.future.flatMap(_.value), 1.second)
    finally Bus.unsub[CliCommand](subscription)

  test("raw dev cli command strings reach omok seed show and clear routing"):
    val omokHandler = handler(OmokDemoSeed(OmokRoundRepo()))

    assertEquals(
      runRaw(omokHandler, " \tomok \n seed   demo1234   freestyle   H8   A1  "),
      "seeded omok round demo1234: ruleSet=freestyle ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(
      runRaw(omokHandler, "omok show demo1234"),
      "omok round demo1234: ruleSet=freestyle ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(
      runRaw(omokHandler, "omok clear demo1234"),
      "cleared omok round demo1234: ruleSet=freestyle ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(runRaw(omokHandler, "omok show demo1234"), "no omok round state for demo1234")
    assertEquals(runRaw(omokHandler, "omok clear demo1234"), "no omok round state to clear for demo1234")
