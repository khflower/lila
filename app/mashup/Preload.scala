package lila.app
package mashup

import play.api.libs.json.*

import lila.core.game.Game
import lila.core.perf.UserWithPerfs
import lila.event.Event
import lila.playban.TempBan
import lila.simul.{ Simul, SimulIsFeaturable }
import lila.streamer.LiveStreams
import lila.swiss.Swiss
import lila.timeline.Entry
import lila.tournament.Tournament
import lila.ublog.UblogPost
import lila.user.{ LightUserApi, Me, User }

final class Preload(
    tv: lila.tv.Tv,
    gameRepo: lila.game.GameRepo,
    perfsRepo: lila.user.UserPerfsRepo,
    timelineApi: lila.timeline.EntryApi,
    liveStreamApi: lila.streamer.LiveApi,
    dailyPuzzle: lila.puzzle.DailyPuzzle.Try,
    lobbyApi: lila.api.LobbyApi,
    playbanApi: lila.playban.PlaybanApi,
    lightUserApi: LightUserApi,
    roundProxy: lila.round.GameProxyRepo,
    omokRoundRepo: lila.round.OmokRoundRepo,
    simulIsFeaturable: SimulIsFeaturable,
    getLastUpdates: lila.feed.Feed.GetLastUpdates,
    ublogApi: lila.ublog.UblogApi,
    unreadCount: lila.msg.MsgUnreadCount,
    relayHome: lila.relay.RelayHomeApi,
    notifyApi: lila.notify.NotifyApi,
    clasApi: lila.clas.ClasApi
)(using Executor):

  import Preload.*

  def apply(
      tours: Fu[List[Tournament]],
      swiss: Option[Swiss],
      events: Fu[List[Event]],
      simuls: Fu[List[Simul]],
      streamerSpots: Int
  )(using ctx: Context): Fu[Homepage] = for
    nbNotifications <- ctx.me.so(notifyApi.unreadCount(_))
    withPerfs <- ctx.user.traverse(perfsRepo.withPerfs)
    given Option[UserWithPerfs] = withPerfs
    lobby <- lobbyApi.get
      .mon(_.lobby.segment("lobbyApi"))
      .zip(tours.mon(_.lobby.segment("tours")))
      .zip(events.mon(_.lobby.segment("events")))
      .zip(simuls.mon(_.lobby.segment("simuls")))
      .zip(tv.getBestGame.mon(_.lobby.segment("tvBestGame")))
      .zip((ctx.userId.so(timelineApi.userEntries)).mon(_.lobby.segment("timeline")))
      .zip((ctx.noBot.so(dailyPuzzle())).mon(_.lobby.segment("puzzle")))
      .zip:
        ctx.kid.no.so:
          liveStreamApi.all
            .dmap(_.homepage(streamerSpots, ctx.acceptLanguages).withTitles(lightUserApi))
            .mon(_.lobby.segment("streams"))
      .zip((ctx.userId.so(playbanApi.currentBan)).mon(_.lobby.segment("playban")))
      .zip(ctx.blind.so(ctx.me).so(roundProxy.urgentGames))
      .zip(fetchOngoingOmokGames)
      .zip(ublogApi.myCarousel)
      .zip:
        ctx.userId
          .ifTrue(nbNotifications > 0)
          .filterNot(liveStreamApi.isStreaming)
          .so(unreadCount.hasLichessMsg)
      .map { payload =>
        val p1 = payload._1
        val lichessMsg = payload._2
        val p2 = p1._1
        val ublogPosts = p1._2
        val p3 = p2._1
        val ongoingOmokGames = p2._2
        val p4 = p3._1
        val blindGames = p3._2
        val p5 = p4._1
        val playban = p4._2
        val p6 = p5._1
        val streams = p5._2
        val p7 = p6._1
        val puzzle = p6._2
        val p8 = p7._1
        val entries = p7._2
        val p9 = p8._1
        val feat = p8._2
        val p10 = p9._1
        val simuls = p9._2
        val p11 = p10._1
        val events = p10._2
        val p12 = p11._1
        val tours = p11._2
        val data = p12._1
        val povs = p12._2
        LobbyPayload(
          data = data,
          povs = povs,
          tours = tours,
          events = events,
          simuls = simuls,
          feat = feat,
          entries = entries,
          puzzle = puzzle,
          streams = streams,
          playban = playban,
          blindGames = blindGames,
          ongoingOmokGames = ongoingOmokGames,
          ublogPosts = ublogPosts,
          lichessMsg = lichessMsg
        )
      }
    (currentGame, _) <- ctx.me
      .soUse(currentGameMyTurn(lobby.povs, lightUserApi.sync))
      .mon(_.lobby.segment("currentGame"))
      .zip:
        lightUserApi
          .preloadMany(lobby.entries.flatMap(_.userIds).toList)
          .mon(_.lobby.segment("lightUsers"))
    classes <- ctx.myId.so(me => clasApi.isStudent(me).so(clasApi.clas.ofStudent(me, 4)))
  yield Homepage(
    lobby.data,
    lobby.entries,
    lobby.tours,
    swiss,
    lobby.events,
    relayHome.spotlight.get,
    lobby.simuls,
    lobby.feat,
    lobby.puzzle,
    lobby.streams.excludeUsers(lobby.events.flatMap(_.hostedBy)),
    lobby.playban,
    currentGame,
    simulIsFeaturable,
    lobby.blindGames,
    lobby.ongoingOmokGames,
    getLastUpdates(),
    lobby.ublogPosts,
    classes,
    withPerfs,
    hasUnreadLichessMessage = lobby.lichessMsg
  )

  private def fetchOngoingOmokGames: Fu[List[Game]] =
    val ids = omokRoundRepo.snapshot.keys.toList
    ids.nonEmpty.so:
      gameRepo
        .gamesFromSecondary(ids)
        .flatMap(roundProxy.upgradeIfPresent)
        .dmap(_.filter(_.playable).sortBy(game => -game.movedAt.toMillis).take(12))

  def currentGameMyTurn(using me: Me): Fu[Option[CurrentGame]] =
    gameRepo
      .playingRealtimeNoAi(me)
      .flatMap:
        _.map { roundProxy.pov(_, me) }.parallel.dmap(_.flatten)
      .flatMap:
        currentGameMyTurn(_, lightUserApi.sync)

  private def currentGameMyTurn(povs: List[Pov], lightUser: lila.core.LightUser.GetterSync)(using
      me: Me
  ): Fu[Option[CurrentGame]] =
    ~povs.collectFirst:
      case p1 if p1.game.nonAi && p1.game.hasClock && p1.isMyTurn =>
        roundProxy.pov(p1.gameId, me).dmap(_ | p1).map { pov =>
          val opponent = lila.game.Namer.playerTextBlocking(pov.opponent)(using lightUser)
          CurrentGame(pov = pov, opponent = opponent).some
        }

