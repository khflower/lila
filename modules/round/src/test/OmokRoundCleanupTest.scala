package lila.round

import chess.{ ByColor, Rated }

import lila.common.Bus
import lila.core.game.{ FinishGame, Player, Source, newGame }
import lila.core.id.GameId
import lila.core.id.GamePlayerId
import lila.core.perf.UserWithPerfs
import lila.core.round.DeleteUnplayed
import lila.omok.{ Color, Pos, Status }

class OmokRoundCleanupTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)
  private def makeGame(gameId: GameId) =
    newGame(
      chess.Game(chess.variant.Standard),
      ByColor(Player(GamePlayerId("abcd"), _, none)),
      rated = Rated.No,
      source = Source.Api,
      pgnImport = none
    ).withId(gameId)

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

  test("remove clears cached finished omok state so the same game can restart from a fresh position"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("cleanup1")

    val finishedState = winningMoves.map(pos => player.place(PlaceRequest(gameId, pos)).toOption.get).last

    val removed = player.remove(gameId)

    assertEquals(finishedState.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals(removed, Some(finishedState.state))
    assertEquals(repo.get(gameId), None)
    assertEquals(player.get(gameId), None)
    assertEquals(player.remove(gameId), None)

    val acceptedAfterCleanup = player.place(PlaceRequest(gameId, pos(0, 0))).toOption.get

    assertEquals(acceptedAfterCleanup.previous.position.ply, 0)
    assertEquals(acceptedAfterCleanup.previous.moves, Vector.empty)
    assertEquals(acceptedAfterCleanup.state.position.ply, 1)
    assertEquals(acceptedAfterCleanup.state.position.lastMove.map(_.pos.key), Some("A1"))
    assertEquals(acceptedAfterCleanup.state.moves.map(_.pos.key), Vector("A1"))

  test("finish game cleanup removes cached omok state through the lifecycle owner"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val cleanup = OmokRoundLifecycleCleanup(player)
    val gameId = GameId("finishcl")

    try
      winningMoves.foreach: move =>
        player.place(PlaceRequest(gameId, move)).toOption.get

      assert(repo.get(gameId).isDefined)

      Bus.pub(FinishGame(makeGame(gameId), ByColor(_ => none[UserWithPerfs])))

      assertEquals(repo.get(gameId), None)
    finally cleanup.unsubscribe()

  test("delete-unplayed cleanup remains safe after terminal cleanup already removed state"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val cleanup = OmokRoundLifecycleCleanup(player)
    val gameId = GameId("deletecl")

    try
      player.place(PlaceRequest(gameId, pos(7, 7))).toOption.get

      Bus.pub(FinishGame(makeGame(gameId), ByColor(_ => none[UserWithPerfs])))
      Bus.pub(DeleteUnplayed(gameId))

      assertEquals(repo.get(gameId), None)
      assertEquals(player.remove(gameId), None)
    finally cleanup.unsubscribe()
