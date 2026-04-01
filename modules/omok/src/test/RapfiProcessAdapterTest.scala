package lila.omok

import scala.collection.mutable
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration.*

class RapfiProcessAdapterTest extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.global

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
    val factory = new FakeFactory(handle)
    val adapter = new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), factory)

    val initial = PositionSnapshot.fromGame(Game.initial())
    val first = Await.result(adapter.bestMove(EngineMoveRequest(initial)), 2.seconds)

    val firstMove = Move(pos(7, 7))
    val afterOne = PositionSnapshot.fromGame(
      Game.initial().play(firstMove).toOption.get,
      Vector(firstMove)
    )
    val second = Await.result(adapter.bestMove(EngineMoveRequest(afterOne)), 2.seconds)

    assertEquals(first.bestMove, firstMove)
    assertEquals(second.bestMove, Move(pos(7, 8)))
    assertEquals(factory.starts, 1)
    assertEquals(
      handle.writes.toVector,
      Vector(
        "START 15",
        "INFO rule 0",
        "BEGIN",
        "TURN 7,7"
      )
    )

  test("analysis falls back to a single primary variation from best-move output"):
    val handle = new FakeHandle(Vector("BESTMOVE 9,9"))
    val adapter =
      new RapfiProcessAdapter(RapfiProcessConfig(command = List("rapfi")), new FakeFactory(handle))

    val analysis =
      Await.result(adapter.analyse(EngineAnalysisRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)

    assertEquals(analysis.bestMove, Some(Move(pos(9, 9))))
    assertEquals(analysis.variations.map(_.rank), Vector(1))

  private final class FakeFactory(handle: FakeHandle) extends RapfiProcessFactory:
    var starts = 0

    def start(command: List[String]): RapfiProcessHandle =
      starts += 1
      handle

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
