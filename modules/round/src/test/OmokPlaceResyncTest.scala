package lila.round

import lila.core.id.GameId
import lila.omok.{ OmokPositionDto, Pos }

class OmokPlaceResyncTest extends munit.FunSuite:

  test("rejecting a duplicate place keeps the cached omok state stable for resync"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("qrstuvwx")
    val request = PlaceRequest(gameId, Pos.unsafe(7, 7))

    val accepted = player.place(request).toOption.get
    val stateAfterAccepted = repo.get(gameId).get

    val rejected = player.place(request).left.toOption.get
    val stateAfterRejected = repo.get(gameId).get

    assertEquals(rejected.message, s"[omok] $gameId cannot place H8: occupied")
    assertEquals(rejected.state, accepted.state)
    assertEquals(stateAfterRejected, stateAfterAccepted)
    assertEquals(OmokPositionDto.fromPosition(stateAfterRejected.position), accepted.payload.position)
    assertEquals(stateAfterRejected.moves.map(_.pos.key), Vector("H8"))
