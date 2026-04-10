package controllers

import play.api.mvc.Result

import lila.app.{ *, given }

final class Coordinate(env: Env) extends LilaController(env):

  private def practiceHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.practiceHub(detail))

  def home = Open(serveHome)
  def homeLang = LangPage(routes.Coordinate.home)(serveHome)

  private def serveHome(using ctx: Context): Fu[Result] =
    practiceHub("training/coordinate")

  def score = AuthBody { ctx ?=> me ?=>
    bindForm(env.coordinate.forms.score)(
      _ => fuccess(BadRequest),
      data => env.coordinate.api.addScore(data.mode, data.color, data.score).inject(Ok(()))
    )
  }
