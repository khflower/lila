package controllers

import play.api.mvc.*

import lila.app.{ *, given }

final class Feed(env: Env) extends LilaController(env):

  def api = env.feed.api

  private def updatesHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.updatesHub(detail))

  def index(page: Int) = Open: ctx ?=>
    updatesHub(s"feed/$page")

  def createForm = Secure(_.Feed) { _ ?=> _ ?=>
    updatesHub("feed/new")
  }

  def create = SecureBody(_.Feed) { _ ?=> _ ?=>
    bindForm(api.form(none))(
      err => BadRequest.async(views.feed.create(err)),
      data =>
        val up = data.toUpdate(none)
        api.set(up).inject(Redirect(routes.Feed.edit(up.id)).flashSuccess)
    )
  }

  def edit(id: String) = Secure(_.Feed) { _ ?=> _ ?=>
    updatesHub(s"feed/$id/edit")
  }

  def update(id: String) = SecureBody(_.Feed) { _ ?=> _ ?=>
    Found(api.get(id)): from =>
      bindForm(api.form(from.some))(
        err => BadRequest.async(views.feed.edit(err, from)),
        data => api.set(data.toUpdate(from.id.some)).inject(Redirect(routes.Feed.edit(from.id)).flashSuccess)
      )
  }

  def delete(id: String) = Secure(_.Feed) { _ ?=> _ ?=>
    Found(api.get(id)): up =>
      api.delete(up.id).inject(Redirect(routes.Feed.index(1)).flashSuccess)
  }

  def atom = Anon:
    updatesHub("feed.atom")
