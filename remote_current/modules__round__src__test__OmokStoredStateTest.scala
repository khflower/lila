package lila.round

import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ Await, Future, Promise }

import reactivemongo.api.bson.*

import lila.core.id.GameId
import lila.game.OmokGameSidecar
import lila.omok.{ Color, CoordinateNotation, Game as OmokGame, Move as OmokMove, Pos, Replay, RuleSet, Status }

class OmokStoredStateTest extends munit.FunSuite:

  given Executor = scala.concurrent.ExecutionContext.global

  private def move(key: String) = OmokMove(CoordinateNotation.parse(key).get)
  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)
  private def await[A](fu: Fu[A]): A = Await.result(fu, 1.second)

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

  test("stored state round-trips canonical rule set and moves"):
    val moves = Vector("H8", "A1", "I8").map(move)
    val game = Replay(OmokGame.initial(RuleSet.Freestyle), moves).toOption.get
    val state = OmokRoundState.fromGame(game, moves)
    val stored = OmokStoredState.fromState(GameId("demo1234"), state)

    assertEquals(stored._id, GameId("demo1234"))
    assertEquals(stored.ruleSet, "freestyle")
    assertEquals(stored.moves, Vector("H8", "A1", "I8"))

    val hydrated = OmokStoredState.toRoundState(stored).toOption.get
    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(hydrated.position.ply, 3)

  test("stored state rejects invalid rule sets and coordinates"):
    assertEquals(
      OmokStoredState.toRoundState(OmokGameSidecar(GameId("demo1234"), "bad-rules", Vector.empty)),
      Left("invalid stored omok rule set 'bad-rules'")
    )
    assertEquals(
      OmokStoredState.toRoundState(OmokGameSidecar(GameId("demo1234"), "renju", Vector("Z99"))),
      Left("invalid stored omok move 'Z99'")
    )

  test("stored state trims move coordinates before hydrating"):
    val stored = OmokGameSidecar(
      GameId("demo1234"),
      " freestyle ",
      Vector(" H8 ", "A1 ", " I8")
    )

    val hydrated = OmokStoredState.toRoundState(stored).toOption.get

    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))

  test("stored state renders the embedded BSON payload used for durable game sidecars"):
    val stored = OmokGameSidecar(GameId("demo1234"), "freestyle", Vector("H8", "A1", "I8"))
    val doc = OmokStoredState.toBdoc(stored)

    assertEquals(doc.getAsOpt[String](OmokStoredState.BsonFields.ruleSet), Some("freestyle"))
    assertEquals(doc.getAsOpt[List[String]](OmokStoredState.BsonFields.moves), Some(List("H8", "A1", "I8")))

  test("stored state rejects blank durable move fragments instead of dropping them"):
    val stored = OmokGameSidecar(
      GameId("demo1234"),
      "freestyle",
      Vector("H8", " ", "A1")
    )

    assertEquals(
      OmokStoredState.toRoundState(stored),
      Left("invalid stored omok move at index 2: blank")
    )

  test("stored state rejects illegal move sequences"):
    val stored = OmokGameSidecar(GameId("demo1234"), "renju", Vector("H8", "H8"))
    val result = OmokStoredState.toRoundState(stored)

    assert(result.isLeft)
    assert(result.left.exists(_.contains("invalid stored omok sequence at move 2 H8")))

  test("stored state round-trips terminal omok state through repo helpers"):
    val sourceRepo = OmokRoundRepo()
    val sourceGameId = GameId("source01")
    val player = OmokMovePlayer(sourceRepo)

    winningMoves.foreach: move =>
      player.place(PlaceRequest(sourceGameId, move)).toOption.get

    val original = sourceRepo.get(sourceGameId).get
    val stored = sourceRepo.getStored(sourceGameId).get
    val restoredRepo = OmokRoundRepo()
    val restoredGameId = GameId("restore1")

    val restored = restoredRepo.putStored(restoredGameId, stored).toOption.get

    assertEquals(stored.ruleSet, "renju")
    assertEquals(stored.moves, Vector("H8", "A1", "I8", "B1", "J8", "C1", "K8", "D1", "L8"))
    assertEquals(restored, original)
    assertEquals(restored.terminalStatus, Some(Status.Win(Color.Black)))
    assertEquals(restoredRepo.get(restoredGameId), Some(original))
    assertEquals(restoredRepo.getStored(restoredGameId), Some(stored.copy(_id = restoredGameId)))


  test("getOrHydrate restores cold cache from stored state and normalizes the target game id"):
    val repo = OmokRoundRepo()
    val gameId = GameId("cold1234")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = "freestyle",
      moves = Vector("H8", "A1", "I8")
    )

    val hydrated = repo.getOrHydrate(gameId)(Some(stored)).toOption.flatten.get

    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(repo.get(gameId), Some(hydrated))
    assertEquals(repo.getStored(gameId), Some(stored.copy(_id = gameId)))

  test("getOrHydrate stores canonical move coordinates after trimming durable input"):
    val repo = OmokRoundRepo()
    val gameId = GameId("trimmed01")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = " freestyle ",
      moves = Vector(" H8 ", "A1 ", " I8")
    )

    val hydrated = repo.getOrHydrate(gameId)(Some(stored)).toOption.flatten.get

    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(
      repo.getStored(gameId),
      Some(OmokGameSidecar(gameId, "freestyle", Vector("H8", "A1", "I8")))
    )

  test("getOrHydrate rejects blank durable move fragments without caching them"):
    val repo = OmokRoundRepo()
    val gameId = GameId("blank001")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = "freestyle",
      moves = Vector("H8", "", "A1")
    )

    val result = repo.getOrHydrate(gameId)(Some(stored))

    assertEquals(result, Left("invalid stored omok move at index 2: blank"))
    assertEquals(repo.get(gameId), None)
    assertEquals(repo.getStored(gameId), None)

  test("getOrHydrate returns cached state without touching the stored fallback"):
    val repo = OmokRoundRepo()
    val gameId = GameId("cache123")
    val cached = OmokRoundState.initial(RuleSet.Freestyle)
    var fetches = 0

    repo.put(gameId, cached)

    val result = repo.getOrHydrate(gameId):
      fetches += 1
      Some(OmokGameSidecar(gameId, "renju", Vector("H8")))

    assertEquals(result, Right(Some(cached)))
    assertEquals(fetches, 0)
    assertEquals(repo.get(gameId), Some(cached))

  test("getOrHydrateAsync coalesces concurrent cold-cache hydrations for the same game"):
    val repo = OmokRoundRepo()
    val gameId = GameId("async123")
    val sourceId = GameId("source01")
    val gate = Promise[Option[OmokGameSidecar]]()
    var fetches = 0

    val first = repo.getOrHydrateAsync(gameId):
      fetches += 1
      gate.future

    val second = repo.getOrHydrateAsync(gameId):
      fetches += 1
      fuccess(Some(OmokGameSidecar(GameId("other001"), "renju", Vector("A1"))))

    assertEquals(fetches, 1)

    gate.success(Some(OmokGameSidecar(sourceId, "freestyle", Vector("H8", "A1", "I8"))))

    val firstResult = await(first)
    val secondResult = await(second)
    val hydrated = firstResult.toOption.flatten.get

    assertEquals(secondResult, firstResult)
    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(repo.get(gameId), Some(hydrated))
    assertEquals(repo.get(sourceId), None)

  test("getOrHydrateAsync starts a single durable lookup under real concurrent contention"):
    val executor = scala.concurrent.ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
    given Executor = executor

    try
      val repo = OmokRoundRepo()
      val gameId = GameId("race1234")
      val callers = 8
      val ready = new CountDownLatch(callers)
      val requested = new CountDownLatch(callers)
      val start = new CountDownLatch(1)
      val loaderStarted = new CountDownLatch(1)
      val gate = Promise[Option[OmokGameSidecar]]()
      val fetches = new AtomicInteger(0)

      val requests = Vector.fill(callers):
        Future {
          ready.countDown()
          start.await(1, TimeUnit.SECONDS)
          val result = repo.getOrHydrateAsync(gameId):
            fetches.incrementAndGet()
            loaderStarted.countDown()
            gate.future
          requested.countDown()
          result
        }.flatMap(identity)

      assert(ready.await(1, TimeUnit.SECONDS))
      start.countDown()
      assert(loaderStarted.await(1, TimeUnit.SECONDS))
      assert(requested.await(1, TimeUnit.SECONDS))
      assertEquals(fetches.get(), 1)

      gate.success(Some(OmokGameSidecar(GameId("source01"), "freestyle", Vector("H8", "A1", "I8"))))

      val results = await(Future.sequence(requests))
      val hydrated = results.head.toOption.flatten.get

      assert(results.forall(_ == Right(Some(hydrated))))
      assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
      assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
      assertEquals(repo.get(gameId), Some(hydrated))
    finally
      executor.shutdownNow()

  test("getOrHydrateAsync keeps a cleared cache empty when an in-flight hydrate finishes late"):
    val repo = OmokRoundRepo()
    val gameId = GameId("cleared01")
    val gate = Promise[Option[OmokGameSidecar]]()
    var fetches = 0

    val pending = repo.getOrHydrateAsync(gameId):
      fetches += 1
      gate.future

    assertEquals(fetches, 1)
    assertEquals(repo.remove(gameId), None)

    gate.success(Some(OmokGameSidecar(GameId("source01"), "freestyle", Vector("H8"))))

    assertEquals(await(pending), Right(None))
    assertEquals(repo.get(gameId), None)

    val retried = await:
      repo.getOrHydrateAsync(gameId):
        fetches += 1
        fuccess(Some(OmokGameSidecar(GameId("source01"), "freestyle", Vector("H8"))))

    assertEquals(fetches, 2)
    assertEquals(retried.toOption.flatten.map(_.moves.map(_.pos.key)), Some(Vector("H8")))
    assertEquals(repo.get(gameId).map(_.moves.map(_.pos.key)), Some(Vector("H8")))

  test("getOrHydrateAsync starts a fresh hydrate immediately after clear instead of waiting for the cancelled lookup"):
    val repo = OmokRoundRepo()
    val gameId = GameId("retrycl01")
    val firstGate = Promise[Option[OmokGameSidecar]]()
    val secondGate = Promise[Option[OmokGameSidecar]]()
    var fetches = 0

    val stale = repo.getOrHydrateAsync(gameId):
      fetches += 1
      firstGate.future

    assertEquals(fetches, 1)
    assertEquals(repo.remove(gameId), None)

    val retried = repo.getOrHydrateAsync(gameId):
      fetches += 1
      secondGate.future

    assertEquals(fetches, 2)

    secondGate.success(Some(OmokGameSidecar(GameId("source02"), "freestyle", Vector("A1"))))
    val retriedResult = await(retried)
    val hydrated = retriedResult.toOption.flatten.get

    firstGate.success(Some(OmokGameSidecar(GameId("source01"), "renju", Vector("H8"))))
    val staleResult = await(stale)

    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("A1"))
    assertEquals(retriedResult, Right(Some(hydrated)))
    assertEquals(staleResult, Right(Some(hydrated)))
    assertEquals(repo.get(gameId), Some(hydrated))

  test("getOrHydrateAsync ignores a cancelled lookup failure once a fresh hydrate has repopulated the cache"):
    val repo = OmokRoundRepo()
    val gameId = GameId("retryfl01")
    val firstGate = Promise[Option[OmokGameSidecar]]()
    val secondGate = Promise[Option[OmokGameSidecar]]()
    var fetches = 0

    val stale = repo.getOrHydrateAsync(gameId):
      fetches += 1
      firstGate.future

    assertEquals(fetches, 1)
    assertEquals(repo.remove(gameId), None)

    val retried = repo.getOrHydrateAsync(gameId):
      fetches += 1
      secondGate.future

    assertEquals(fetches, 2)

    secondGate.success(Some(OmokGameSidecar(GameId("source02"), "freestyle", Vector("A1"))))
    val retriedResult = await(retried)
    val hydrated = retriedResult.toOption.flatten.get

    firstGate.failure(RuntimeException("boom"))
    val staleResult = await(stale)

    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("A1"))
    assertEquals(retriedResult, Right(Some(hydrated)))
    assertEquals(staleResult, Right(Some(hydrated)))
    assertEquals(repo.get(gameId), Some(hydrated))

  test("getOrHydrateAsync turns loader failures into retryable hydration errors"):
    val repo = OmokRoundRepo()
    val gameId = GameId("failed01")
    var fetches = 0

    val failed = await:
      repo.getOrHydrateAsync(gameId):
        fetches += 1
        Future.failed(RuntimeException("boom"))

    val retried = await:
      repo.getOrHydrateAsync(gameId):
        fetches += 1
        fuccess(Some(OmokGameSidecar(GameId("source01"), "freestyle", Vector("H8"))))

    assertEquals(failed, Left("failed to load stored omok for failed01: boom"))
    assertEquals(fetches, 2)
    assertEquals(retried.toOption.flatten.map(_.moves.map(_.pos.key)), Some(Vector("H8")))
    assertEquals(repo.get(gameId).map(_.moves.map(_.pos.key)), Some(Vector("H8")))

  test("getOrHydrateAsync turns synchronous loader throws into retryable hydration errors"):
    val repo = OmokRoundRepo()
    val gameId = GameId("failed02")
    var fetches = 0

    val failed = await:
      repo.getOrHydrateAsync(gameId):
        fetches += 1
        throw RuntimeException("sync boom")

    val retried = await:
      repo.getOrHydrateAsync(gameId):
        fetches += 1
        fuccess(Some(OmokGameSidecar(GameId("source01"), "freestyle", Vector("H8"))))

    assertEquals(failed, Left("failed to load stored omok for failed02: sync boom"))
    assertEquals(fetches, 2)
    assertEquals(retried.toOption.flatten.map(_.moves.map(_.pos.key)), Some(Vector("H8")))
    assertEquals(repo.get(gameId).map(_.moves.map(_.pos.key)), Some(Vector("H8")))

  test("putStored rejects invalid durable state without mutating the repo"):
    val repo = OmokRoundRepo()
    val gameId = GameId("invalid01")
    val stored = OmokGameSidecar(
      _id = gameId,
      ruleSet = "freestyle",
      moves = Vector("H8", "H8")
    )

    val result = repo.putStored(stored)

    assertEquals(result, Left("invalid stored omok sequence at move 2 H8: occupied"))
    assertEquals(repo.get(gameId), None)
    assertEquals(repo.getStored(gameId), None)

  test("putStored warms the cache without echoing the hydrated sidecar through the saver seam"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val repo = OmokRoundRepo(writes += _)
    val gameId = GameId("cacheput1")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = "freestyle",
      moves = Vector("H8", "A1", "I8")
    )

    val hydrated = repo.putStored(gameId, stored).toOption.get

    assertEquals(writes.toVector, Vector.empty)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(
      repo.getStored(gameId),
      Some(OmokGameSidecar(gameId, "freestyle", Vector("H8", "A1", "I8")))
    )

  test("getOrHydrate repairs trim-only stored sidecar fragments through the saver seam"):
    val writes = ArrayBuffer.empty[OmokGameSidecar]
    val repo = OmokRoundRepo(writes += _)
    val gameId = GameId("repair01")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = " freestyle ",
      moves = Vector(" H8 ", "A1 ", " I8")
    )

    val hydrated = repo.getOrHydrate(gameId)(Some(stored)).toOption.flatten.get

    assertEquals(
      writes.toVector,
      Vector(OmokGameSidecar(gameId, "freestyle", Vector("H8", "A1", "I8")))
    )
    assertEquals(hydrated.ruleSet, RuleSet.Freestyle)
    assertEquals(hydrated.moves.map(_.pos.key), Vector("H8", "A1", "I8"))
    assertEquals(
      repo.getStored(gameId),
      Some(OmokGameSidecar(gameId, "freestyle", Vector("H8", "A1", "I8")))
    )

  test("getOrHydrate rejects invalid stored state without caching it"):
    val repo = OmokRoundRepo()
    val gameId = GameId("invalid02")
    val stored = OmokGameSidecar(
      _id = GameId("source01"),
      ruleSet = "freestyle",
      moves = Vector("H8", "H8")
    )

    val result = repo.getOrHydrate(gameId)(Some(stored))

    assertEquals(result, Left("invalid stored omok sequence at move 2 H8: occupied"))
    assertEquals(repo.get(gameId), None)
    assertEquals(repo.getStored(gameId), None)
