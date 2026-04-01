package lila.round

import lila.core.id.GameId
import lila.omok.{ Color, Pos }

class OmokMovePlayerTest extends munit.FunSuite:


  test("preview builds the next omok state and move event payload"):
    val gameId = GameId("abcdefgh")

    val preview = OmokMovePlayer
      .preview(gameId, OmokRoundState.initial(), Pos.unsafe(7, 7))
      .toOption
      .get

    assertEquals(preview.move.pos.key, "H8")
    assertEquals(preview.state.position.ply, 1)
    assertEquals(preview.state.position.turn, Color.White)
    assertEquals(preview.state.position.lastMove.map(_.pos.key), Some("H8"))
    assertEquals(preview.state.moves.map(_.pos.key), Vector("H8"))
    assertEquals(preview.payload.move.key, "H8")
    assertEquals(preview.event.gameId, gameId)

  test("place persists state in the repo and rejects an occupied point"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("ijklmnop")
    val request = PlaceRequest(gameId, Pos.unsafe(7, 7))

    val first = player.place(request).toOption.get

    assertEquals(first.previous.position.ply, 0)
    assertEquals(first.state.position.ply, 1)
    assertEquals(repo.get(gameId).map(_.position.ply), Some(1))

    val error = player.place(request).left.toOption.get

    assertEquals(error.message, s"[omok] $gameId cannot place H8: occupied")

  test("place rejects a mismatched expected turn without mutating cached state"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("qrstuvwx")
    val first = player.place(PlaceRequest(gameId, Pos.unsafe(7, 7))).toOption.get
    val stateBeforeRejected = repo.get(gameId).get

    val rejected = player
      .place(PlaceRequest(gameId, Pos.unsafe(7, 8), expectedTurn = Some(Color.Black)))
      .left
      .toOption
      .get
    val stateAfterRejected = repo.get(gameId).get

    assertEquals(rejected.message, s"[omok] $gameId cannot place I8: wrong turn")
    assertEquals(rejected.state, first.state)
    assertEquals(stateAfterRejected, stateBeforeRejected)

  test("place keeps the cached state usable for the next valid move after a wrong-turn rejection"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("turnflow1")
    val first = player.place(PlaceRequest(gameId, Pos.unsafe(7, 7))).toOption.get

    val rejected = player
      .place(PlaceRequest(gameId, Pos.unsafe(7, 8), expectedTurn = Some(Color.Black)))
      .left
      .toOption
      .get
    val stateAfterRejected = repo.get(gameId).get

    val accepted = player
      .place(PlaceRequest(gameId, Pos.unsafe(7, 8), expectedTurn = Some(Color.White)))
      .toOption
      .get

    assertEquals(rejected.message, s"[omok] $gameId cannot place I8: wrong turn")
    assertEquals(rejected.state, first.state)
    assertEquals(stateAfterRejected, first.state)
    assertEquals(accepted.previous, first.state)
    assertEquals(accepted.state.position.ply, 2)
    assertEquals(accepted.state.position.turn, Color.Black)
    assertEquals(accepted.state.position.lastMove.map(_.pos.key), Some("I8"))
    assertEquals(accepted.state.moves.map(_.pos.key), Vector("H8", "I8"))
    assertEquals(repo.get(gameId), Some(accepted.state))
