package lila.api

import scala.concurrent.Await

import chess.{ ByColor, Rated }

import lila.core.game.{ Player, Source, newGame }
import lila.core.id.{ GameId, GamePlayerId }
import lila.round.{ OmokDemoSeed, OmokNativeGameStarter, OmokNativeStartGame, OmokRoundRepo, OmokStartScaffold }

class OmokStartCliPathTest extends munit.FunSuite:
  given Executor = scala.concurrent.ExecutionContext.global
  private val omokCliModuleClass = getClass.getClassLoader.loadClass("lila.round.OmokCli$")
  private val omokCliModule = omokCliModuleClass.getField("MODULE$").get(null)
  private val omokCliHandler = omokCliModuleClass.getMethod(
    "handler",
    classOf[OmokDemoSeed],
    classOf[OmokStartScaffold],
    classOf[scala.concurrent.ExecutionContextExecutor]
  )

  private def run(handler: PartialFunction[List[String], Fu[String]], command: String): String =
    Await.result(handler(CliInput.parse(command)), 1.second)

  private def nativeScaffold(repo: OmokRoundRepo) =
    val game = newGame(
      chess.Game(chess.variant.Standard),
      ByColor(color =>
        if color.white then Player(GamePlayerId("abcd"), color, none)
        else Player(GamePlayerId("wxyz"), color, none)
      ),
      rated = Rated.No,
      source = Source.Api,
      pgnImport = none
    ).withId(GameId("native01")).start
    OmokStartScaffold(repo, new OmokNativeGameStarter(() => fuccess(OmokNativeStartGame(game, game.fullIds))))

  private def handler(repo: OmokRoundRepo) =
    omokCliHandler
      .invoke(
        omokCliModule,
        OmokDemoSeed(repo),
        nativeScaffold(repo),
        scala.concurrent.ExecutionContext.global
      )
      .asInstanceOf[PartialFunction[List[String], Fu[String]]]

  test("raw dev cli strings route native omok starts through the supported argv shapes"):
    val repo = OmokRoundRepo()
    val omokHandler = handler(repo)

    assertEquals(
      run(omokHandler, " \tomok \n start   freestyle "),
      "started native omok round native01: black=/native01wxyz white=/native01abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      run(omokHandler, "omok show native01"),
      "omok round native01: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )

  test("raw dev cli strings with extra omok start argv are not routed"):
    val repo = OmokRoundRepo()
    val omokHandler = handler(repo)

    assert(!omokHandler.isDefinedAt(CliInput.parse("omok start demo1234abcd freestyle extra")))
