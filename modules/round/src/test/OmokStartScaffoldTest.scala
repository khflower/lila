package lila.round

import scala.concurrent.Await

import chess.{ ByColor, Rated }

import lila.core.game.{ Player, Source, newGame }
import lila.core.id.{ GameFullId, GameId, GamePlayerId }
import lila.omok.RuleSet

class OmokStartScaffoldTest extends munit.FunSuite:
  private given Executor = scala.concurrent.ExecutionContext.global

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

  test("startNew creates a fresh omok round and reveals real black and white full ids"):
    val repo = OmokRoundRepo()
    val game = makeNativeGame(GameId("native01"))
    val scaffold = OmokStartScaffold(
      repo,
      new OmokNativeGameStarter(() => fuccess(OmokNativeStartGame(game, game.fullIds)))
    )

    val result = Await.result(scaffold.startNew(Some("freestyle")), 1.second).toOption.get
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
    val scaffold = OmokStartScaffold(repo)

    val result = scaffold.start(GameFullId("demo1234abcd"))
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
    val scaffold = OmokStartScaffold(repo)

    repo.put(
      GameId("demo1234"),
      OmokRoundState.initial().copy(moves = Vector(lila.omok.Move(lila.omok.Pos.unsafe(7, 7))))
    )

    val result = scaffold.start(GameFullId("demo1234wxyz"), RuleSet.Freestyle)
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
    val scaffold = OmokStartScaffold(repo)

    assertEquals(
      scaffold.start("bad-full-id").left.map(_.message),
      Left("invalid full id 'bad-full-id'; expected 12 characters matching [A-Za-z0-9_-]")
    )
    assertEquals(
      scaffold.start("demo1234abcd", Some("unknown")).left.map(_.message),
      Left("invalid rule set 'unknown'; expected one of: renju, freestyle")
    )
    assertEquals(
      Await.result(scaffold.startNew(Some("unknown")), 1.second).left.map(_.message),
      Left("invalid rule set 'unknown'; expected one of: renju, freestyle")
    )
    assertEquals(repo.get(GameId("demo1234")), None)
