package lila.round

import lila.core.id.GameId
import lila.omok.{ Color, Game, Move, Pos, Replay, RuleSet, Status }

class OmokMovePlayerTest extends munit.FunSuite:

  private def move(row: Int, col: Int) = Move(Pos.unsafe(row, col))


  test("preview builds the next omok state and move event payload"):
    val gameId = GameId("abcdefgh")

    val preview = OmokMovePlayer
      .preview(gameId, OmokRoundState.initial(), Pos.unsafe(7, 8))
      .toOption
      .get

    assertEquals(preview.move.pos.key, "I8")
    assertEquals(preview.state.position.ply, 2)
    assertEquals(preview.state.position.turn, Color.Black)
    assertEquals(preview.state.position.lastMove.map(_.pos.key), Some("I8"))
    assertEquals(preview.state.moves.map(_.pos.key), Vector("H8", "I8"))
    assertEquals(preview.payload.move.key, "I8")
    assertEquals(preview.event.gameId, gameId)

  test("place persists state in the repo and rejects an occupied point"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("ijklmnop")
    val request = PlaceRequest(gameId, Pos.unsafe(7, 8))

    val first = player.place(request).toOption.get

    assertEquals(first.previous.position.ply, 1)
    assertEquals(first.state.position.ply, 2)
    assertEquals(repo.get(gameId).map(_.position.ply), Some(2))

    val error = player.place(request).left.toOption.get

    assertEquals(error.message, s"[omok] $gameId cannot place I8: occupied")

  test("placeIfPresent refuses to initialize missing omok state"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("missing42")

    val missing = player.placeIfPresent(PlaceRequest(gameId, Pos.unsafe(7, 7), expectedTurn = Some(Color.Black)))

    assertEquals(missing, None)
    assertEquals(repo.get(gameId), None)
    assertEquals(player.get(gameId), None)

  test("place rejects a mismatched expected turn without mutating cached state"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("qrstuvwx")
    val first = player.place(PlaceRequest(gameId, Pos.unsafe(7, 8))).toOption.get
    val stateBeforeRejected = repo.get(gameId).get

    val rejected = player
      .place(PlaceRequest(gameId, Pos.unsafe(8, 7), expectedTurn = Some(Color.White)))
      .left
      .toOption
      .get
    val stateAfterRejected = repo.get(gameId).get

    assertEquals(rejected.message, s"[omok] $gameId cannot place H9: wrong turn")
    assertEquals(rejected.state, first.state)
    assertEquals(stateAfterRejected, stateBeforeRejected)

  test("place keeps the cached state usable for the next valid move after a wrong-turn rejection"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("turnflow1")
    val first = player.place(PlaceRequest(gameId, Pos.unsafe(7, 8))).toOption.get

    val rejected = player
      .place(PlaceRequest(gameId, Pos.unsafe(8, 7), expectedTurn = Some(Color.White)))
      .left
      .toOption
      .get
    val stateAfterRejected = repo.get(gameId).get

    val accepted = player
      .place(PlaceRequest(gameId, Pos.unsafe(8, 7), expectedTurn = Some(Color.Black)))
      .toOption
      .get

    assertEquals(rejected.message, s"[omok] $gameId cannot place H9: wrong turn")
    assertEquals(rejected.state, first.state)
    assertEquals(stateAfterRejected, first.state)
    assertEquals(accepted.previous, first.state)
    assertEquals(accepted.state.position.ply, 3)
    assertEquals(accepted.state.position.turn, Color.White)
    assertEquals(accepted.state.position.lastMove.map(_.pos.key), Some("H9"))
    assertEquals(accepted.state.moves.map(_.pos.key), Vector("H8", "I8", "H9"))
    assertEquals(repo.get(gameId), Some(accepted.state))

  test("candidate selection keeps the proposer black and disables the final swap"):
    val gameId = GameId("candpick")
    val moves = Vector(
      move(7, 7),
      move(7, 8),
      move(8, 7),
      move(8, 8)
    )
    val game = Replay(Game.initial(RuleSet.Taraguchi10), moves).toOption.get
    val candidateA = move(6, 7)
    val candidateB = move(6, 8)
    val state = OmokRoundState.fromGame(
      game,
      moves,
      OmokOpeningState(
        candidateMode = true,
        candidateSelection = true,
        candidateMoves = Vector(candidateA, candidateB),
        history = OmokOpeningState.initialTaraguchiHistory(moves.head)
      )
    )

    val preview = OmokMovePlayer.preview(gameId, state, candidateB.pos).toOption.get

    assertEquals(preview.move.pos.key, "I7")
    assertEquals(preview.state.position.ply, 5)
    assertEquals(preview.state.position.turn, Color.White)
    assertEquals(preview.state.canSwap, false)
    assertEquals(preview.state.moves.map(_.pos.key), Vector("H8", "I8", "H9", "I9", "I7"))
    assert(preview.state.opening.history.exists(_.contains("White selected candidate I7")))
    assert(preview.state.opening.history.exists(_.contains("5. Black placed I7 (selected from candidates)")))

  test("candidate symmetry ignores move order and rejects geometric duplicates"):
    val gameId = GameId("candsym1")
    val moves = Vector(
      move(7, 7), // H8
      move(5, 5), // F6
      move(5, 7), // H6
      move(5, 9)  // J6
    )
    val game = Replay(Game.initial(RuleSet.Taraguchi10), moves).toOption.get
    val existingCandidate = move(5, 6) // G6
    val state = OmokRoundState.fromGame(
      game,
      moves,
      OmokOpeningState(
        candidateMode = true,
        candidateSelection = false,
        candidateMoves = Vector(existingCandidate),
        history = OmokOpeningState.initialTaraguchiHistory(moves.head)
      )
    )

    val error = OmokMovePlayer.preview(gameId, state, move(5, 8).pos).left.toOption.get // I6

    assertEquals(error.cause, lila.omok.MoveError.Forbidden(lila.omok.ForbiddenReason.DoubleThree))

  test("preview keeps terminal omok status for a winning move"):
    val gameId = GameId("terminal")
    val moves = Vector(
      move(7, 7),
      move(0, 0),
      move(7, 8),
      move(0, 1),
      move(7, 9),
      move(0, 2),
      move(7, 10),
      move(0, 3)
    )
    val game = Replay(Game.initial(), moves).toOption.get
    val winningState = OmokRoundState.fromGame(game, moves)

    val preview = OmokMovePlayer.preview(gameId, winningState, Pos.unsafe(7, 11)).toOption.get

    assertEquals(preview.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals(preview.payload.status, Some("win"))
    assertEquals(preview.payload.winner, Some("black"))

  test("place retains terminal omok outcome in cached state for reconnect boot data"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("bootterm")
    val moves = Vector(
      move(0, 0),
      move(7, 8),
      move(0, 1),
      move(7, 9),
      move(0, 2),
      move(7, 10),
      move(0, 3),
      move(7, 11)
    )

    moves.foreach: move =>
      player.place(PlaceRequest(gameId, move.pos)).toOption.get

    val retained = repo.get(gameId).get
    val omokJson = retained.analyseDto.asJson

    assertEquals(retained.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals((omokJson \ "status").as[String], "win")
    assertEquals((omokJson \ "winner").as[String], "black")
    assertEquals((omokJson \ "position" \ "lastMove" \ "key").as[String], "L8")
