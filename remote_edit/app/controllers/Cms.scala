package controllers

import play.api.mvc.*

import lila.app.*
import lila.cms.CmsPage
import lila.core.id.{ CmsPageId, CmsPageKey }

final class Cms(env: Env) extends LilaController(env):

  def api = env.cms.api

  private def omokPageFor(key: String)(using Context): Option[Fu[Result]] =
    key match
      case "help" | "faq" => Ok.page(views.omokPages.faq).some
      case "tos"          => Ok.page(views.omokPages.terms).some
      case "privacy" | "about" | "thanks" | "changelog" | "ads" =>
        Ok.page(views.omokPages.info(key)).some
      case "source" => Ok.page(views.omokPages.source).some
      case _        => none

  // crud

  def index = Secure(_.Pages): ctx ?=>
    for
      pages <- api.list
      renderedPage <- renderPage(views.cms.index(pages))
    yield Ok(renderedPage)

  def createForm(key: Option[CmsPageKey]) = Secure(_.Pages) { _ ?=> _ ?=>
    Ok.async(views.cms.create(env.cms.form.create, key))
  }

  def create = SecureBody(_.Pages) { _ ?=> me ?=>
    bindForm(env.cms.form.create)(
      err => BadRequest.async(views.cms.create(err, none)),
      data =>
        val page = data.create(me)
        api.create(page).inject(Redirect(routes.Cms.edit(page.id)).flashSuccess)
    )
  }

  def edit(id: CmsPageId) = Secure(_.Pages) { _ ?=> _ ?=>
    Found(api.withAlternatives(id)): pages =>
      Ok.async(views.cms.edit(env.cms.form.edit(pages.head), pages.head, pages.tail))
  }

  def update(id: CmsPageId) = SecureBody(_.Pages) { _ ?=> me ?=>
    Found(api.withAlternatives(id)): pages =>
      bindForm(env.cms.form.edit(pages.head))(
        err => BadRequest.async(views.cms.edit(err, pages.head, pages.tail)),
        data =>
          api
            .update(pages.head, data)
            .map: page =>
              Redirect(routes.Cms.edit(page.id)).flashSuccess
      )
  }

  def delete(id: CmsPageId) = Secure(_.Pages) { _ ?=> _ ?=>
    Found(api.get(id)): up =>
      api.delete(up.id).inject(Redirect(routes.Cms.index).flashSuccess)
  }

  // pages

  val help = Open:
    pageHit
    Ok.page(views.omokPages.faq)

  val tos = Open:
    pageHit
    Ok.page(views.omokPages.terms)

  def page(key: CmsPageKey, active: Option[String])(using Context) =
    omokPageFor(key.value).getOrElse:
      FoundPage(env.cms.render(key)): p =>
        active.fold(views.cms.lone(p)):
          views.site.page.withMenu(_, p)

  def lonePage(key: CmsPageKey) = Open:
    omokPageFor(key.value).getOrElse:
      orCreateOrNotFound(key): page =>
        page.canonicalPath.filter(_ != req.path && req.path == s"/page/$key") match
          case Some(path) => Redirect(path)
          case None =>
            pageHit
            Ok.async(views.cms.lone(page))

  def orCreateOrNotFound(key: CmsPageKey)(f: CmsPage.Render => Fu[Result])(using Context): Fu[Result] =
    env.cms
      .render(key)
      .flatMap:
        case Some(page) => f(page)
        case None =>
          import lila.ui.Context.ctxMe // no idea why this is needed here
          if isGrantedOpt(_.Pages)
          then Ok.async(views.cms.create(env.cms.form.create, key.some))
          else notFound

  def menuPage(key: CmsPageKey) = Open:
    pageHit
    omokPageFor(key.value).getOrElse:
      FoundPage(env.cms.render(key)):
        views.site.page.withMenu(key.value, _)

  def source = Open:
    pageHit
    Ok.page(views.omokPages.source)

  def variantHome = Open:
    negotiate(
      Ok.async(views.site.variant.home),
      Ok(lila.web.StaticContent.variantsJson)
    )

  import chess.variant.Variant
  def variant(key: Variant.LilaKey) = Open:
    (for
      variant <- Variant(key)
      perfKey <- PerfKey.byVariant(variant)
    yield FoundPage(env.cms.renderKey(s"variant-${variant.key}")): p =>
      views.site.variant.show(p, variant, perfKey)) | notFound
