package lila.omok

import lila.core.lilaism.Core.*
import scala.concurrent.ExecutionContext.Implicits.global

trait RapfiProcessHandle:
  def isAlive: Boolean = true
  def writeLine(line: String): Unit
  def readLine(): Option[String]
  def close(): Unit

trait RapfiProcessFactory:
  def start(command: List[String]): RapfiProcessHandle

final case class RapfiProcessConfig(
    command: List[String],
    boardSize: Int = Pos.Size,
    workingDir: Option[String] = None
):
  require(command.nonEmpty, "command must be non-empty")

enum RapfiResponseBoundary:
  case BestMove

final case class RapfiCommandBatch(lines: Vector[String], responseBoundary: RapfiResponseBoundary)

object RapfiCommandBatch:
  def move(request: EngineMoveRequest): RapfiCommandBatch =
    RapfiCommandBatch(RapfiAdapter.moveRequest(request.position, request.limits), RapfiResponseBoundary.BestMove)

  def analysis(request: EngineAnalysisRequest): RapfiCommandBatch =
    RapfiCommandBatch(RapfiAdapter.analysisRequest(request.position, request.limits), RapfiResponseBoundary.BestMove)

object RapfiResponseParser:

  def parseMove(lines: Vector[String]): Either[String, EngineMoveResponse] =
    val stdout = lines.mkString("\n")
    lines.iterator
      .flatMap(line => parseBestMoveLine(line).toOption)
      .map(move => EngineMoveResponse(bestMove = move, principalVariation = Vector(move), raw = Map("rapfi.stdout" -> stdout)))
      .nextOption()
      .toRight(s"No Rapfi best move found in output: $stdout")

  private def parseBestMoveLine(line: String): Either[String, Move] =
    val trimmed = line.trim
    val coord =
      if trimmed.toLowerCase.startsWith("bestmove ") then trimmed.drop(9).trim
      else trimmed
    RapfiProtocol.parseCoord(coord).map(Move.apply).toRight(s"Invalid move line: $line")

object RapfiParsers:
  def parseBestMove(line: String): Either[EngineError.Protocol, Move] =
    RapfiResponseParser.parseMove(Vector(line)).map(_.bestMove).left.map(EngineError.Protocol.apply)

class RapfiProcessAdapter(config: RapfiProcessConfig, factory: RapfiProcessFactory) extends EngineClient:

  @volatile private var currentHandle: Option[RapfiProcessHandle] = None

  private def ensureHandle(): RapfiProcessHandle = synchronized {
    currentHandle.filter(_.isAlive).getOrElse {
      val handle = factory.start(config.command)
      RapfiAdapter.openingHandshake(config.boardSize).foreach(handle.writeLine)
      currentHandle = Some(handle)
      handle
    }
  }

  def bestMove(request: EngineMoveRequest): Fu[EngineMoveResponse] = Future {
    val handle = ensureHandle()
    val batch = RapfiCommandBatch.move(request)
    batch.lines.foreach(handle.writeLine)
    val response = collectMoveResponse(handle)
    response match
      case Right(move) => move
      case Left(err)   => throw new RuntimeException(err)
  }

  def analyse(request: EngineAnalysisRequest): Fu[EngineAnalysisResponse] = Future {
    val handle = ensureHandle()
    val batch = RapfiCommandBatch.analysis(request)
    batch.lines.foreach(handle.writeLine)
    collectMoveResponse(handle) match
      case Right(move) => EngineAnalysisResponse.fromMoveResponse(move)
      case Left(_)     => EngineAnalysisResponse.normalized(Vector.empty, Map("status" -> "phase1-scaffold"))
  }

  override def health: Fu[EngineHealth] = Future.successful {
    currentHandle match
      case Some(handle) if !handle.isAlive => EngineHealth.Degraded("rapfi-handle-dead")
      case _                               => EngineHealth.Ready
  }

  private def collectMoveResponse(handle: RapfiProcessHandle): Either[String, EngineMoveResponse] =
    val buffer = scala.collection.mutable.ArrayBuffer.empty[String]
    var parsed: Option[Either[String, EngineMoveResponse]] = None
    var keepReading = true
    while keepReading do
      handle.readLine() match
        case Some(line) =>
          buffer += line
          RapfiResponseParser.parseMove(Vector(line)) match
            case right @ Right(_) =>
              parsed = Some(right.map(_.copy(raw = Map("rapfi.stdout" -> buffer.mkString("\n")))))
              keepReading = false
            case Left(_) => ()
        case None =>
          keepReading = false
    parsed.getOrElse(Left(s"No Rapfi best move found in output: ${buffer.mkString(" | ")}"))

final class RapfiProcessClient(factory: RapfiProcessFactory, config: RapfiProcessConfig) extends EngineClient:

  def bestMove(request: EngineMoveRequest): Fu[EngineMoveResponse] = Future {
    val handle = factory.start(config.command)
    try
      RapfiAdapter.openingHandshake(config.boardSize).foreach(handle.writeLine)
      RapfiCommandBatch.move(request).lines.foreach(handle.writeLine)
      collectMoveResponse(handle) match
        case Right(move) => move
        case Left(err)   => throw new RuntimeException(err)
    finally handle.close()
  }

  def analyse(request: EngineAnalysisRequest): Fu[EngineAnalysisResponse] = Future {
    val handle = factory.start(config.command)
    try
      RapfiAdapter.openingHandshake(config.boardSize).foreach(handle.writeLine)
      RapfiCommandBatch.analysis(request).lines.foreach(handle.writeLine)
      collectMoveResponse(handle) match
        case Right(move) => EngineAnalysisResponse.fromMoveResponse(move)
        case Left(_)     => EngineAnalysisResponse.normalized(Vector.empty, Map("status" -> "phase1-scaffold"))
    finally handle.close()
  }

  private def collectMoveResponse(handle: RapfiProcessHandle): Either[String, EngineMoveResponse] =
    val buffer = scala.collection.mutable.ArrayBuffer.empty[String]
    var parsed: Option[Either[String, EngineMoveResponse]] = None
    var keepReading = true
    while keepReading do
      handle.readLine() match
        case Some(line) =>
          buffer += line
          RapfiResponseParser.parseMove(Vector(line)) match
            case right @ Right(_) =>
              parsed = Some(right.map(_.copy(raw = Map("rapfi.stdout" -> buffer.mkString("\n")))))
              keepReading = false
            case Left(_) => ()
        case None =>
          keepReading = false
    parsed.getOrElse(Left(s"No Rapfi best move found in output: ${buffer.mkString(" | ")}"))
