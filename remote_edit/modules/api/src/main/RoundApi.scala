package lila.api

import chess.format.Fen
import play.api.libs.json.*
import reactivemongo.api.bson.*

import lila.analyse.{ Analysis, JsonView as analysisJson }
import lila.api.Context.given
import lila.common.HTTPRequest
import lila.common.Json.given
import lila.core.id.GameId
import lila.db.dsl.{ *, given }
import lila.game.OmokGameSidecar
import lila.omok.OmokAnalyseDto
import scalalib.data.Preload
import lila.core.i18n.Translate
import lila.core.perm.Granter
import lila.core.user.GameUsers
import lila.pref.Pref
import lila.puzzle.PuzzleOpening
import lila.round.{ Forecast, JsonView, OmokRoundState }
import lila.simul.Simul
import lila.swiss.GameView as SwissView
import lila.tournament.GameView as TourView
import lila.tree.{ ExportOptions, Tree }
import lila.game.GameExt.timeForFirstMove

object RoundApi:

  type StoredOmokLookup = GameId => Fu[Option[OmokGameSidecar]]

  import lila.game.Game.BSONFields as G

  private object StoredOmokBson:
    val ruleSet = "ruleSet"
    val moves = "moves"
    val swapCount = "swapCount"
    val lastSwapPly = "lastSwapPly"
    val forceSimpleFifth = "forceSimpleFifth"
    val finalSwapUsed = "finalSwapUsed"
    val candidateMode = "candidateMode"
    val candidateSelection = "candidateSelection"
    val candidates = "candidates"
    val openingHistory = "openingHistory"
    val ai = "ai"

  val noStoredOmokLookup: StoredOmokLookup = _ => fuccess(None)
  private[api] val storedOmokProjection = $doc(G.id -> true, G.omok -> true)

  def storedOmokLookup(gameRepo: lila.game.GameRepo)(using Executor): StoredOmokLookup =
    gameId =>
      gameRepo.coll
        .one[Bdoc]($id(gameId), storedOmokProjection)
        .flatMap:
          case None => fuccess(None)
          case Some(doc) =>
            readStoredOmok(doc) match
              case Right(stored) => fuccess(stored)
              case Left(err)     => fufail(RuntimeException(err))

  private[api] def readStoredOmok(doc: Bdoc): Either[String, Option[OmokGameSidecar]] =
    doc.getAsOpt[Bdoc](G.omok) match
      case None => Right(None)
      case Some(omok) =>
        for
          gameId <- doc.getAsOpt[GameId](G.id).toRight("invalid stored omok projection: missing game id")
          ruleSet <- omok.getAsOpt[String](StoredOmokBson.ruleSet).toRight("invalid stored omok projection: missing ruleSet")
          moves <-
            if omok.contains(StoredOmokBson.moves) then
              omok
                .getAsOpt[List[String]](StoredOmokBson.moves)
                .map(_.toVector)
                .toRight("invalid stored omok projection: moves must be a string list")
            else Right(Vector.empty)
          candidates <-
            if omok.contains(StoredOmokBson.candidates) then
              omok
                .getAsOpt[List[String]](StoredOmokBson.candidates)
                .map(_.toVector)
                .toRight("invalid stored omok projection: candidates must be a string list")
            else Right(Vector.empty)
          openingHistory <-
            if omok.contains(StoredOmokBson.openingHistory) then
              omok
                .getAsOpt[List[String]](StoredOmokBson.openingHistory)
                .map(_.toVector)
                .toRight("invalid stored omok projection: openingHistory must be a string list")
            else Right(Vector.empty)
          ai =
            omok.getAsOpt[Bdoc](StoredOmokBson.ai).map: aiDoc =>
              lila.omok.OmokAiConfig(
                provider = aiDoc.getAsOpt[String]("provider").getOrElse("rapfi"),
                mode = aiDoc.getAsOpt[String]("mode").getOrElse("browser"),
                aiColor = aiDoc.getAsOpt[String]("aiColor"),
                threads = aiDoc.getAsOpt[Int]("threads").getOrElse(1),
                moveTimeMs = aiDoc.getAsOpt[Int]("moveTimeMs"),
                depth = aiDoc.getAsOpt[Int]("depth"),
                nodes = aiDoc.getAsOpt[Long]("nodes"),
                analysisEnabled = aiDoc.getAsOpt[Boolean]("analysisEnabled").getOrElse(true)
              )
        yield Some(
          OmokGameSidecar(
            _id = gameId,
            ruleSet = ruleSet,
            moves = moves,
            swapCount = omok.getAsOpt[Int](StoredOmokBson.swapCount).getOrElse(0),
            lastSwapPly = omok.getAsOpt[Int](StoredOmokBson.lastSwapPly),
            forceSimpleFifth = omok.getAsOpt[Boolean](StoredOmokBson.forceSimpleFifth).getOrElse(false),
            finalSwapUsed = omok.getAsOpt[Boolean](StoredOmokBson.finalSwapUsed).getOrElse(false),
            candidateMode = omok.getAsOpt[Boolean](StoredOmokBson.candidateMode).getOrElse(false),
            candidateSelection = omok.getAsOpt[Boolean](StoredOmokBson.candidateSelection).getOrElse(false),
            candidates = candidates,
            openingHistory = openingHistory,
            ai = ai
          )
        )

  def getOrHydrateOmok(
      omokRoundRepo: lila.round.OmokRoundRepo,
      gameId: GameId,
      loadStoredOmok: StoredOmokLookup
  )(using Executor): Fu[Either[String, Option[OmokRoundState]]] =
    omokRoundRepo.getOrHydrateAsync(gameId)(loadStoredOmok(gameId))

