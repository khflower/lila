package controllers

import play.api.libs.json.*
import play.api.mvc.*
import scalalib.Json.given
import scalalib.paginator.Paginator

import lila.analyse.Analysis
import lila.app.{ *, given }
import lila.common.{ Bus, HTTPRequest }
import lila.core.id.RelayRoundId
import lila.core.misc.lpv.LpvEmbed
import lila.core.socket.Sri
import lila.core.study.StudyOrder
import lila.core.data.ErrorMsg
import lila.study.JsonView.JsData
import lila.study.PgnDump.WithFlags
import lila.study.Study.WithChapter
import lila.study.{ BecomeStudyAdmin, Who }
import lila.study.{ Chapter, Orders, Settings, Study as StudyModel, StudyForm }
import lila.tree.Node.partitionTreeWriter
import com.fasterxml.jackson.core.JsonParseException
import lila.ui.Page

final class Study(
    env: Env,
    editorC: => Editor,
    userAnalysisC: => UserAnalysis,
    apiC: => Api
) extends LilaController(env):

  private def pgnDump = env.study.pgnDump
  private def reviewHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.reviewHub(detail))

  def search(text: String, page: Int, order: Option[StudyOrder]) =
    OpenOrScopedBody(parse.anyContent)(_.Study.Read, _.Web.Mobile):
      reviewHub(s"study/search/${text.take(100).trim}/$page")

  def homeLang = LangPage(routes.Study.allDefault())(reviewHub("study"))

  def allDefault(page: Int) = OpenOrScoped(_.Study.Read, _.Web.Mobile):
    reviewHub(s"study/$page")

  def all(order: StudyOrder, page: Int) = OpenOrScoped(_.Study.Read, _.Web.Mobile):
    reviewHub(s"study/all/$order/$page")

  private def allResults(order: StudyOrder, page: Int)(using ctx: Context) =
    Reasonable(page):
      order match
        case order if !Orders.withoutSelector.contains(order) =>
          Redirect(routes.Study.allDefault(page))
        case order =>
          for
            pag <- env.study.pager.all(order, page)
            _ <- preloadMembers(pag)
            res <- negotiate(
              Ok.page(views.study.list.all(pag, order)),
              apiStudies(pag)
            )
          yield res

  def byOwnerDefault(username: UserStr, page: Int) = Open:
    reviewHub(s"study/by/$username/$page")

  def byOwner(username: UserStr, order: StudyOrder, page: Int) = Open:
    reviewHub(s"study/by/$username/$order/$page")

  def mine(order: StudyOrder, page: Int) = AuthOrScoped(_.Web.Mobile) { ctx ?=> me ?=>
    reviewHub(s"study/mine/$order/$page")
  }

  def minePublic(order: StudyOrder, page: Int) = AuthOrScoped(_.Web.Mobile) { ctx ?=> me ?=>
    reviewHub(s"study/public/$order/$page")
  }

  def minePrivate(order: StudyOrder, page: Int) = AuthOrScoped(_.Web.Mobile) { ctx ?=> me ?=>
    reviewHub(s"study/private/$order/$page")
  }

  def mineMember(order: StudyOrder, page: Int) = AuthOrScoped(_.Web.Mobile) { ctx ?=> me ?=>
    reviewHub(s"study/member/$order/$page")
  }

  def mineLikes(order: StudyOrder, page: Int) = AuthOrScoped(_.Web.Mobile) { ctx ?=> me ?=>
    reviewHub(s"study/likes/$order/$page")
  }

  private type StudyPager = Paginator[StudyModel.WithChaptersAndLiked]

  private def MyStudyPager(
      makePager: (StudyOrder, Int) => Me ?=> Fu[StudyPager],
      render: (StudyPager, StudyOrder) => Context ?=> Me ?=> Fu[Page]
  ) = (order: StudyOrder, page: Int) =>
    AuthOrScoped(_.Web.Mobile) { ctx ?=> me ?=>
      for
        pager <- makePager(order, page)
        _ <- preloadMembers(pager)
        res <- negotiate(Ok.async(render(pager, order)), apiStudies(pager))
      yield res
    }

  def byTopic(name: String, order: StudyOrder, page: Int) = Open:
    reviewHub(s"study/topic/$name/$order/$page")

  private def preloadMembers(pag: Paginator[StudyModel.WithChaptersAndLiked]) =
    env.user.lightUserApi.preloadMany(
      pag.currentPageResults.view
        .flatMap(_.study.members.members.values.take(StudyModel.previewNbMembers))
        .map(_.id)
        .toSeq
    )

  private def apiStudies(pager: Paginator[StudyModel.WithChaptersAndLiked]) =
    given Writes[StudyModel.WithChaptersAndLiked] = Writes(env.study.jsonView.pagerData)
    Ok(Json.obj("paginator" -> pager))

  private def orRelayRedirect(id: StudyId, chapterId: Option[StudyChapterId] = None)(
      f: => Fu[Result]
  )(using ctx: Context): Fu[Result] =
    if HTTPRequest.isRedirectable(ctx.req)
    then
      env.relay.api
        .byIdWithTour(id.into(RelayRoundId))
        .flatMap:
          _.fold(f): rt =>
            Redirect(chapterId.fold(rt.call)(rt.call))
    else f

  private def showQuery(query: Option[WithChapter])(using ctx: Context): Fu[Result] =
    Found(query): oldSc =>
      CanView(oldSc.study) {
        if !oldSc.study.notable && HTTPRequest.isCrawler(req).yes
        then notFound
        else
          negotiate(
            html =
              val noCrawler = HTTPRequest.isCrawler(ctx.req).no
              for
                (sc, data) <- getJsonData(oldSc, withChapters = true)
                chat <- noCrawler.so(chatOf(sc.study))
                sVersion <- noCrawler.so(env.study.version(sc.study.id))
                streamers <- noCrawler.so(streamerCache.get(sc.study.id))
                page <- renderPage(views.study.show(sc.study, sc.chapter, data, chat, sVersion, streamers))
              yield Ok(page)
                .withCanonical(routes.Study.chapter(sc.study.id, sc.chapter.id))
                .enforceCrossSiteIsolation
            ,
            json = for
              (sc, data) <- getJsonData(
                oldSc,
                withChapters = getBool("chapters") || HTTPRequest.isLichobile(ctx.req)
              )
              chatOpt <- HTTPRequest.isXhr(ctx.req).not.so(chatOf(sc.study))
              jsChat <- chatOpt.traverse: c =>
                env.chat.json.mobile(c.chat, writeable = ctx.userId.so(sc.study.canChat))
            yield Ok:
              Json.obj(
                "study" -> data.study.add("chat" -> jsChat),
                "analysis" -> data.analysis
              )
          )
      }(privateUnauthorizedFu(oldSc.study), privateForbiddenFu(oldSc.study))
    .dmap(_.noCache)

  private[controllers] def getJsonData(sc: WithChapter, withChapters: Boolean)(using
      ctx: Context
  ): Fu[(WithChapter, JsData)] =
    for
      (studyFromDb, chapter) <- env.study.api.maybeResetAndGetChapter(sc.study, sc.chapter)
      study <- env.relay.api.reconfigureStudy(studyFromDb, chapter)
      previews <- withChapters.optionFu(env.study.preview.jsonList(study.id))
      _ <- env.user.lightUserApi.preloadMany(study.members.ids.toList)
      pov = userAnalysisC.makePov(chapter.root.fen.some, chapter.setup.variant)
      analysis <- chapterAnalysis(sc)
      division = analysis.isDefined.option(env.study.serverEvalMerger.divisionOf(chapter))
      baseData <- env.analyse.externalEngine.withExternalEngines(
        env.round.jsonView.userAnalysisJson(
          pov,
          ctx.pref,
          chapter.root.fen.some,
          chapter.setup.orientation,
          owner = false,
          division = division
        )
      )
      withMembers = !study.isRelay || isGrantedOpt(_.StudyAdmin) || ctx.me.exists(study.isMember)
      studyJson <- env.study.jsonView.full(study, chapter, previews, withMembers = withMembers)
      lichobile = HTTPRequest.isLichobile(ctx.req)
    yield WithChapter(study, chapter) -> JsData(
      study = studyJson,
      analysis = baseData
        .add("treeParts" -> partitionTreeWriter(chapter.root, lichobile = lichobile).some)
        .add("analysis" -> analysis.map { env.analyse.jsonView.bothPlayers(chapter.root.ply, _) })
    )

  private def chapterAnalysis(sc: WithChapter) =
    sc.chapter.serverEval
      .exists(_.done)
      .so(env.analyse.repo.byId(Analysis.Id(sc.study.id, sc.chapter.id)))

  def show(id: StudyId) = OpenOrScoped(_.Study.Read, _.Web.Mobile):
    reviewHub(s"study/$id")

  def chapter(id: StudyId, chapterId: StudyChapterId) = OpenOrScoped(_.Study.Read, _.Web.Mobile):
    reviewHub(s"study/$id/$chapterId")

  def chapterConfig(id: StudyId, chapterId: StudyChapterId) = Open:
    reviewHub(s"study/$id/$chapterId/config")

  private[controllers] def chatOf(study: lila.study.Study)(using ctx: Context) = {
    ctx.kid.no && ctx.noBot // no public chats for kids and bots
  }.optionFu:
    env.chat.api.userChat
      .findMine(study.id.into(ChatId))
      .mon(_.chat.fetch("study"))

  def createAs = AuthBody { ctx ?=> me ?=>
    bindForm(StudyForm.importGame.form)(
      _ => Redirect(routes.Study.byOwnerDefault(me.username)),
      data =>
        for
          owner <- env.study.api.recentByOwnerWithChapterCount(me, 50)
          contrib <- env.study.api.recentByContributorWithChapterCount(me, 50)
          res <-
            if owner.isEmpty && contrib.isEmpty then createStudy(data)
            else
              val back = HTTPRequest
                .referer(ctx.req)
                .orElse:
                  data.fen.map(fen => editorC.editorUrl(fen, data.variant | chess.variant.Variant.default))
              Ok.page(views.study.create(data, owner, contrib, back))
        yield res
    )
  }

  def create = AuthBody { ctx ?=> me ?=>
    bindForm(StudyForm.importGame.form)(
      _ => Redirect(routes.Study.byOwnerDefault(me.username)),
      createStudy
    )
  }

  private def createStudy(data: StudyForm.importGame.Data)(using ctx: Context, me: Me) =
    val cost = if !data.isNewStudy then 0 else if coachOrTitled then 1 else 2
    limit.studyCreate(me.userId -> ctx.ip, rateLimited, cost):
      Found(env.study.api.importGame(lila.study.StudyMaker.ImportGame(data), me, ctx.pref.showRatings)): sc =>
        Redirect(routes.Study.chapter(sc.study.id, sc.chapter.id))

  def apiCreate = ScopedBody(_.Study.Write) { _ ?=> me ?=>
    bindForm(StudyForm.form)(
      jsonFormError,
      data =>
        limit.studyCreate(me.userId -> ctx.ip, rateLimited, if coachOrTitled then 1 else 2):
          for sc <- env.study.api.create(data)
          yield JsonOk(Json.obj("id" -> sc.study.id))
    )
  }

  private def coachOrTitled(using me: Me) = isGranted(_.Coach) || me.hasTitle

  def delete(id: StudyId) = Auth { _ ?=> me ?=>
    Found(env.study.api.byIdAndOwnerOrAdmin(id, me)): study =>
      for
        round <- env.relay.api.deleteRound(id.into(RelayRoundId))
        _ <- env.study.api.delete(study)
      yield round match
        case None => Redirect(routes.Study.mine(StudyOrder.hot))
        case Some(tour) => Redirect(routes.RelayTour.show(tour.slug, tour.id))
  }

  def apiChapterDelete(id: StudyId, chapterId: StudyChapterId) = ScopedBody(_.Study.Write) { _ ?=> me ?=>
    Found(env.study.api.byIdAndOwnerOrAdmin(id, me)): study =>
      env.study.api.deleteChapter(study.id, chapterId)(Who(me.userId, Sri("api"))).inject(NoContent)
  }

  def clearChat(id: StudyId) = Auth { _ ?=> me ?=>
    env.study.api
      .isOwnerOrAdmin(id, me)
      .flatMapz:
        env.chat.api.userChat.clear(id.into(ChatId))
      .inject(Redirect(routes.Study.show(id)))
  }

  private def doImportPgn(id: StudyId, data: StudyForm.importPgn.Data, sri: Sri)(
      f: (List[Chapter], Option[ErrorMsg]) => Result
  )(using ctx: Context, me: Me): Future[Result] =
    val chapterDatas = data.toChapterDatas
    limit.studyPgnImport(me, rateLimited, cost = chapterDatas.size):
      env.study.api
        .importPgns(id, chapterDatas, sticky = data.sticky, ctx.pref.showRatings)(Who(me, sri))
        .map(f.tupled)

  def importPgn(id: StudyId) = AuthBody { ctx ?=> me ?=>
    get("sri").so: sri =>
      bindForm(StudyForm.importPgn.form)(
        doubleJsonFormError,
        data =>
          doImportPgn(id, data, Sri(sri)): (_, errors) =>
            errors.fold(NoContent)(BadRequest(_))
      )
  }

  def apiImportPgn(id: StudyId) = ScopedBody(_.Study.Write) { ctx ?=> me ?=>
    bindForm(StudyForm.importPgn.form)(
      jsonFormError,
      data =>
        doImportPgn(id, data, Sri("api")): (chapters, errors) =>
          import lila.study.ChapterPreview.json.given
          val previews = chapters.map(env.study.preview.fromChapter(_))
          JsonOk(Json.obj("chapters" -> previews, "error" -> errors))
    )
  }

  def admin(id: StudyId) = Secure(_.StudyAdmin) { ctx ?=> me ?=>
    Bus.pub(BecomeStudyAdmin(id, me))
    env.study.api
      .becomeAdmin(id, me)
      .inject:
        if HTTPRequest.isXhr(ctx.req) then NoContent else Redirect(routes.Study.show(id))
  }

  def embed(studyId: StudyId, chapterId: StudyChapterId) = Anon:
    reviewHub(s"study/embed/$studyId/$chapterId")

  def cloneStudy(id: StudyId) = Auth { ctx ?=> _ ?=>
    reviewHub(s"study/$id/clone")
  }

  def cloneApply(id: StudyId) = Auth { ctx ?=> me ?=>
    limit.studyClone(me.userId -> ctx.ip, rateLimited, if coachOrTitled then 1 else 3):
      Found(env.study.api.byId(id)) { prev =>
        CanView(prev, prev.settings.cloneable.some) {
          env.study.api
            .cloneWithChat(me, prev)
            .map: study =>
              Redirect(routes.Study.show((study | prev).id))
        }(privateUnauthorizedFu(prev), privateForbiddenFu(prev))
      }
  }

  def pgn(id: StudyId) = Open:
    pgnWithFlags(id, identity)

  def apiPgn(id: StudyId) = AnonOrScoped(_.Study.Read, _.Web.Mobile): ctx ?=>
    pgnWithFlags(id, identity)

  def pgnWithFlags(id: StudyId, flags: Update[WithFlags])(using Context) =
    Found(env.study.api.byId(id)): study =>
      HeadLastModifiedAt(study.updatedAt):
        val limiter = if study.isRelay then limit.relayPgn else limit.studyPgn
        limiter[Fu[Result]](req.ipAddress, rateLimited, msg = id.value):
          CanView(study, study.settings.shareable.some)(doPgn(study, flags))(
            privateUnauthorizedFu(study),
            privateForbiddenFu(study)
          )

  private def doPgn(study: StudyModel, flags: Update[WithFlags])(using RequestHeader) =
    def makeStudySource = pgnDump.chaptersOf(study, _ => flags(pgnDump.requestPgnFlags()))
    val pgnSource = akka.stream.scaladsl.Source.futureSource:
      if study.isRelay
      then env.relay.pgnStream.ofStudy(study).map(_ | makeStudySource)
      else fuccess(makeStudySource)
    Ok.chunked(pgnSource.throttle(20, 1.second))
      .asAttachmentStream(s"${pgnDump.filename(study)}.pgn")
      .as(pgnContentType)
      .withDateHeaders(lastModified(study.updatedAt))

  def chapterPgn(id: StudyId, chapterId: StudyChapterId) = Open:
    doChapterPgn(id, chapterId, notFound, privateUnauthorizedFu, privateForbiddenFu)

  def apiChapterPgn(id: StudyId, chapterId: StudyChapterId) =
    AnonOrScoped(_.Study.Read, _.Web.Mobile): ctx ?=>
      doChapterPgn(
        id,
        chapterId,
        fuccess(NotFound("Study or chapter not found")),
        _ => fuccess(Unauthorized("This study is now private")),
        _ => fuccess(Forbidden("This study is now private"))
      )

  private def doChapterPgn(
      id: StudyId,
      chapterId: StudyChapterId,
      studyNotFound: => Fu[Result],
      studyUnauthorized: StudyModel => Fu[Result],
      studyForbidden: StudyModel => Fu[Result]
  )(using Context) =
    env.study.api
      .byIdWithChapter(id, chapterId)
      .flatMap:
        _.fold(studyNotFound) { case sc @ WithChapter(study, chapter) =>
          CanView(study) {
            def makeChapterPgn = pgnDump.ofChapter(study, pgnDump.requestPgnFlags())(chapter)
            for
              pgn <-
                if study.isRelay
                then env.relay.pgnStream.ofChapter(sc).getOrElse(makeChapterPgn)
                else makeChapterPgn
              analysisJson <- getBool("analysisHeader").so:
                chapterAnalysis(sc).map2: analysis =>
                  val division = env.study.serverEvalMerger.divisionOf(chapter)
                  env.analyse.jsonView.analysisHeader(sc.chapter.root, division, analysis)
              filename = s"${pgnDump.filename(study, chapter)}.pgn"
              res = Ok(pgn.toString).as(pgnContentType).asAttachment(filename)
              resWithAnalysis = analysisJson.fold(res): a =>
                res.withHeaders("X-Lichess-Analysis" -> Json.stringify(a))
            yield resWithAnalysis
          }(studyUnauthorized(study), studyForbidden(study))
        }

  def apiExportPgn(username: UserStr) = OpenOrScoped(_.Study.Read, _.Web.Mobile): ctx ?=>
    val name =
      if username.value == "me"
      then ctx.me.fold(UserName("me"))(_.username)
      else username.into(UserName)
    val userId = name.id
    val isMe = ctx.me.exists(_.is(userId))
    val makeStream = env.study.studyRepo
      .sourceByOwner(userId, isMe)
      .flatMapConcat(pgnDump.chaptersOf(_, _ => pgnDump.requestPgnFlags()))
      .throttle(if isMe then 20 else 10, 1.second)
    apiC.GlobalConcurrencyLimitPerIpAndUserOption(userId.some)(makeStream): source =>
      Ok.chunked(source)
        .asAttachmentStream(s"${name}-${if isMe then "all" else "public"}-studies.pgn")
        .as(pgnContentType)

  def apiListByOwner(username: UserStr) = OpenOrScoped(_.Study.Read, _.Web.Mobile): ctx ?=>
    val isMe = ctx.is(username)
    apiC.jsonDownload:
      env.study.studyRepo
        .sourceByOwner(username.id, isMe)
        .throttle(if isMe then 50 else 20, 1.second)
        .map(lila.study.JsonView.metadata)

  def chapterGif(
      id: StudyId,
      chapterId: StudyChapterId,
      theme: Option[String],
      piece: Option[String],
      showGlyphs: Boolean
  ) = Open:
    Found(env.study.api.byIdWithChapter(id, chapterId)):
      case WithChapter(study, chapter) =>
        CanView(study) {
          env.study.gifExport
            .ofChapter(chapter, theme, piece, showGlyphs)
            .map: stream =>
              Ok.chunked(stream)
                .asAttachmentStream(s"${pgnDump.filename(study, chapter)}.gif")
                .as("image/gif")
            .recover { case lila.core.lilaism.LilaInvalid(msg) =>
              BadRequest(msg)
            }
        }(privateUnauthorizedFu(study), privateForbiddenFu(study))

  def apiChapterTagsUpdate(studyId: StudyId, chapterId: StudyChapterId) =
    AuthOrScopedBody(_.Study.Write) { _ ?=> _ ?=>
      bindForm(StudyForm.chapterTagsForm)(
        jsonFormError,
        pgn => env.study.api.updateChapterTags(studyId, chapterId, pgn).inject(NoContent)
      )
    }

  def apiChapterPgnMovesUpdate(studyId: StudyId, chapterId: StudyChapterId) =
    AuthOrScopedBody(_.Study.Write) { _ ?=> me ?=>
      bindForm(StudyForm.replaceChapterPgnMoves)(
        jsonFormError,
        pgnStr =>
          env.study.api
            .replaceChapterPgnMoves(studyId, chapterId, pgnStr)
            .map:
              if _ then NoContent
              else JsonBadRequest(s"Invalid or forbidden chapter $studyId/$chapterId")
            .recover:
              case lila.study.StudyValidationException(error) => JsonBadRequest(error)
      )
    }

  def topicAutocomplete = Anon:
    get("term").filter(_.nonEmpty) match
      case None => BadRequest("No search term provided")
      case Some(term) =>
        import lila.common.Json.given
        env.study.topicApi.findLike(term, getUserStr("user").map(_.id)).map { JsonOk(_) }

  def topics = OpenOrScoped():
    reviewHub("study/topic")

  def setTopics = AuthBody { ctx ?=> me ?=>
    bindForm(StudyForm.topicsForm)(
      _ => Redirect(routes.Study.topics),
      topics =>
        try env.study.topicApi.userTopics(me, topics).inject(Redirect(routes.Study.topics))
        catch case e: JsonParseException => BadRequest(e.getMessage)
    )
  }

  def staffPicks = Open:
    reviewHub("study/staff-picks")

  def staffPicksPost = SecureBody(_.StudyAdmin) { _ ?=> _ ?=>
    bindForm(env.study.pager.featured.setting.form)(
      _ => Redirect(routes.Study.staffPicks),
      v => env.study.pager.featured.setting.setString(v.toString).inject(Redirect(routes.Study.staffPicks))
    )
  }

  def privateUnauthorizedJson = Unauthorized(jsonError("This study is now private"))
  def privateUnauthorizedFu(study: StudyModel)(using Context) = negotiate(
    Unauthorized.page(views.study.privateStudy(study)),
    privateUnauthorizedJson
  )

  def privateForbiddenJson = forbiddenJson("This study is now private")
  def privateForbiddenFu(study: StudyModel)(using Context) = negotiate(
    Forbidden.page(views.study.privateStudy(study)),
    privateForbiddenJson
  )

  def CanView(study: StudyModel, userSelection: Option[Settings.UserSelection] = none)(
      f: => Fu[Result]
  )(unauthorized: => Fu[Result], forbidden: => Fu[Result])(using me: Option[Me]): Fu[Result] =
    def withUserSelection =
      if userSelection.forall(Settings.UserSelection.allows(_, study, me.map(_.userId))) then f
      else forbidden
    me match
      case _ if !study.isPrivate => withUserSelection
      case None => unauthorized
      case Some(me) if study.members.contains(me.value) => withUserSelection
      case _ => forbidden

  private val streamerCache =
    env.memo.cacheApi[StudyId, List[UserId]](64, "study.streamers"):
      _.expireAfterWrite(10.seconds).buildAsyncFuture: studyId =>
        env.study.findConnectedUsersIn(studyId)(env.streamer.liveApi.streamerUserIds)

  def glyphs(lang: String) = Anon:
    Found(play.api.i18n.Lang.get(lang)): lang =>
      JsonOk:
        lila.study.JsonView.glyphs(using env.translator.to(lang))
      .headerCacheSeconds(3600)
