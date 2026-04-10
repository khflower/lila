package lila.round

import lila.core.id.GameId
import lila.omok.{ Color, Pos, RuleSet }

class OmokTaraguchiOpeningTest extends munit.FunSuite:

  private def pos(key: String): Pos =
    lila.omok.CoordinateNotation.parse(key).get

  private def place(player: OmokMovePlayer, gameId: GameId, key: String, seat: Color) =
    player.place(PlaceRequest(gameId, pos(key), expectedTurn = Some(seat))).toOption.get

  private def playToFourth(player: OmokMovePlayer, gameId: GameId): Unit =
    player.ensure(gameId, RuleSet.Taraguchi10)
    place(player, gameId, "G7", Color.White)
    place(player, gameId, "H7", Color.Black)
    place(player, gameId, "G8", Color.White)

  test("taraguchi starts at center and keeps early moves inside opening ranges"):
    val player = OmokMovePlayer(OmokRoundRepo())
    val gameId = GameId("tarag001")
    val initial = player.ensure(gameId, RuleSet.Taraguchi10)

    assertEquals(initial.moves.map(_.pos.key), Vector("H8"))
    assertEquals(initial.position.turn, Color.White)
    assertEquals(initial.activeSeat, Color.White)
    assert(initial.canSwap)

    assert(player.place(PlaceRequest(gameId, pos("A1"), expectedTurn = Some(Color.White))).isLeft)
    val second = place(player, gameId, "G7", Color.White)

    assertEquals(second.state.position.ply, 2)
    assertEquals(second.state.activeSeat, Color.Black)

  test("fourth-move swap forces the simple fifth-move route"):
    val player = OmokMovePlayer(OmokRoundRepo())
    val gameId = GameId("tarag002")
    playToFourth(player, gameId)

    assert(player.get(gameId).exists(_.canStartCandidates))
    val swapped = player.swapIfPresent(gameId, Color.Black).get.toOption.get.state

    assert(swapped.opening.forceSimpleFifth)
    assert(!swapped.canStartCandidates)
    assertEquals(swapped.activeSeat, Color.White)
    val fifth = place(player, gameId, "I8", Color.White)
    assertEquals(fifth.state.position.ply, 5)

  test("candidate route removes proposed fifth moves until one remains"):
    val player = OmokMovePlayer(OmokRoundRepo())
    val gameId = GameId("tarag003")
    playToFourth(player, gameId)

    val started = player.startCandidatesIfPresent(gameId, Color.Black).get.toOption.get.state
    assert(started.opening.candidateMode)

    val candidates = Vector("A1", "B1", "C1", "D1", "E1", "F1", "G1", "H1", "B2", "C2")
    candidates.foreach: key =>
      player.place(PlaceRequest(gameId, pos(key), expectedTurn = Some(Color.Black))).toOption.get

    val full = player.get(gameId).get
    assertEquals(full.opening.candidateMoves.size, 10)
    assertEquals(full.activeSeat, Color.White)

    candidates.take(9).foreach: key =>
      player.place(PlaceRequest(gameId, pos(key), expectedTurn = Some(Color.White))).toOption.get

    val finalState = player.get(gameId).get
    assertEquals(finalState.opening.candidateMoves, Vector.empty)
    assertEquals(finalState.position.ply, 5)
    assertEquals(finalState.moves.lastOption.map(_.pos.key), Some("C2"))

  test("candidate symmetry compares the completed five-stone pattern"):
    val player = OmokMovePlayer(OmokRoundRepo())
    val gameId = GameId("tarag004")
    playToFourth(player, gameId)

    player.startCandidatesIfPresent(gameId, Color.Black).get.toOption.get
    place(player, gameId, "A1", Color.Black)

    // A1 and O15 are symmetric as single points around H8, but the completed
    // five-stone openings are not symmetric once the first four stones are included.
    val oppositeCorner = player.place(PlaceRequest(gameId, pos("O15"), expectedTurn = Some(Color.Black)))
    assert(oppositeCorner.isRight)
