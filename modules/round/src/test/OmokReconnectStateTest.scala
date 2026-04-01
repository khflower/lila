package lila.round

import play.api.libs.json.JsObject

import lila.core.id.GameId
import lila.omok.{ OmokAnalyseDto, Pos }

class OmokReconnectStateTest extends munit.FunSuite:

  test("reconnect snapshot follows the latest cached omok state after a rejected place"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("resync42")

    player.place(PlaceRequest(gameId, Pos.unsafe(7, 7))).toOption.get
    val second = player.place(PlaceRequest(gameId, Pos.unsafe(0, 0))).toOption.get

    val rejected = player.place(PlaceRequest(gameId, Pos.unsafe(0, 0))).left.toOption.get
    val third = player.place(PlaceRequest(gameId, Pos.unsafe(7, 8))).toOption.get

    val current = repo.get(gameId).get
    val reconnectJson = OmokAnalyseDto.fromPosition(current.position).asJson

    assertEquals(rejected.message, s"[omok] $gameId cannot place A1: occupied")
    assertEquals(rejected.state, second.state)
    assertEquals(current, third.state)
    assertEquals((reconnectJson \ "position").as[JsObject], third.payload.position.asJson)
    assertEquals((reconnectJson \ "position" \ "lastMove" \ "key").as[String], "I8")
    assertEquals(
      (reconnectJson \ "position" \ "moves").as[Vector[JsObject]].map(_("key").as[String]),
      Vector("H8", "A1", "I8")
    )
