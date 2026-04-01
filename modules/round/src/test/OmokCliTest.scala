package lila.round

import scala.concurrent.Await

import chess.{ ByColor, Rated }

import lila.core.game.{ Player, Source, newGame }
import lila.core.id.{ GameFullId, GameId, GamePlayerId }

class OmokCliTest extends munit.FunSuite:
  private given Executor = scala.concurrent.ExecutionContext.global

  private def run(handler: PartialFunction[List[String], Fu[String]], args: List[String]): String =
    Await.result(handler(args), 1.second)

  private def scaffold(repo: OmokRoundRepo, existing: Set[GameFullId]) =
    OmokStartScaffold(repo, fullId => fuccess(existing(fullId)))

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

  test("seed show and clear route through the omok demo helper"):
    val repo = OmokRoundRepo()
    val handler = OmokCli.handler(OmokDemoSeed(repo), scaffold(repo, Set.empty))

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
    val repo = OmokRoundRepo()
    val handler = OmokCli.handler(OmokDemoSeed(repo), scaffold(repo, Set.empty))

    assert(!handler.isDefinedAt(Nil))
    assert(!handler.isDefinedAt(List("omok")))
    assert(!handler.isDefinedAt(List("omok", "seed")))
    assert(!handler.isDefinedAt(List("omok", "start", "demo1234abcd", "freestyle", "extra")))
    assert(!handler.isDefinedAt(List("omok", "start", "demo1234abcd", "extra", "ignored")))
    assert(!handler.isDefinedAt(List("omok", "show", "demo1234", "extra")))
    assert(!handler.isDefinedAt(List("omok", "clear", "demo1234", "extra")))
    assert(!handler.isDefinedAt(List("omok", "unknown", "demo1234")))

  test("comma-split cli argv still seeds the expected omok moves"):
    val repo = OmokRoundRepo()
    val handler = OmokCli.handler(OmokDemoSeed(repo), scaffold(repo, Set.empty))
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

  test("start routes through the typed omok scaffold entry point for existing full ids"):
    val repo = OmokRoundRepo()
    val fullId = GameFullId("demo1234abcd")
    val handler = OmokCli.handler(OmokDemoSeed(repo), scaffold(repo, Set(fullId)))

    assert(handler.isDefinedAt(List("omok", "start", "demo1234abcd")))
    assert(handler.isDefinedAt(List("omok", "start", "demo1234abcd", "freestyle")))
    assertEquals(
      run(handler, List("omok", "start", "demo1234abcd")),
      "started omok scaffold demo1234 -> /demo1234abcd: ruleSet=renju ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      run(handler, List("omok", "start", "demo1234abcd", "freestyle")),
      "restarted omok scaffold demo1234 -> /demo1234abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      run(handler, List("omok", "start", "demo1234abcd", "unknown")),
      "ERROR invalid rule set 'unknown'; expected one of: renju, freestyle"
    )
    assertEquals(
      run(handler, List("omok", "start", "ghost123abcd")),
      "ERROR no real round for full id 'ghost123abcd'; expected an existing player fullId"
    )
    assertEquals(
      run(handler, List("omok", "show", "demo1234")),
      "omok round demo1234: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )

  test("start without a full id creates a native omok round"):
    val repo = OmokRoundRepo()
    val handler = OmokCli.handler(OmokDemoSeed(repo), nativeScaffold(repo))

    assert(handler.isDefinedAt(List("omok", "start")))
    assert(handler.isDefinedAt(List("omok", "start", "freestyle")))
    assertEquals(
      run(handler, List("omok", "start", "freestyle")),
      "started native omok round native01: black=/native01wxyz white=/native01abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(
      run(handler, List("omok", "show", "native01")),
      "omok round native01: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
