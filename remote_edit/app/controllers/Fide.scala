package controllers

import play.api.mvc.*

import lila.app.{ *, given }
import lila.core.fide.FidePlayerOrder
import lila.fide.{ FidePlayer, Federation }

final class Fide(env: Env) extends LilaController(env):

  import env.fide.json.given
  private def playerUrl(player: FidePlayer) = routes.Fide.show(player.id, player.slug)
  private def competitiveHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.competitiveHub(detail))

  def index(page: Int, q: Option[String] = None) = Open:
    competitiveHub(List("fide", (~q).trim, page.toString).filter(_.nonEmpty).mkString("/"))

  def show(id: chess.FideId, slug: String, page: Int) = Open:
    competitiveHub(s"fide/$id/$slug/$page")

  def follow(fideId: chess.FideId, follow: Boolean) = AuthOrScopedBody(_.Web.Mobile): _ ?=>
    me ?=>
      val f = if follow then env.fide.repo.follower.follow else env.fide.repo.follower.unfollow
      for _ <- f(me.userId, fideId) yield NoContent

  def apiShow(id: chess.FideId) = Anon:
    WithProxy:
      limit.enumeration.fidePlayer(rateLimited):
        Found(env.fide.playerApi.withFollow(id))(JsonOk)

  def apiRatings(id: chess.FideId) = Anon:
    WithProxy:
      limit.enumeration.fidePlayer(rateLimited):
        JsonOk(env.fide.playerApi.getRatings(id).map(_.toJson))

  def apiSearch(q: String) = Anon:
    env.fide.search(q.some, 1, FidePlayerOrder.default).map(_.fold(Seq(_), _.currentPageResults)).map(JsonOk)

  def federations(page: Int) = Open:
    competitiveHub(s"fide/federation/$page")

  def federation(slug: String, page: Int) = Open:
    competitiveHub(s"fide/federation/$slug/$page")

  def playerPhoto(id: chess.FideId) = SecureBody(lila.web.HashedMultiPart(parse))(_.FidePlayer) {
    ctx ?=> _ ?=>
      Found(env.fide.repo.player.fetch(id)): p =>
        ctx.body.body.file("photo") match
          case Some(photo) =>
            for
              pic <- env.fide.playerApi.uploadPhoto(p, photo)
              picUrl = FidePlayer.PlayerPhoto(env.memo.picfitUrl, pic.id, _.Small)
              _ <- env.irc.api.fidePhoto(playerUrl(p).url, picUrl)
            yield Redirect(routes.Coach.edit)
          case None => Redirect(playerUrl(p))
  }

  def playerUpdate(id: chess.FideId) = SecureBody(_.FidePlayer) { ctx ?=> _ ?=>
    Found(env.fide.repo.player.fetch(id)): p =>
      bindForm(FidePlayer.form.credit(p))(
        _ => funit,
        credit =>
          for
            _ <- env.fide.playerApi.setPhotoCredit(p, credit)
            _ <- env.irc.api.fidePhotoCredits(playerUrl(p).url, credit | "-")
          yield ()
      ).inject(Redirect(playerUrl(p)))
  }
