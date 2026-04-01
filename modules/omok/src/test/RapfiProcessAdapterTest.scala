package lila.omok

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.*

class RapfiProcessAdapterTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("command batches preserve move and analysis request boundaries"):
    val initial = PositionSnapshot.fromGame(Game.initial())

    val moveBatch = RapfiCommandBatch.move(EngineMoveRequest(initial))
    assertEquals(moveBatch.lines, Vector("BEGIN"))
    assertEquals(moveBatch.responseBoundary, RapfiResponseBoundary.BestMove)

    val analysisBatch =
      RapfiCommandBatch.analysis(
        EngineAnalysisRequest(initial, EngineLimits(depth = Some(10)))
      )
    assertEquals(analysisBatch.lines.head, "INFO max_depth 10")
    assertEquals(analysisBatch.lines.drop(1).head, "BOARD")
    assertEquals(analysisBatch.lines.last, "DONE")
    assertEquals(analysisBatch.responseBoundary, RapfiResponseBoundary.BestMove)

  test("response parser extracts the first best move from raw Rapfi output"):
    val parsed =
      RapfiResponseParser.parseMove(
        Vector("MESSAGE searching", "BESTMOVE 8,7", "MESSAGE ignored")
      )

    assertEquals(parsed.map(_.bestMove), Right(Move(pos(7, 8))))
    parsed match
      case Right(response) =>
        assert(response.raw.get("rapfi.stdout").exists(_.contains("BESTMOVE 8,7")))
      case Left(error) =>
        fail(error)

  test("process adapter starts once, emits handshake, and reuses the handle"):
    val handle = new FakeHandle(
      Vector(
        "MESSAGE opening book",
        "7,7",
        "BESTMOVE 8,7"
      )
    )
    val factory = new FakeFactory(Vector(handle))
    val adapter = new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), factory)

    val initial = PositionSnapshot.fromGame(Game.initial())
    val first = Await.result(adapter.bestMove(EngineMoveRequest(initial)), 2.seconds)

    val firstMove = Move(pos(7, 7))
    val replyMove = Move(pos(7, 8))
    val afterReply = PositionSnapshot.fromGame(
      Replay(Game.initial(), Vector(firstMove, replyMove)).toOption.get,
      Vector(firstMove, replyMove)
    )
    val second = Await.result(adapter.bestMove(EngineMoveRequest(afterReply)), 2.seconds)

    assertEquals(first.bestMove, firstMove)
    assertEquals(second.bestMove, Move(pos(7, 8)))
    assertEquals(factory.starts, 1)
    assertEquals(
      handle.writes.toVector,
      Vector(
        "START 15",
        "INFO rule 0",
        "BEGIN",
        "TURN 8,7"
      )
    )

  test("process adapter closes and replaces a stale handle with a full board sync for midgame requests"):
    val failedHandle = new FakeHandle(Vector("MESSAGE thinking"))
    val recoveredHandle = new FakeHandle(Vector("BESTMOVE 9,9"))
    val factory = new FakeFactory(Vector(failedHandle, recoveredHandle))
    val adapter = new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), factory)
    val firstMove = Move(pos(7, 7))
    val secondMove = Move(pos(7, 8))
    val game = Replay(Game.initial(), Vector(firstMove, secondMove)).toOption.get
    val request = EngineMoveRequest(PositionSnapshot.fromGame(game, Vector(firstMove, secondMove)))

    intercept[RuntimeException]:
      Await.result(adapter.bestMove(request), 2.seconds)

    val recovered = Await.result(adapter.bestMove(request), 2.seconds)

    assert(failedHandle.closed)
    assertEquals(recovered.bestMove, Move(pos(9, 9)))
    assertEquals(factory.starts, 2)
    assertEquals(
      recoveredHandle.writes.toVector,
      Vector(
        "START 15",
        "INFO rule 0",
        "BOARD",
        "7,7,1",
        "8,7,2",
        "DONE"
      )
    )

  test("process adapter restarts the session when the next move request changes ruleset"):
    val firstHandle = new FakeHandle(Vector("BESTMOVE 7,7"))
    val secondHandle = new FakeHandle(Vector("BESTMOVE 9,9"))
    val factory = new FakeFactory(Vector(firstHandle, secondHandle))
    val adapter = new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), factory)

    val renjuInitial = PositionSnapshot.fromGame(Game.initial(RuleSet.Renju))
    val renjuMove = Await.result(adapter.bestMove(EngineMoveRequest(renjuInitial)), 2.seconds)
    val firstMove = renjuMove.bestMove
    val freestyleGame = Game.initial(RuleSet.Freestyle).play(firstMove).toOption.get
    val freestylePosition = PositionSnapshot.fromGame(freestyleGame, Vector(firstMove))
    val freestyleReply = Await.result(adapter.bestMove(EngineMoveRequest(freestylePosition)), 2.seconds)

    assert(firstHandle.closed)
    assertEquals(freestyleReply.bestMove, Move(pos(9, 9)))
    assertEquals(factory.starts, 2)
    assertEquals(
      secondHandle.writes.toVector,
      Vector(
        "START 15",
        "INFO rule 0",
        "BOARD",
        "7,7,1",
        "DONE"
      )
    )

  test("analysis falls back to a single primary variation from best-move output"):
    val handle = new FakeHandle(Vector("BESTMOVE 9,9"))
    val adapter =
      new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), new FakeFactory(Vector(handle)))

    val analysis =
      Await.result(adapter.analyse(EngineAnalysisRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)

    assertEquals(analysis.bestMove, Some(Move(pos(9, 9))))
    assertEquals(analysis.variations.map(_.rank), Vector(1))

  test("analysis fallback clears the cached handle so the next request restarts cleanly"):
    val failedHandle = new FakeHandle(Vector.empty)
    val recoveredHandle = new FakeHandle(Vector("BESTMOVE 9,9"))
    val factory = new FakeFactory(Vector(failedHandle, recoveredHandle))
    val adapter = new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), factory)

    val analysis =
      Await.result(adapter.analyse(EngineAnalysisRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)
    val move = Await.result(adapter.bestMove(EngineMoveRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)

    assertEquals(analysis.variations, Vector.empty)
    assertEquals(analysis.metadata.get("status"), Some("phase1-scaffold"))
    assert(failedHandle.closed)
    assertEquals(move.bestMove, Move(pos(9, 9)))
    assertEquals(factory.starts, 2)

  test("successful analysis recycles the process so the next move request starts a fresh session"):
    val analysisHandle = new FakeHandle(Vector("BESTMOVE 9,9"))
    val moveHandle = new FakeHandle(Vector("BESTMOVE 8,7"))
    val factory = new FakeFactory(Vector(analysisHandle, moveHandle))
    val adapter = new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), factory)
    val firstMove = Move(pos(7, 7))
    val game = Game.initial().play(firstMove).toOption.get
    val position = PositionSnapshot.fromGame(game, Vector(firstMove))

    val analysis = Await.result(adapter.analyse(EngineAnalysisRequest(position)), 2.seconds)
    val move = Await.result(adapter.bestMove(EngineMoveRequest(position)), 2.seconds)

    assertEquals(analysis.bestMove, Some(Move(pos(9, 9))))
    assertEquals(move.bestMove, Move(pos(7, 8)))
    assert(analysisHandle.closed)
    assertEquals(factory.starts, 2)
    assertEquals(
      analysisHandle.writes.toVector,
      Vector(
        "START 15",
        "INFO rule 0",
        "BOARD",
        "7,7,1",
        "DONE"
      )
    )
    assertEquals(
      moveHandle.writes.toVector,
      Vector(
        "START 15",
        "INFO rule 0",
        "BOARD",
        "7,7,1",
        "DONE",
      )
    )

  private final class FakeFactory(handles: Vector[FakeHandle]) extends RapfiProcessFactory:
    var starts = 0
    private val remaining = mutable.Queue.from(handles)

    def start(command: List[String]): RapfiProcessHandle =
      starts += 1
      remaining.dequeue()

  private final class FakeHandle(initialOutput: Vector[String]) extends RapfiProcessHandle:
    val writes = mutable.ArrayBuffer.empty[String]
    private val output = mutable.Queue.from(initialOutput)
    private var alive = true

    override def isAlive: Boolean = alive

    def writeLine(line: String): Unit =
      writes += line

    def readLine(): Option[String] =
      Option.when(output.nonEmpty)(output.dequeue())

    def close(): Unit =
      alive = false
    def closed: Boolean = !alive
