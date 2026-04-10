package controllers

import java.time.Instant
import scala.concurrent.duration.*

import chess.format.Fen
import play.api.libs.json.Json
import play.api.mvc.{ EssentialAction, Result }

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.core.id.GameFullId
import lila.core.socket.Sri
import lila.game.AnonCookie
import lila.omok.RuleSet
import lila.setup.Processor.HookResult
import lila.setup.ValidFen

final class Setup(
    env: Env,
    challengeC: => Challenge
) extends LilaController(env)
    with lila.web.TheftPrevention:

  import env.setup.{ forms, processor }

  def ai = OpenBody:
    limit.setupBotAi(ctx.userId | UserId(""), rateLimited, cost = ctx.me.exists(_.isBot).so(1)):
      limit.setupPost(ctx.ip, rateLimited):
        bindForm(forms.ai)(
          doubleJsonFormError,
          config =>
            processor.ai(config).flatMap { pov =>
              seedOmokAiIfNeeded(pov, config).flatMap { _ =>
                negotiateApi(
                  html = redirectPov(pov, omok = config.isOmok),
                  api = _ => env.api.roundApi.player(pov, scalalib.data.Preload.none, none).map(Created(_))
                )
              }
            }
        )

  def friend(userId: Option[UserStr]) =
    OpenBody: ctx ?=>
      limit.setupPost(ctx.ip, rateLimited):
        bindForm(forms.friend)(
          doubleJsonFormError,
          config =>
            val normalizedRuleSet = config.omokRuleSet.map(parseOmokRuleSet).getOrElse(RuleSet.Taraguchi10)
            val creatorColor = config.creatorColor
            val normalizedColor =
              if creatorColor == Color.white then lila.lobby.TriColor.White
              else lila.lobby.TriColor.Black
            val session = Setup.OmokFriendInviteStore.create(
              config = config.copy(color = normalizedColor, omokRuleSet = normalizedRuleSet.toString.toLowerCase.some),
              ruleSet = normalizedRuleSet,
              hostUserId = ctx.userId,
              hostSid = ctx.req.sid,
              requestedUser = userId.map(_.value)
            )
            fuccess(Redirect(routes.Setup.omokFriend(session.id, session.hostToken.some)))
        )

  def omokFriend(id: String, host: Option[String]) = Open: ctx ?=>
    Setup.OmokFriendInviteStore.view(id, host, ctx.req.sid, ctx.userId) match
      case Some(view) =>
        view.redirectFullId match
          case Some(fullId) => fuccess(Redirect(routes.Round.omokClaim(fullId)))
          case None =>
            val shareUrl = s"${if ctx.req.secure then "https" else "http"}://${ctx.req.host}${view.sharePath}"
            Ok.page(
              views.omokPages.friendInvite(
                sessionId = view.session.id,
                shareUrl = shareUrl,
                stateUrl = routes.Setup.omokFriendState(view.session.id, host).url,
                confirmUrl = routes.Setup.omokFriendConfirm(view.session.id, host).url,
                isHost = view.isHost,
                guestJoined = view.session.guestJoined,
                ruleSet = renderFriendRuleSet(view.session.ruleSet),
                timeControl = renderFriendTimeControl(view.session.config),
                gameMode = if view.session.config.rated.yes then "Rated" else "Casual",
                side = view.session.config.color.name.capitalize,
                requestedUser = view.session.requestedUser
              )
            )
      case None =>
        NotFound.page(
          views.omokPages.practiceHub("This invite link is missing or expired. Start a fresh omok invite from the lobby.")
        )

  def omokFriendState(id: String, host: Option[String]) = Open: ctx ?=>
    Setup.OmokFriendInviteStore.view(id, host, ctx.req.sid, ctx.userId).fold(notFoundJson()): view =>
      JsonOk(
        Json.obj(
          "ok" -> true,
          "isHost" -> view.isHost,
          "guestJoined" -> view.session.guestJoined,
          "canConfirm" -> (view.isHost && view.session.guestJoined && !view.session.starting && view.session.started.isEmpty),
          "starting" -> view.session.starting,
          "started" -> view.session.started.nonEmpty,
          "redirectUrl" -> view.redirectFullId.map(routes.Round.omokClaim(_).url),
          "ruleSet" -> renderFriendRuleSet(view.session.ruleSet),
          "timeControl" -> renderFriendTimeControl(view.session.config),
          "gameMode" -> (if view.session.config.rated.yes then "Rated" else "Casual"),
          "side" -> view.session.config.color.name.capitalize,
          "requestedUser" -> view.session.requestedUser
        )
      )

  def omokFriendConfirm(id: String, host: Option[String]) = OpenBody: ctx ?=>
    Setup.OmokFriendInviteStore.prepareStart(id, host).fold(
      err => JsonBadRequest(err).toFuccess,
      view =>
        view.redirectFullId match
          case Some(fullId) =>
            JsonOk(Json.obj("ok" -> true, "redirectUrl" -> routes.Round.omokClaim(fullId).url)).toFuccess
          case None =>
            processor
              .friendOmok(view.session.config, view.session.ruleSet)
              .flatMap: game =>
                env.round.omokRoundRepo.put(game.game.id, lila.round.OmokRoundState.initial(view.session.ruleSet))
                val started = Setup.OmokFriendStarted(
                  hostFullId = game.host.fullId,
                  guestFullId = game.guest.fullId
                )
                Setup.OmokFriendInviteStore.completeStart(view.session.id, started)
                JsonOk(
                  Json.obj(
                    "ok" -> true,
                    "redirectUrl" -> routes.Round.omokClaim(game.host.fullId).url
                  )
                ).toFuccess
              .recoverWith {
                case err =>
                  Setup.OmokFriendInviteStore.resetStarting(view.session.id)
                  JsonBadRequest(Option(err.getMessage).filter(_.nonEmpty).getOrElse("Failed to create omok room")).toFuccess
              }
    )

  def omokSolo = Open: ctx ?=>
    Ok.page(views.omokPages.soloBoard)

  private def hookResponse(res: HookResult) = res match
    case HookResult.CreatedHook(hook) =>
      JsonOk:
        Json.obj(
          "ok" -> true,
          "hook" -> hook.render
        )
    case HookResult.CreatedSeek(id) =>
      JsonOk:
        Json.obj(
          "ok" -> true,
          "hook" -> Json.obj("id" -> id)
        )
    case HookResult.Refused => JsonBadRequest(("Game was not created"))

  def hook(sri: Sri) = OpenOrScopedBody(parse.anyContent)(_.Web.Mobile, _.Web.Polygon): ctx ?=>
    NoBot:
      NoPlaybanOrCurrent:
        bindForm(forms.hook)(
          doubleJsonFormError,
          userConfig =>
            limit.setupPost(req.ipAddress, rateLimited):
              limit.setupAnonHook(req.ipAddress, rateLimited, cost = ctx.isAnon.so(1)):
                for
                  me <- ctx.user.traverse(env.user.api.withPerfs)
                  given Perf = me.fold(lila.rating.Perf.default)(_.perfs(userConfig.perfType))
                  blocking <- ctx.userId.so(env.relation.api.fetchBlocking)
                  res <- processor.hook(
                    userConfig.withinLimits.copy(color = lila.lobby.TriColor.Random),
                    sri,
                    req.sid,
                    lila.core.pool.Blocking(blocking),
                    omok = true,
                    omokRuleSet = userConfig.omokRuleSet
                  )(using me)
                yield hookResponse(res)
        )

  def like(sri: Sri, gameId: GameId) = Open:
    NoBot:
      limit.setupPost(ctx.ip, rateLimited):
        NoPlaybanOrCurrent:
          Found(env.game.gameRepo.game(gameId)): game =>
            for
              orig <- ctx.user.traverse(env.user.api.withPerfs)
              blocking <- ctx.userId.so(env.relation.api.fetchBlocking)
              hookConfig = lila.setup.HookConfig.default(ctx.isAuth)
              hookConfigWithRating = get("rr")
                .fold(
                  hookConfig.withRatingRange(
                    orig.fold(lila.rating.Perf.default)(_.perfs(game.perfKey)).intRating.some,
                    get("deltaMin"),
                    get("deltaMax")
                  )
                )(hookConfig.withRatingRange)
                .updateFrom(game)
              allBlocking = lila.core.pool.Blocking(blocking ++ game.userIds)
              hookResult <- processor.hook(hookConfigWithRating, sri, ctx.req.sid, allBlocking)(using orig)
            yield hookResponse(hookResult)

  def boardApiHook = WithBoardApiHookAuthor { (author, reqSri) => ctx ?=>
    forms
      .boardApiHook:
        ctx.isMobileOauth || ctx.isPolygon || (ctx.isAnon && HTTPRequest.isLichessMobile(ctx.req))
      .bindFromRequest()
      .fold(
        doubleJsonFormError,
        config =>
          for
            me <- ctx.me.so(env.user.api.withPerfs)
            blocking <- ctx.me.so(env.relation.api.fetchBlocking(_))
            sri = orUserSri(author)
            _ = lila.mon.lobby.hook
              .apiCreate:
                if ctx.isMobileOauth then env.oAuth.signedClients.mobile.clientId.value
                else if ctx.isPolygon then env.oAuth.signedClients.polygon.clientId.value
                else "other"
              .increment()
            forcedColor <- env.lobby.boardApiHookStream.mustPlayAsColor(config.color)
            res <- forcedColor.match
              case Some(forced) => fuccess(JsonBadRequest(s"You must also play some games as $forced"))
              case None =>
                config
                  .hook(reqSri | sri, me, sid = sri.value.some, lila.core.pool.Blocking(blocking))
                  .match
                    case Left(hook) =>
                      limit.setupPost(req.ipAddress, rateLimited):
                        limit
                          .boardApiConcurrency(author.map(_.id), msg = req.userAgent.value)(
                            env.lobby.boardApiHookStream(hook.copy(boardApi = true))
                          )(jsOptToNdJson)
                          .toFuccess
                    case Right(Some(seek)) =>
                      author match
                        case Left(_) => JsonBadRequest("Anonymous users cannot create seeks").toFuccess
                        case Right(me) =>
                          env.setup.processor.createSeekIfAllowed(seek, me.id).map {
                            case HookResult.Refused => JsonBadRequest("Already playing too many games")
                            case HookResult.CreatedSeek(id) => Ok(Json.obj("id" -> id))
                            case HookResult.CreatedHook(hook) => Ok(Json.obj("id" -> hook.id))
                          }
                    case Right(None) => notFoundJson().toFuccess
          yield res
      )
  }

  def boardApiHookCancel = WithBoardApiHookAuthor { (author, reqSri) => _ ?=>
    env.lobby.boardApiHookStream.cancel(orUserSri(author), reqSri)
    NoContent
  }

  private def orUserSri(author: Either[Sri, lila.user.User]): Sri =
    author.fold(identity, u => Sri(s"user:${u.id}"))

  private def WithBoardApiHookAuthor(
      f: (Either[Sri, lila.user.User], Option[Sri]) => BodyContext[?] ?=> Fu[Result]
  ): EssentialAction =
    AnonOrScopedBody(parse.anyContent)(_.Board.Play, _.Web.Mobile, _.Web.Polygon): ctx ?=>
      NoBot:
        val reqSri = getAs[Sri]("sri")
        ctx.me match
          case Some(u) => f(Right(u), reqSri)
          case None =>
            reqSri match
              case Some(sri) => f(Left(sri), reqSri)
              case None => JsonBadRequest("Authentication required")

  def filterForm = Open:
    Ok.snip(views.setup.filter(forms.filter))

  def validateFen = Open:
    (get("fen").map(Fen.Full.clean): Option[Fen.Full]).flatMap(ValidFen(getBool("strict"))) match
      case None => BadRequest
      case Some(v) => Ok.snip(views.analyse.ui.miniSpan(v.fen.board, v.color))

  def apiAi = ScopedBody(_.Challenge.Write, _.Bot.Play, _.Board.Play, _.Web.Mobile, _.Web.Polygon) {
    ctx ?=> me ?=>
      limit.setupBotAi(me, rateLimited, cost = me.isBot.so(1)):
        limit.setupPost(req.ipAddress, rateLimited):
          bindForm(forms.api.ai)(
            doubleJsonFormError,
            config =>
              processor.apiAi(config).map { pov =>
                val json = env.game.jsonView.apiAiNewGame(pov, config.fen)
                Created(json).as(JSON)
              }
          )
  }

  private def seedOmokAiIfNeeded(pov: Pov, config: lila.setup.AiConfig): Fu[Unit] =
    if !config.isOmok then fuccess(())
    else
      val ruleSet = config.omokRuleSet.map(parseOmokAiRuleSet).getOrElse(RuleSet.Renju)
      val aiColor = if pov.color == Color.white then Color.black else Color.white
      env.round.omokRoundRepo.put(pov.gameId, lila.round.OmokRoundState.initial(ruleSet, config.omokAiConfig(aiColor)))
      fuccess(())

  private def parseOmokAiRuleSet(raw: String): RuleSet =
    raw.trim.toLowerCase match
      case "freestyle" => RuleSet.Freestyle
      case "taraguchi10" | "taraguchi-10" | "taraguchi" => RuleSet.Taraguchi10
      case _           => RuleSet.Renju

  private def parseOmokRuleSet(raw: String): RuleSet =
    raw.trim.toLowerCase match
      case "freestyle" => RuleSet.Freestyle
      case "renju"     => RuleSet.Renju
      case _           => RuleSet.Taraguchi10

  private def renderFriendRuleSet(ruleSet: RuleSet): String =
    ruleSet.toString match
      case "Taraguchi10" => "Taraguchi-10"
      case value         => value

  private def renderFriendTimeControl(config: lila.setup.FriendConfig): String =
    config.makeClock
      .map(_.show)
      .orElse(config.makeDaysPerTurn.map(days => s"${days.value} days / turn"))
      .getOrElse("Unlimited")

  private[controllers] def redirectPov(pov: Pov, omok: Boolean = false)(using ctx: Context) =
    val redir = Redirect(if omok then routes.Round.omokClaim(pov.fullId) else routes.Round.watcher(pov.gameId, Color.white))
    if ctx.isAuth then redir
    else
      redir.withCookies(
        env.security.lilaCookie.cookie(
          AnonCookie.name,
          pov.playerId.value,
          maxAge = AnonCookie.maxAge.some,
          httpOnly = false.some
        )
      )

