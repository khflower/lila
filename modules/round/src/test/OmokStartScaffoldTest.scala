package lila.round

import scala.concurrent.Await

import chess.{ ByColor, Rated }

import lila.core.game.{ Player, Source, newGame }
import lila.core.id.{ GameFullId, GameId, GamePlayerId }
import lila.omok.RuleSet

class OmokStartScaffoldTest extends munit.FunSuite:
  private given Executor = scala.concurrent.ExecutionContext.global

  private def await[A](fa: Fu[A]): A = Await.result(fa, 1.second)

  private def makeNativeGame(gameId: GameId) =
    newGame(
      chess.Game(chess.variant.Standard),
      ByColor(color =>
        if color.white then Player(GamePlayerId("abcd"), color, none)
        else Player(GamePlayerId("wxyz"), color, none)
      ),
      rated = Rated.No,
      source = Source.Api,
      pgnImport = none
    ).withId(gameId).start

  private def scaffold(repo: OmokRoundRepo, existing: Set[GameFullId]) =
    OmokStartScaffold(repo, fullId => fuccess(existing(fullId)))

  test("startNew creates a fresh omok round and reveals real black and white full ids"):
    val repo = OmokRoundRepo()
    val game = makeNativeGame(GameId("native01"))
    val scaffolded = OmokStartScaffold(
      repo,
      new OmokNativeGameStarter(() => fuccess(OmokNativeStartGame(game, game.fullIds)))
    )

    val result = await(scaffolded.startNew(Some("freestyle"))).toOption.get
    val state = repo.get(GameId("native01")).get

    assertEquals(result.fullId, GameFullId("native01wxyz"))
    assertEquals(result.redirectPath, "/native01wxyz")
    assertEquals(
      result.message,
      "started native omok round native01: black=/native01wxyz white=/native01abcd: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(result.nativeFullIds.map(_.white), Some(GameFullId("native01abcd")))
    assertEquals(result.nativeFullIds.map(_.black), Some(GameFullId("native01wxyz")))
    assertEquals(state.ruleSet, RuleSet.Freestyle)
    assertEquals(state.position.ply, 0)
    assertEquals(state.moves, Vector.empty)

  test("start seeds a blank renju state for the requested player full id"):
    val repo = OmokRoundRepo()
    val fullId = GameFullId("demo1234abcd")
    val scaffolded = scaffold(repo, Set(fullId))

    val result = await(scaffolded.start(fullId)).toOption.get
    val state = repo.get(GameId("demo1234")).get

    assertEquals(result.reset, false)
    assertEquals(result.redirectPath, "/demo1234abcd")
    assertEquals(
      result.message,
      "started omok scaffold demo1234 -> /demo1234abcd: ruleSet=renju ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(state.ruleSet, RuleSet.Renju)
    assertEquals(state.position.ply, 0)
    assertEquals(state.moves, Vector.empty)

  test("start resets an existing omok sidecar state back to a blank ruleset-specific root"):
    val repo = OmokRoundRepo()
    val fullId = GameFullId("demo1234wxyz")
    val scaffolded = scaffold(repo, Set(fullId))

    repo.put(
      GameId("demo1234"),
      OmokRoundState.initial().copy(moves = Vector(lila.omok.Move(lila.omok.Pos.unsafe(7, 7))))
    )

    val result = await(scaffolded.start(fullId, RuleSet.Freestyle)).toOption.get
    val state = repo.get(GameId("demo1234")).get

    assertEquals(result.reset, true)
    assertEquals(
      result.message,
      "restarted omok scaffold demo1234 -> /demo1234wxyz: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(state.ruleSet, RuleSet.Freestyle)
    assertEquals(state.position.ply, 0)
    assertEquals(state.moves, Vector.empty)

  test("raw start parsing rejects invalid full ids and rule sets"):
    val repo = OmokRoundRepo()
    val scaffolded = scaffold(repo, Set(GameFullId("demo1234abcd")))

    assertEquals(
      await(scaffolded.start("bad-full-id")).left.map(_.message),
      Left("invalid full id 'bad-full-id'; expected 12 characters matching [A-Za-z0-9_-]")
    )
    assertEquals(
      await(scaffolded.start("demo1234abcd", Some("unknown"))).left.map(_.message),
      Left("invalid rule set 'unknown'; expected one of: renju, freestyle")
    )
    assertEquals(
      await(scaffolded.startNew(Some("unknown"))).left.map(_.message),
      Left("invalid rule set 'unknown'; expected one of: renju, freestyle")
    )
    assertEquals(repo.get(GameId("demo1234")), None)

  test("start rejects unknown full ids instead of scaffolding a fake round"):
    val repo = OmokRoundRepo()
    val scaffolded = scaffold(repo, Set.empty)

    assertEquals(
      await(scaffolded.start("demo1234abcd")).left.map(_.message),
      Left("no real round for full id 'demo1234abcd'; expected an existing player fullId")
    )
    assertEquals(repo.get(GameId("demo1234")), None)
