package lila.round

import lila.core.id.GameId
import lila.omok.{ Color, Pos, Status }

class OmokFinishedRoundReloadTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  private val winningMoves = Vector(
    pos(7, 7),
    pos(0, 0),
    pos(7, 8),
    pos(0, 1),
    pos(7, 9),
    pos(0, 2),
    pos(7, 10),
    pos(0, 3),
    pos(7, 11)
  )

  test("finished omok state retains terminal metadata across reload and rejects extra moves"):
    val repo = OmokRoundRepo()
    val gameId = GameId("reloadwin")
    val player = OmokMovePlayer(repo)

    val finished = winningMoves.map(pos => player.place(PlaceRequest(gameId, pos)).toOption.get).last
    val retained = repo.get(gameId).get
    val reloadedPlayer = OmokMovePlayer(repo)

    val rejected = reloadedPlayer.place(PlaceRequest(gameId, pos(0, 4))).left.toOption.get

    assertEquals(finished.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals(retained, finished.state)
    assertEquals(reloadedPlayer.get(gameId), Some(retained))
    assertEquals(retained.position.ply, winningMoves.size)
    assertEquals(retained.position.lastMove.map(_.pos.key), Some("L8"))
    assertEquals(
      retained.moves.map(_.pos.key),
      Vector("H8", "A1", "I8", "B1", "J8", "C1", "K8", "D1", "L8")
    )
    assertEquals(rejected.message, s"[omok] $gameId cannot place E1: game already over")
    assertEquals(rejected.state, retained)
    assertEquals(repo.get(gameId), Some(retained))

  test("finished omok state survives reload until cleanup clears it for a fresh restart"):
    val repo = OmokRoundRepo()
    val gameId = GameId("reloadclr")
    val player = OmokMovePlayer(repo)

    winningMoves.foreach: pos =>
      player.place(PlaceRequest(gameId, pos)).toOption.get

    val retained = repo.get(gameId).get
    val reloadedPlayer = OmokMovePlayer(repo)

    assertEquals(retained.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals(player.get(gameId), Some(retained))
    assertEquals(reloadedPlayer.get(gameId), Some(retained))
    assertEquals((retained.analyseDto.asJson \ "status").as[String], "win")
    assertEquals((retained.analyseDto.asJson \ "winner").as[String], "black")

    val removed = reloadedPlayer.remove(gameId)

    assertEquals(removed, Some(retained))
    assertEquals(repo.get(gameId), None)
    assertEquals(player.get(gameId), None)
    assertEquals(reloadedPlayer.get(gameId), None)

    val restarted = reloadedPlayer.place(PlaceRequest(gameId, pos(0, 0))).toOption.get

    assertEquals(restarted.previous.position.ply, 0)
    assertEquals(restarted.previous.moves, Vector.empty)
    assertEquals(restarted.state.position.ply, 1)
    assertEquals(restarted.state.position.lastMove.map(_.pos.key), Some("A1"))
    assertEquals(restarted.state.moves.map(_.pos.key), Vector("A1"))
