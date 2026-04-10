package controllers

import play.api.libs.json.Json
import play.api.mvc.*

import lila.app.{ *, given }
import lila.racer.{ RacerPlayer, RacerRace }
import lila.common.Json.given

final class Racer(env: Env) extends LilaController(env):

  private def practiceHub(detail: String)(using Context): Fu[Result] =
    pageHit
    Ok.page(views.omokPages.practiceHub(detail))

  def home = Open(serveHome)
  def homeLang = LangPage(routes.Racer.home)(serveHome)

  private def serveHome(using Context) = NoBot:
    practiceHub("racer")

  def create = WithPlayerId { _ ?=> playerId =>
    AuthOrTrustedIp:
      env.racer.api
        .createAndJoin(playerId)
        .map: raceId =>
          Redirect(routes.Racer.show(raceId.value))
  }

  def apiCreate = Scoped(_.Racer.Write) { _ ?=> me ?=>
    me.noBot.so:
      env.racer.api
        .createAndJoin(RacerPlayer.Id.User(me))
        .map: raceId =>
          JsonOk:
            Json.obj(
              "id" -> raceId,
              "url" -> routeUrl(routes.Racer.show(raceId.value))
            )
  }

  def apiGet(id: String) = Anon:
    Found(env.racer.api.get(RacerRace.Id(id))): race =>
      JsonOk(env.racer.json.apiResults(race))

  def show(id: String) = WithPlayerId { ctx ?=> playerId =>
    practiceHub(s"racer/$id")
  }

  def rematch(id: String) = WithPlayerId { _ ?=> playerId =>
    practiceHub(s"racer/$id/rematch")
  }

  def lobby = WithPlayerId { ctx ?=> playerId =>
    AuthOrTrustedIp:
      env.racer.lobby
        .join(playerId)
        .map: raceId =>
          Redirect(routes.Racer.show(raceId.value))
  }

  private def WithPlayerId(f: Context ?=> RacerPlayer.Id => Fu[Result]) = Open:
    NoBot:
      env.security.lilaCookie.ensureAndGet(ctx.req): sid =>
        f(env.racer.api.playerId(sid, ctx.me))
