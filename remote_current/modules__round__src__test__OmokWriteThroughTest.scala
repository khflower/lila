package lila.round

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await

import lila.core.id.{ GameFullId, GameId }
import lila.game.OmokGameSidecar
import lila.omok.{ Color, Pos, RuleSet }

class OmokWriteThroughTest extends munit.FunSuite:

  private given Executor = scala.concurrent.ExecutionContext.global

  private def await[A](fa: Fu[A]): A = Await.result(fa, 1.second)

  test("start writes the blank scaffold snapshot through the stored sidecar seam"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val repo = OmokRoundRepo(writes += _)
    val fullId = GameFullId("demo1234abcd")
    val scaffolded = OmokStartScaffold(repo, full => fuccess(full == fullId))

    await(scaffolded.start(fullId, RuleSet.Freestyle)).toOption.get

    assertEquals(
      writes.toVector,
      Vector(OmokGameSidecar(GameId("demo1234"), "freestyle", Vector.empty))
    )

  test("ensure writes the first blank round snapshot through the stored sidecar seam"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val repo = OmokRoundRepo(writes += _)
    val player = OmokMovePlayer(repo)
    val gameId = GameId("ensure01")

    val ensured = player.ensure(gameId, RuleSet.Freestyle)

    assertEquals(ensured.ruleSet, RuleSet.Freestyle)
    assertEquals(ensured.moves, Vector.empty)
    assertEquals(
      writes.toVector,
      Vector(OmokGameSidecar(gameId, "freestyle", Vector.empty))
    )
    assertEquals(repo.get(gameId), Some(ensured))

  test("ensure does not rewrite the sidecar when the round is already cached"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val repo = OmokRoundRepo(writes += _)
    val player = OmokMovePlayer(repo)
    val gameId = GameId("ensure02")

    val first = player.ensure(gameId)
    val second = player.ensure(gameId, RuleSet.Freestyle)

    assertEquals(second, first)
    assertEquals(
      writes.toVector,
      Vector(OmokGameSidecar(gameId, "renju", Vector.empty))
    )

  test("place writes accepted positions through the stored sidecar seam"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val repo = OmokRoundRepo(writes += _)
    val player = OmokMovePlayer(repo)
    val gameId = GameId("persist01")

    player.place(PlaceRequest(gameId, Pos.unsafe(7, 7))).toOption.get
    player.place(PlaceRequest(gameId, Pos.unsafe(7, 8), expectedTurn = Some(Color.White))).toOption.get

    val rejected = player.place(PlaceRequest(gameId, Pos.unsafe(7, 8)))
    assert(rejected.isLeft)

    assertEquals(
      writes.toVector,
      Vector(
        OmokGameSidecar(gameId, "renju", Vector("H8")),
        OmokGameSidecar(gameId, "renju", Vector("H8", "I8"))
      )
    )

  test("remove clears the stored sidecar through the durable seam"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val clears = ArrayBuffer.empty[GameId]
    val repo = OmokRoundRepo(writes += _, clears += _)
    val gameId = GameId("persist02")

    repo.put(gameId, OmokRoundState.initial())

    assertEquals(repo.remove(gameId).map(_.moves), Some(Vector.empty))
    assertEquals(writes.toVector, Vector(OmokGameSidecar(gameId, "renju", Vector.empty)))
    assertEquals(clears.toVector, Vector(gameId))
