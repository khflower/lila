package controllers

import chess.format.Fen
import chess.variant.{ FromPosition, Standard, Variant, Chess960 }
import chess.{ Position, ByColor }
import play.api.libs.json.Json
import play.api.mvc.*

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.core.id.GameFullId
import lila.tree.ExportOptions

final class UserAnalysis(
    env: Env,
    gameC: => Game
) extends LilaController(env)
    with lila.web.TheftPrevention:

  private val loadStoredOmok = lila.api.RoundApi.storedOmokLookup(env.game.gameRepo)

  private def omokAnalysisHome(using Context): Fu[Result] =
    Ok.page(views.omokPages.analysisHome).map(_.enforceCrossSiteIsolation)

  def index = Open:
    omokAnalysisHome

  def parseArg(arg: String) =
    Open:
      omokAnalysisHome

  private def load(urlFen: Option[String], variant: Variant) = Open:
    val inputFen: Option[Fen.Full] = urlFen.orElse(get("fen")).flatMap(readFen)
    val chess960PositionNum: Option[Int] = variant.chess960.so:
      getInt("position").orElse: // no input fen or num defaults to standard start position
        Chess960.positionNumber(inputFen | variant.initialFen)
    val decodedFen: Option[Fen.Full] = inputFen.orElse(chess960PositionNum.flatMap(Chess960.positionToFen))
    val pov = makePov(decodedFen, variant)
    val orientation = get("color").flatMap(Color.fromName) | pov.color
    for
      data <- env.api.roundApi.userAnalysisJson(
        pov,
        ctx.pref,
        decodedFen,
        orientation,
        owner = false
      )
      page <- renderPage(views.analyse.ui.userAnalysis(data, pov, chess960PositionNum))
    yield Ok(page)
      .withCanonical(routes.UserAnalysis.index)
      .enforceCrossSiteIsolation

  def pgn(pgn: String) = Open:
    omokAnalysisHome

  def embed = Anon:
    InEmbedContext:
      fuccess(Ok.snip(views.omokPages.analysisEmbed).enforceCrossSiteIsolation)

  def readFen(from: String): Option[Fen.Full] = lila.common.String
    .decodeUriPath(from)
    .filter(_.trim.nonEmpty)
    .map(Fen.Full.clean)

  private[controllers] def makePov(fen: Option[Fen.Full], variant: Variant): Pov =
    makePov:
      Position.AndFullMoveNumber(variant, fen.filter(_.value.nonEmpty))

  private[controllers] def makePov(from: Position.AndFullMoveNumber): Pov =
    Pov(
      lila.core.game
        .newGame(
          chess = chess.Game(position = from.position, ply = from.ply),
          players = ByColor(lila.game.Player.make(_, none)),
          rated = chess.Rated.No,
          source = lila.core.game.Source.Api,
          pgnImport = None
        )
        .withId(lila.game.Game.syntheticId),
      from.position.color
    )

  // correspondence premove aka forecast
  // also used by lichobile for post-game analysis
  def game(id: GameId, color: Color) = Open:
    Found(env.game.gameRepo.game(id)): g =>
      env.round.proxyRepo.upgradeIfPresent(g).flatMap { game =>
        val pov = Pov(game, color)
        negotiateApi(
          html =
            lila.api.RoundApi
              .getOrHydrateOmok(env.round.omokRoundRepo, pov.gameId, loadStoredOmok)
              .flatMap:
                case Right(Some(state)) =>
                  Ok.page(views.omokPages.analysisGame(pov, state)).map:
                    _.noCache.enforceCrossSiteIsolation
                case _ =>
                  Ok.page(views.omokPages.analysisHome).map:
                    _.noCache.enforceCrossSiteIsolation
          ,
          api = _ => mobileAnalysis(pov)
        )
      }

  private def mobileAnalysis(pov: Pov)(using ctx: Context): Fu[Result] = for
    initialFen <- env.game.gameRepo.initialFen(pov.game)
    users <- env.user.api.gamePlayers.analysis(pov.game)
    owner = isMyPov(pov)
    _ = gameC.preloadUsers(users)
    analysis <- env.analyse.analyser.get(pov.game)
    crosstable <- env.game.crosstableApi(pov.game)
    data <- env.api.roundApi.review(
      pov,
      users,
      tv = none,
      analysis,
      initialFen = initialFen,
      withFlags = ExportOptions(
        division = true,
        clocks = true,
        movetimes = true,
        rating = ctx.pref.showRatings,
        lichobileCompat = HTTPRequest.isLichobile(ctx.req)
      ),
      owner = owner
    )
  yield
    import lila.game.JsonView.given
    Ok(data.add("crosstable", crosstable))

  private def forecastReload = JsonOk(Json.obj("reload" -> true))

  def forecastsPost(fullId: GameFullId) = AuthOrScopedBodyWithParser(parse.json)(_.Web.Mobile) { ctx ?=> _ ?=>
    import lila.round.Forecast
    Found(env.round.proxyRepo.pov(fullId)): pov =>
      if isTheft(pov) then theftResponse
      else
        ctx.body.body
          .validate[Forecast.Steps]
          .fold(
            err => BadRequest(err.toString),
            forecasts =>
              val fu = for
                _ <- env.round.forecastApi.save(pov, forecasts)
                res <- env.round.forecastApi.loadForDisplay(pov)
              yield res.fold(JsonOk(Json.obj("none" -> true)))(JsonOk(_))
              fu.recover:
                case Forecast.OutOfSync => forecastReload
                case _: lila.core.round.ClientError => forecastReload
          )
  }

  def forecastsGet(fullId: GameFullId) = Scoped(_.Web.Mobile) { _ ?=> _ ?=>
    Found(env.round.proxyRepo.pov(fullId)): pov =>
      JsonOk(env.round.mobile.forecast(pov.game, pov.fullId.anyId))
  }

  def forecastsOnMyTurn(fullId: GameFullId, uci: String) =
    AuthOrScopedBodyWithParser(parse.json)(_.Web.Mobile) { ctx ?=> _ ?=>
      import lila.round.Forecast
      Found(env.round.proxyRepo.pov(fullId)): pov =>
        if isTheft(pov) then theftResponse
        else
          ctx.body.body
            .validate[Forecast.Steps]
            .fold(
              err => BadRequest(err.toString),
              forecasts =>
                for
                  _ <- env.round.forecastApi.playAndSave(pov, uci, forecasts).recoverDefault
                  wait = (1 + Forecast.maxPlies(forecasts).min(10)) * 50
                  _ <- lila.common.LilaFuture.sleep(wait.millis)
                yield forecastReload
            )
    }

  def help = Open:
    Ok.snip(views.omokPages.analysisEmbed)
