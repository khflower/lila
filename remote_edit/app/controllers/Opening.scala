package controllers

import play.api.mvc.*

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.core.net.Crawler
import lila.core.security.IsProxy
import lila.opening.OpeningQuery.queryFromUrl
import lila.security.UserAgentParser

final class Opening(env: Env) extends LilaController(env):

  private def openingHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.openingHub(detail))

  def index(q: Option[String] = None) = Open:
    openingHub((~q).trim)

  private val ipRateLimit =
    env.security.ipTrust.rateLimit(50, 10.minutes, "opening.byKeyAndMoves", _.proxyMultiplier(3))

  def byKeyAndMoves(key: String, moves: String) = Open:
    openingHub(List(key, moves).filter(_.nonEmpty).mkString("/"))

  def config(thenTo: String) = OpenBody:
    NoCrawlers:
      val redir = Redirect:
        lila.common.HTTPRequest.referer(ctx.req) | {
          if thenTo.isEmpty || thenTo == "index" then routes.Opening.index().url
          else if thenTo.startsWith("q:") then routes.Opening.index(thenTo.drop(2).some).url
          else routes.Opening.byKeyAndMoves(thenTo, "").url
        }
      bindForm(lila.opening.OpeningConfig.form)(
        _ => redir,
        cfg => redir.withCookies(env.opening.config.write(cfg))
      )

  def wikiWrite(key: String, moves: String) = SecureBody(_.OpeningWiki) { ctx ?=> me ?=>
    env.opening.api
      .lookup(queryFromUrl(key, moves.some), Crawler.No, IsProxy.empty)
      .map(_.flatMap(_.query.exactOpening))
      .orNotFound: op =>
        val redirect = Redirect(routes.Opening.byKeyAndMoves(key, moves))
        bindForm(lila.opening.OpeningWiki.form)(
          _ => redirect,
          text =>
            for _ <- env.opening.wiki.write(op, text, me.userId)
            yield
              env.irc.api.openingEdit(me.light, key, moves)
              redirect
        )
  }

  def tree = Open:
    openingHub("tree")
