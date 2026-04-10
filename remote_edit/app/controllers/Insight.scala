package controllers
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.*

import lila.app.{ *, given }
import lila.core.i18n.Translate
import lila.insight.{ InsightDimension, InsightMetric }

final class Insight(env: Env) extends LilaController(env):

  private def insightsHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.insightsHub(detail))

  def refresh(username: UserStr) = OpenOrScoped(): ctx ?=>
    AccessibleApi(username): user =>
      val byMod = isGrantedOpt(_.MarkBooster) || isGrantedOpt(_.MarkEngine)
      env.insight.api.indexAll(user, force = byMod).inject(Ok)

  def index(username: UserStr) = OpenOrScoped(): ctx ?=>
    insightsHub(username.toString)

  def path(username: UserStr, metric: String, dimension: String, filters: String) = Open:
    insightsHub(List(username.toString, metric, dimension, ~lila.common.String.decodeUriPath(filters)).filter(_.nonEmpty).mkString("/"))

  private def doPath(user: lila.user.User, metric: String, dimension: String, filters: String)(using
      Context
  ) =
    import lila.insight.InsightApi.UserStatus.*
    env.insight.api
      .userStatus(user)
      .flatMap:
        case NoGame => Ok.page(views.site.message.insightNoGames(user))
        case Empty => Ok.page(views.insight.empty(user))
        case s =>
          for
            insightUser <- env.insight.api.insightUser(user)
            prefId <- env.insight.share.getPrefId(user)
            page <- renderPage:
              views.insight.index(
                u = user,
                insightUser = insightUser,
                prefId = prefId,
                ui = env.insight.jsonView
                  .ui(insightUser.families, insightUser.openings, asMod = isGrantedOpt(_.ViewBlurs)),
                question = env.insight.jsonView.question(metric, dimension, filters),
                stale = s == Stale
              )
          yield Ok(page)

  def json(username: UserStr) =
    OpenOrScopedBody(parse.json)(): ctx ?=>
      AccessibleApi(username) { processQuestion(_, ctx.body) }

  private def processQuestion(user: lila.user.User, body: Request[JsValue])(using Translate) =
    body.body
      .validate[lila.insight.JsonQuestion]
      .fold(
        err => BadRequest(jsonError(err.toString)).toFuccess,
        _.question.fold(BadRequest.toFuccess): q =>
          env.insight.api
            .ask(q, user)
            .flatMap(lila.insight.InsightChart.fromAnswer(env.user.lightUser))
            .map(env.insight.jsonView.chartWrites.writes)
            .map { Ok(_) }
      )

  private def Accessible(username: UserStr)(f: lila.user.User => Fu[Result])(using Context) =
    isAccessible(username)(f, u => Forbidden.page(views.insight.forbidden(u)))

  private def AccessibleApi(username: UserStr)(f: lila.user.User => Fu[Result])(using Context) =
    isAccessible(username)(f, _ => Forbidden)

  private def isAccessible(
      username: UserStr
  )(f: lila.user.User => Fu[Result], fallback: lila.user.User => Fu[Result])(using Context): Fu[Result] =
    Found(meOrFetch(username)): u =>
      env.insight.share
        .grant(u)
        .flatMap:
          if _ then f(u)
          else fallback(u)
