package lila.setup

import chess.{ ByColor, Rated }

import lila.common.Bus
import lila.core.perf.UserWithPerfs
import lila.core.game.{ Source, newGame }
import lila.lobby.{ SetupBus, Seek }
import lila.omok.RuleSet

final private[setup] class Processor(
    gameApi: lila.core.game.GameApi,
    gameRepo: lila.core.game.GameRepo,
    userApi: lila.core.user.UserApi,
    onStart: lila.core.game.OnStart
)(using Executor, lila.core.game.IdGenerator, lila.core.game.NewPlayer):

  def ai(config: AiConfig)(using me: Option[Me]): Fu[Pov] = for
    me <- me.map(_.value).traverse(userApi.withPerf(_, config.perfType))
    pov <- config.pov(me)
    _ <- gameRepo.insertDenormalized(pov.game)
    _ = onStart.exec(pov.gameId)
  yield pov

  def apiAi(config: ApiAiConfig)(using me: Me): Fu[Pov] = for
    me <- userApi.withPerf(me, config.perfType)
    pov <- config.pov(me.some)
    _ <- gameRepo.insertDenormalized(pov.game)
    _ = onStart.exec(pov.gameId)
  yield pov

  def friendOmok(config: FriendConfig, ruleSet: RuleSet): Fu[Processor.OmokFriendGame] =
    config
      .fenGame: chessGame =>
        summon[lila.core.game.IdGenerator].game.map: gameId =>
          lila.core.game
            .newGame(
              chess = chessGame,
              players = ByColor: c =>
                summon[lila.core.game.NewPlayer].anon(c, none),
              rated = if lila.core.game.allowRated(config.variant, chessGame.clock.map(_.config)) then config.rated else Rated.No,
              source = Source.Friend,
              daysPerTurn = config.makeDaysPerTurn,
              pgnImport = None
            )
            .withId(gameId)
            .start
      .flatMap: game =>
        val hostColor = config.creatorColor
        val host = Pov(game, hostColor)
        val guest = Pov(game, !hostColor)
        for
          _ <- gameRepo.insertDenormalized(game)
          _ = onStart.exec(game.id)
        yield Processor.OmokFriendGame(game, host, guest)

  def hook(
      config: HookConfig,
      sri: lila.core.socket.Sri,
      sid: Option[String],
      blocking: lila.core.pool.Blocking,
      omok: Boolean = false,
      omokRuleSet: Option[String] = none
  )(using me: Option[UserWithPerfs]): Fu[Processor.HookResult] =
    import Processor.HookResult.*
    config.hook(sri, me, sid, blocking) match
      case Left(hook) =>
        fuccess:
          val actualHook = if omok then hook.copy(omok = true, omokRuleSet = omokRuleSet) else hook
          Bus.pub(SetupBus.AddHook(actualHook))
          CreatedHook(actualHook)
      case Right(Some(seek)) => me.fold(fuccess(Refused))(u => createSeekIfAllowed(seek, u.id))
      case _ => fuccess(Refused)

  def createSeekIfAllowed(seek: Seek, owner: UserId): Fu[Processor.HookResult] =
    gameApi.nbPlaying(owner).map { nbPlaying =>
      import Processor.HookResult.*
      if lila.core.game.maxPlaying <= nbPlaying
      then Refused
      else
        Bus.pub(SetupBus.AddSeek(seek))
        CreatedSeek(seek.id)
    }

object Processor:

  final case class OmokFriendGame(
      game: lila.core.game.Game,
      host: Pov,
      guest: Pov
  )

  enum HookResult:
    case CreatedHook(hook: lila.lobby.Hook)
    case CreatedSeek(id: String)
    case Refused
