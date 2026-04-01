package lila.api

import scala.concurrent.Await
import scala.concurrent.Promise

import lila.common.{ Bus, CliCommand }
import lila.core.id.GameFullId
import lila.round.{ OmokDemoSeed, OmokRoundRepo, OmokStartScaffold }

class OmokCliRoutingTest extends munit.FunSuite:

  given Executor = scala.concurrent.ExecutionContext.global

  private val omokCliModuleClass = getClass.getClassLoader.loadClass("lila.round.OmokCli$")
  private val omokCliModule = omokCliModuleClass.getField("MODULE$").get(null)
  private val omokCliHandler = omokCliModuleClass.getMethod(
    "handler",
    classOf[OmokDemoSeed],
    classOf[OmokStartScaffold],
    classOf[scala.concurrent.ExecutionContextExecutor]
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
      .invoke(
        omokCliModule,
        seed,
        OmokStartScaffold(repo, _ => fuccess(false)),
        scala.concurrent.ExecutionContext.global
      )
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
    val fullId = GameFullId("demo1234abcd")
    val omokHandler = omokCliHandler
      .invoke(
        omokCliModule,
        OmokDemoSeed(repo),
        OmokStartScaffold(repo, candidate => fuccess(candidate == fullId)),
        scala.concurrent.ExecutionContext.global
      )
      .asInstanceOf[PartialFunction[List[String], Fu[String]]]

    assert(omokHandler.isDefinedAt(List("omok", "start", "demo1234abcd")), "start route should exist")
    assert(
      !omokHandler.isDefinedAt(List("omok", "start", "demo1234abcd", "freestyle", "extra")),
      "extra start args should not route"
    )
    assertEquals(
      runRaw(omokHandler, "omok start demo1234abcd"),
      "started omok scaffold demo1234 -> /demo1234abcd: ruleSet=renju ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      runRaw(omokHandler, "omok start demo1234abcd freestyle"),
      "restarted omok scaffold demo1234 -> /demo1234abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      runRaw(omokHandler, "omok start ghost123abcd"),
      "ERROR no real round for full id 'ghost123abcd'; expected an existing player fullId"
    )
    assertEquals(
      runRaw(omokHandler, "omok show demo1234"),
      "omok round demo1234: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