object Preload:

  private case class LobbyPayload(
      data: JsObject,
      povs: List[Pov],
      tours: List[Tournament],
      events: List[Event],
      simuls: List[Simul],
      feat: Option[Game],
      entries: Vector[Entry],
      puzzle: Option[lila.puzzle.DailyPuzzle.WithHtml],
      streams: LiveStreams.WithTitles,
      playban: Option[TempBan],
      blindGames: List[Pov],
      ongoingOmokGames: List[Game],
      ublogPosts: List[UblogPost.PreviewPost],
      lichessMsg: Boolean
  )

  case class Homepage(
      data: JsObject,
      userTimeline: Vector[Entry],
      tours: List[Tournament],
      swiss: Option[Swiss],
      events: List[Event],
      relays: List[lila.relay.RelayCard],
      simuls: List[Simul],
      featured: Option[Game],
      puzzle: Option[lila.puzzle.DailyPuzzle.WithHtml],
      streams: LiveStreams.WithTitles,
      playban: Option[TempBan],
      currentGame: Option[Preload.CurrentGame],
      isFeaturable: Simul => Boolean,
      blindGames: List[Pov],
      ongoingOmokGames: List[Game],
      lastUpdates: List[lila.feed.Feed.Update],
      ublogPosts: List[UblogPost.PreviewPost],
      classes: List[lila.clas.Clas],
      me: Option[UserWithPerfs],
      hasUnreadLichessMessage: Boolean
  )

  case class CurrentGame(pov: Pov, opponent: String)
