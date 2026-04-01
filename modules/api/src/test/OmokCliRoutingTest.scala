package lila.api

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

import lila.common.{ Bus, CliCommand }
import lila.round.{ OmokDemoSeed, OmokRoundRepo, OmokStartScaffold }

class OmokCliRoutingTest extends munit.FunSuite:

  private val omokCliModuleClass = getClass.getClassLoader.loadClass("lila.round.OmokCli$")
  private val omokCliModule = omokCliModuleClass.getField("MODULE$").get(null)
  private val omokCliHandler = omokCliModuleClass.getMethod(
    "handler",
    classOf[OmokDemoSeed],
    classOf[OmokStartScaffold]
  )

  private def runRaw(handler: PartialFunction[List[String], Fu[String]], command: String): String =
    val subscription = lila.common.Cli.handle(handler)
    try
      val promise = Promise[scalalib.data.LazyFu[String]]()
      Bus.pub(CliCommand(CliInput.parse(command), promise))
      Await.result(promise.future.flatMap(_.value), 1.second)
    finally Bus.unsub[CliCommand](subscription)

  test("raw dev cli command strings reach omok seed show and clear routing"):
    val repo = OmokRoundRepo()
    val seed = OmokDemoSeed(repo)
    val omokHandler = omokCliHandler
      .invoke(omokCliModule, seed, OmokStartScaffold(repo))
      .asInstanceOf[PartialFunction[List[String], Fu[String]]]

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

  test("raw dev cli command strings reach omok start routing"):
    val repo = OmokRoundRepo()
    val omokHandler = omokCliHandler
      .invoke(omokCliModule, OmokDemoSeed(repo), OmokStartScaffold(repo))
      .asInstanceOf[PartialFunction[List[String], Fu[String]]]

    assertEquals(
      runRaw(omokHandler, "omok start demo1234abcd freestyle"),
      "started omok scaffold demo1234 -> /demo1234abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      runRaw(omokHandler, "omok show demo1234"),
      "omok round demo1234: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