object Setup:

  final case class OmokFriendStarted(
      hostFullId: GameFullId,
      guestFullId: GameFullId
  )

  final case class OmokFriendInviteSession(
      id: String,
      hostToken: String,
      config: lila.setup.FriendConfig,
      ruleSet: RuleSet,
      hostUserId: Option[UserId],
      hostSid: Option[String],
      requestedUser: Option[String],
      guestJoined: Boolean,
      guestUserId: Option[UserId],
      guestSid: Option[String],
      started: Option[OmokFriendStarted],
      starting: Boolean,
      createdAt: Instant
  ):
    def sharePath: String = s"/omok/friend/$id"
    def hostPath: String = s"$sharePath?host=$hostToken"

  final case class OmokFriendInviteView(session: OmokFriendInviteSession, isHost: Boolean):
    def sharePath: String = session.sharePath
    def redirectFullId: Option[GameFullId] =
      session.started.map(start => if isHost then start.hostFullId else start.guestFullId)

  object OmokFriendInviteStore:
    private val sessions = collection.mutable.Map.empty[String, OmokFriendInviteSession]
    private val ttl = 12.hours

    def create(
        config: lila.setup.FriendConfig,
        ruleSet: RuleSet,
        hostUserId: Option[UserId],
        hostSid: Option[String],
        requestedUser: Option[String]
    ): OmokFriendInviteSession = synchronized {
      prune()
      val session = OmokFriendInviteSession(
        id = randomId(8),
        hostToken = randomId(12),
        config = config,
        ruleSet = ruleSet,
        hostUserId = hostUserId,
        hostSid = hostSid,
        requestedUser = requestedUser,
        guestJoined = false,
        guestUserId = none,
        guestSid = none,
        started = none,
        starting = false,
        createdAt = nowInstant
      )
      sessions.update(session.id, session)
      session
    }

    def view(
        id: String,
        hostToken: Option[String],
        sid: Option[String],
        userId: Option[UserId]
    ): Option[OmokFriendInviteView] = synchronized {
      prune()
      sessions.get(id).map: session =>
        val isHost = hostToken.contains(session.hostToken)
        val updated =
          if isHost then session
          else
            session.copy(
              guestJoined = true,
              guestUserId = userId.orElse(session.guestUserId),
              guestSid = sid.orElse(session.guestSid)
            )
        sessions.update(id, updated)
        OmokFriendInviteView(updated, isHost)
    }

    def prepareStart(id: String, hostToken: Option[String]): Either[String, OmokFriendInviteView] = synchronized {
      prune()
      sessions.get(id).toRight("Invite session not found").flatMap: session =>
        if !hostToken.contains(session.hostToken) then Left("Only the inviter can start this omok room")
        else if session.started.nonEmpty then Right(OmokFriendInviteView(session, isHost = true))
        else if session.starting then Left("This omok room is already starting")
        else if !session.guestJoined then Left("Wait for another player to open the invite link first")
        else
          val updated = session.copy(starting = true)
          sessions.update(id, updated)
          Right(OmokFriendInviteView(updated, isHost = true))
    }

    def completeStart(id: String, started: OmokFriendStarted): Unit = synchronized {
      sessions.get(id).foreach: session =>
        sessions.update(id, session.copy(started = started.some, starting = false))
    }

    def resetStarting(id: String): Unit = synchronized {
      sessions.get(id).foreach: session =>
        sessions.update(id, session.copy(starting = false))
    }

    private def prune(): Unit =
      val threshold = nowInstant.minusSeconds(ttl.toSeconds)
      sessions.keys.foreach: id =>
        sessions.get(id).foreach: session =>
          if session.createdAt.isBefore(threshold) then sessions.remove(id)

    private def randomId(length: Int): String =
      scalalib.ThreadLocalRandom.nextString(length)
