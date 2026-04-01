package lila.round

import lila.core.id.GameId
import lila.omok.Pos

class OmokRoundCleanupTest extends munit.FunSuite:

  test("remove clears cached omok state so the same game can restart from a fresh position"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("cleanup1")

    player.place(PlaceRequest(gameId, Pos.unsafe(7, 7))).toOption.get
    val acceptedBeforeCleanup = player.place(PlaceRequest(gameId, Pos.unsafe(7, 8))).toOption.get

    val removed = player.remove(gameId)

    assertEquals(removed, Some(acceptedBeforeCleanup.state))
    assertEquals(repo.get(gameId), None)
    assertEquals(player.get(gameId), None)
    assertEquals(player.remove(gameId), None)

    val acceptedAfterCleanup = player.place(PlaceRequest(gameId, Pos.unsafe(0, 0))).toOption.get

    assertEquals(acceptedAfterCleanup.previous.position.ply, 0)
    assertEquals(acceptedAfterCleanup.previous.moves, Vector.empty)
    assertEquals(acceptedAfterCleanup.state.position.ply, 1)
    assertEquals(acceptedAfterCleanup.state.moves.map(_.pos.key), Vector("A1"))
