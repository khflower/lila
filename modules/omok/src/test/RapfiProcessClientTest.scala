package lila.omok

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.collection.mutable.ArrayBuffer

class RapfiProcessClientTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  private final class FakeHandle(responses: List[String]) extends RapfiProcessHandle:
    val writes = ArrayBuffer.empty[String]
    private var remaining = responses
    var closed = false

    def writeLine(line: String) =
      writes += line
      ()

    def readLine() =
      remaining match
        case head :: tail =>
          remaining = tail
          Some(head)
        case Nil => None

    def close() =
      closed = true
      ()

  test("best-move parser accepts raw and prefixed Rapfi output"):
    assertEquals(RapfiParsers.parseBestMove("7,7"), Right(Move(pos(7, 7))))
    assertEquals(RapfiParsers.parseBestMove("bestmove 8,7"), Right(Move(pos(7, 8))))
    assert(RapfiParsers.parseBestMove("oops").isLeft)

  test("client emits handshake and move batch before parsing response"):
    var handleRef: FakeHandle = null
    val factory = new RapfiProcessFactory:
      def start(command: List[String]) =
        handleRef = new FakeHandle(List("7,7"))
        handleRef

    val client = RapfiProcessClient(factory, RapfiProcessConfig(List("rapfi")))
    val request = EngineMoveRequest(PositionSnapshot.fromGame(Game.initial()))
    val result = Await.result(client.bestMove(request), 2.seconds)

    assertEquals(result.bestMove, Move(pos(7, 7)))
    assertEquals(handleRef.writes.take(3).toVector, Vector("START 15", "INFO rule 0", "BEGIN"))
    assert(handleRef.closed)

  test("client full-syncs non-empty positions with BOARD on a fresh process"):
    var handleRef: FakeHandle = null
    val factory = new RapfiProcessFactory:
      def start(command: List[String]) =
        handleRef = new FakeHandle(List("8,7"))
        handleRef

    val firstMove = Move(pos(7, 7))
    val game = Game.initial().play(firstMove).toOption.get
    val client = RapfiProcessClient(factory, RapfiProcessConfig(List("rapfi")))
    val request = EngineMoveRequest(PositionSnapshot.fromGame(game, Vector(firstMove)))
    val result = Await.result(client.bestMove(request), 2.seconds)

    assertEquals(result.bestMove, Move(pos(7, 8)))
    assertEquals(
      handleRef.writes.toVector,
      Vector("START 15", "INFO rule 0", "BOARD", "7,7,1", "DONE")
    )
    assert(handleRef.closed)

  test("analysis scaffold emits board payload and returns empty normalized analysis"):
    var handleRef: FakeHandle = null
    val factory = new RapfiProcessFactory:
      def start(command: List[String]) =
        handleRef = new FakeHandle(Nil)
        handleRef

    val moves = Vector(Move(pos(7, 7)), Move(pos(7, 8)))
    val game = Replay(Game.initial(), moves).toOption.get
    val client = RapfiProcessClient(factory, RapfiProcessConfig(List("rapfi")))
    val result = Await.result(client.analyse(EngineAnalysisRequest(PositionSnapshot.fromGame(game, moves))), 2.seconds)

    assertEquals(result.variations, Vector.empty)
    assertEquals(result.metadata.get("status"), Some("phase1-scaffold"))
    assert(handleRef.writes.contains("BOARD"))
    assertEquals(handleRef.writes.last, "DONE")
    assert(handleRef.closed)