final private[api] class RoundApi(
    jsonView: JsonView,
    noteApi: lila.round.NoteApi,
    forecastApi: lila.round.ForecastApi,
    bookmarkApi: lila.bookmark.BookmarkApi,
    gameRepo: lila.game.GameRepo,
    tourApi: lila.tournament.TournamentApi,
    swissApi: lila.swiss.SwissApi,
    simulApi: lila.simul.SimulApi,
    puzzleOpeningApi: lila.puzzle.PuzzleOpeningApi,
    externalEngineApi: lila.analyse.ExternalEngineApi,
    getLightTeam: lila.core.team.LightTeam.GetterSync,
    userApi: lila.user.UserApi,
    prefApi: lila.pref.PrefApi,
    getLightUser: lila.core.LightUser.GetterSync,
    userLag: lila.socket.UserLagCache,
    omokRoundRepo: lila.round.OmokRoundRepo,
    loadStoredOmok: RoundApi.StoredOmokLookup
)(using Executor):

  def player(
      pov: Pov,
      users: Preload[GameUsers],
      tour: Option[TourView]
  )(using ctx: Context): Fu[JsObject] = {
    for
      initialFen <- gameRepo.initialFen(pov.game)
      users <- users.orLoad(userApi.gamePlayers(pov.game.userIdPair, pov.game.perfKey))
      prefs <- prefApi.get(users.map(_.map(_.user)), pov.color, ctx.pref)
      (json, simul, swiss, note, forecast, bookmarked, omok) <-
        (
          jsonView.playerJson(pov, prefs, users, initialFen, ctxFlags),
          pov.game.simulId.so(simulApi.find),
          swissApi.gameView(pov),
          ctx.myId.ifTrue(ctx.isMobileApi).so(noteApi.get(pov.gameId, _)),
          forecastApi.loadForDisplay(pov),
          bookmarkApi.exists(pov.game, ctx.me),
          withOmok(pov)
        ).tupled
    yield (
      withTournament(pov, tour)
        .compose(withSwiss(swiss))
        .compose(withSimul(simul))
        .compose(withSteps(pov, initialFen))
        .compose(withNote(note))
        .compose(withBookmark(bookmarked))
        .compose(withForecastCount(forecast.map(_.steps.size)))
        .compose(withOpponentSignal(pov))
        .compose(omok)
    )(json)
  }.mon(_.round.api.player)

  def watcher(
      pov: Pov,
      users: GameUsers,
      tour: Option[TourView],
      tv: Option[lila.round.OnTv],
      initialFenO: Option[Option[Fen.Full]] = None // Preload[Option[Fen.Full]]?
  )(using ctx: Context): Fu[JsObject] = {
    for
      initialFen <- initialFenO.fold(gameRepo.initialFen(pov.game))(fuccess)
      given Translate = ctx.translate
      (json, simul, swiss, note, bookmarked, omok) <-
        (
          jsonView.watcherJson(pov, users, ctx.pref.some, ctx.me, tv, initialFen, ctxFlags),
          pov.game.simulId.so(simulApi.find),
          swissApi.gameView(pov),
          ctx.me.ifTrue(ctx.isMobileApi).so(noteApi.get(pov.gameId, _)),
          bookmarkApi.exists(pov.game, ctx.me),
          withOmok(pov)
        ).tupled
    yield (
      withTournament(pov, tour)
        .compose(withSwiss(swiss))
        .compose(withSimul(simul))
        .compose(withNote(note))
        .compose(withBookmark(bookmarked))
        .compose(withSteps(pov, initialFen))
        .compose(omok)
    )(json)
  }.mon(_.round.api.watcher)

  private def ctxFlags(using ctx: Context) =
    ExportOptions(
      blurs = Granter.opt(_.ViewBlurs),
      rating = ctx.pref.showRatings,
      nvui = ctx.blind,
      lichobileCompat = HTTPRequest.isLichobile(ctx.req)
    )

  def review(
      pov: Pov,
      users: GameUsers,
      tv: Option[lila.round.OnTv] = None,
      analysis: Option[Analysis] = None,
      initialFen: Option[Fen.Full],
      withFlags: ExportOptions,
      owner: Boolean = false
  )(using ctx: Context): Fu[JsObject] =
    given Translate = ctx.translate
    (
      jsonView.watcherJson(
        pov,
        users,
        ctx.pref.some,
        ctx.me,
        tv,
        initialFen = initialFen,
        flags = withFlags.copy(blurs = Granter.opt(_.ViewBlurs))
      ),
      tourApi.gameView.analysis(pov.game),
      pov.game.simulId.so(simulApi.find),
      swissApi.gameView(pov),
      ctx.me.ifTrue(ctx.isMobileApi).so(noteApi.get(pov.gameId, _)),
      owner.so(forecastApi.loadForDisplay(pov)),
      withFlags.puzzles.so(pov.game.opening.map(_.opening)).so(puzzleOpeningApi.getClosestTo(_, true)),
      bookmarkApi.exists(pov.game, ctx.me)
    ).mapN: (json, tour, simul, swiss, note, fco, puzzleOpening, bookmarked) =>
      (
        withTournament(pov, tour)
          .compose(withSwiss(swiss))
          .compose(withSimul(simul))
          .compose(withNote(note))
          .compose(withBookmark(bookmarked))
          .compose(withTree(pov, analysis, initialFen, withFlags))
          .compose(withAnalysis(pov.game, analysis))
          .compose(withForecast(pov, fco))
          .compose(withPuzzleOpening(puzzleOpening))
          .compose(withOmokAnalyse)
      )(json)
    .flatMap(externalEngineApi.withExternalEngines)
      .mon(_.round.api.watcher)

  def userAnalysisJson(
      pov: Pov,
      pref: Pref,
      initialFen: Option[Fen.Full],
      orientation: Color,
      owner: Boolean,
      addLichobileCompat: Boolean = false
  )(using Option[Me]) =
    owner
      .so(forecastApi.loadForDisplay(pov))
      .map: fco =>
        withOmokAnalyse:
          withForecast(pov, fco):
            val opts = ExportOptions(lichobileCompat = addLichobileCompat)
            withTree(pov, analysis = none, initialFen, opts):
              jsonView.userAnalysisJson(
                pov,
                pref,
                initialFen,
                orientation,
                owner = owner
              )
      .flatMap(externalEngineApi.withExternalEngines)

  private def withTree(
      pov: Pov,
      analysis: Option[Analysis],
      initialFen: Option[Fen.Full],
      withFlags: ExportOptions
  )(obj: JsObject) =
    obj + ("treeParts" ->
      Tree.makePartitionTreeJson(
        pov.game,
        analysis,
        initialFen | pov.game.variant.initialFen,
        withFlags,
        logChessError = lila.log("api.round").warn
      ))

  private def withSteps(pov: Pov, initialFen: Option[Fen.Full])(obj: JsObject) =
    obj + ("steps" -> lila.round.StepBuilder(
      id = pov.gameId,
      sans = pov.game.sans,
      variant = pov.game.variant,
      initialFen = initialFen | pov.game.variant.initialFen
    ))

  private def withNote(note: String)(json: JsObject) =
    if note.isEmpty then json else json + ("note" -> JsString(note))

  private def withBookmark(v: Boolean)(json: JsObject) =
    json.add("bookmarked" -> v)

  private def withForecastCount(count: Option[Int])(json: JsObject) =
    count.filter(0 !=).fold(json) { c =>
      json + ("forecastCount" -> JsNumber(c))
    }

  private def withOpponentSignal(pov: Pov)(json: JsObject) =
    if pov.game.speed <= chess.Speed.Bullet then
      json.add("opponentSignal", pov.opponent.userId.flatMap(userLag.getLagRating))
    else json

  private def withPuzzleOpening(
      opening: Option[Either[PuzzleOpening.FamilyWithCount, PuzzleOpening.WithCount]]
  )(json: JsObject) =
    json.add(
      "puzzle" -> opening
        .map {
          case Left(p) => (p.family.key.toString, p.family.name.value, p.count)
          case Right(p) => (p.opening.key.toString, p.opening.name.value, p.count)
        }
        .map { case (key, name, count) =>
          Json.obj("key" -> key, "name" -> name, "count" -> count)
        }
    )

  private val omitOmok: JsObject => JsObject = json => json

  private def withOmok(pov: Pov): Fu[JsObject => JsObject] =
    RoundApi
      .getOrHydrateOmok(omokRoundRepo, pov.gameId, loadStoredOmok)
      .map:
        case Right(Some(state)) => json => json + ("omok" -> state.analyseDto.asJson)
        case Right(None)        => omitOmok
        case Left(err) =>
          lila.log("api.round").warn(s"[omok] failed to hydrate ${pov.gameId}: $err")
          omitOmok

  // Reserve a stable namespace for future omok analyse boot data.
  private def withOmokAnalyse(json: JsObject) =
    json + ("omok" -> OmokAnalyseDto.empty.asJson)

  private def withForecast(pov: Pov, fco: Option[Forecast])(json: JsObject) =
    if pov.game.forecastable then
      json + (
        "forecast" -> {
          if pov.forecastable then
            fco.fold[JsValue](Json.obj("none" -> true)) { fc =>
              import Forecast.given
              Json.toJson(fc)
            }
          else Json.obj("onMyTurn" -> true)
        }
      )
    else json

  private def withAnalysis(g: Game, o: Option[Analysis])(json: JsObject) =
    json.add(
      "analysis",
      o.map { analysisJson.bothPlayers(g.startedAtPly, _) }
    )

  def withTournament(pov: Pov, viewO: Option[TourView])(json: JsObject)(using Translate) =
    json.add("tournament" -> viewO.map { v =>
      Json
        .obj(
          "id" -> v.tour.id,
          "name" -> v.tour.name(full = false),
          "running" -> v.tour.isStarted
        )
        .add("secondsToFinish" -> v.tour.isStarted.option(v.tour.secondsToFinish))
        .add("berserkable" -> v.tour.isStarted.option(v.tour.berserkable))
        // mobile app API BC / should use game.expiration instead
        .add("nbSecondsForFirstMove" -> v.tour.isStarted.option {
          pov.game.timeForFirstMove.toSeconds
        })
        .add("ranks" -> v.ranks)
        .add(
          "top",
          v.top.map:
            lila.tournament.JsonView.top(_, getLightUser)
        )
        .add(
          "team",
          v.teamVs
            .map(_.teams(pov.color))
            .map: id =>
              getLightTeam(id).fold(Json.obj("name" -> id)): team =>
                Json.obj(
                  "name" -> team.name,
                  "flair" -> team.flair
                )
        )
    })

  def withSwiss(sv: Option[SwissView])(json: JsObject) =
    json.add("swiss" -> sv.map: s =>
      Json
        .obj(
          "id" -> s.swiss.id,
          "running" -> s.swiss.isStarted
        )
        .add("ranks" -> s.ranks.map: r =>
          Json.obj(
            "white" -> r.whiteRank,
            "black" -> r.blackRank
          )))

  private def withSimul(simulOption: Option[Simul])(json: JsObject) =
    json.add(
      "simul",
      simulOption.map: simul =>
        Json.obj(
          "id" -> simul.id,
          "hostId" -> simul.hostId,
          "name" -> simul.name,
          "nbPlaying" -> simul.playingPairings.size
        )
    )
