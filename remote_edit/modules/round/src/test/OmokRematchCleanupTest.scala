package lila.round

import chess.{ ByColor, Rated }

import lila.common.Bus
import lila.core.game.{ FinishGame, Player, Source, newGame }
import lila.core.id.{ GameId, GamePlayerId }
import lila.core.perf.UserWithPerfs
import lila.core.round.DeleteUnplayed
import lila.game.OmokGameSidecar
import lila.omok.RuleSet

class OmokRematchCleanupTest extends munit.FunSuite:

  given Executor = scala.concurrent.ExecutionContext.global

  private def makeGame(gameId: GameId) =
    newGame(
      chess.Game(chess.variant.Standard),
      ByColor(Player(GamePlayerId("abcd"), _, none)),
      rated = Rated.No,
      source = Source.Api,
      pgnImport = none
    ).withId(gameId)

  private def repoWithStore(backing: scala.collection.mutable.Map[GameId, OmokGameSidecar]) =
    OmokRoundRepo(
      stored =>
        backing.put(stored._id, stored)
        (),
      gameId =>
        backing.remove(gameId)
        ()
    )

  test("finish cleanup evicts cache but preserves durable omok sidecar for rematch"):
    val stored = scala.collection.mutable.Map.empty[GameId, OmokGameSidecar]
    val repo = repoWithStore(stored)
    val player = OmokMovePlayer(repo)
    val cleanup = OmokRoundLifecycleCleanup(player)
    val gameId = GameId("tararem1")

    try
      player.ensure(gameId, RuleSet.Taraguchi10)

      assertEquals(repo.get(gameId).map(_.ruleSet), Some(RuleSet.Taraguchi10))
      assertEquals(stored.get(gameId).map(_.ruleSet), Some("taraguchi10"))

      Bus.pub(FinishGame(makeGame(gameId), ByColor(_ => none[UserWithPerfs])))

      assertEquals(repo.get(gameId), None)
      assertEquals(stored.get(gameId).map(_.ruleSet), Some("taraguchi10"))

      val hydrated = repo.getOrHydrate(gameId)(stored.get(gameId)).toOption.flatten

      assertEquals(hydrated.map(_.ruleSet), Some(RuleSet.Taraguchi10))
    finally cleanup.unsubscribe()

  test("delete-unplayed cleanup still clears durable omok sidecar"):
    val stored = scala.collection.mutable.Map.empty[GameId, OmokGameSidecar]
    val repo = repoWithStore(stored)
    val player = OmokMovePlayer(repo)
    val cleanup = OmokRoundLifecycleCleanup(player)
    val gameId = GameId("tararem2")

    try
      player.ensure(gameId, RuleSet.Taraguchi10)

      assertEquals(stored.get(gameId).map(_.ruleSet), Some("taraguchi10"))

      Bus.pub(DeleteUnplayed(gameId))

      assertEquals(repo.get(gameId), None)
      assertEquals(stored.get(gameId), None)
    finally cleanup.unsubscribe()
