package lila.omok

import lila.core.lilaism.Core.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

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

private final case class RapfiSessionState(ruleSet: RuleSet, syncedMoves: Vector[Move])

class RapfiProcessAdapter(config: RapfiProcessConfig, factory: RapfiProcessFactory) extends EngineClient:

  @volatile private var currentHandle: Option[RapfiProcessHandle] = None
  @volatile private var currentSession: Option[RapfiSessionState] = None

  private def ensureHandle(): RapfiProcessHandle = synchronized {
    currentHandle.filter(_.isAlive).getOrElse {
      val handle = factory.start(config.command)
      RapfiAdapter.openingHandshake(config.boardSize).foreach(handle.writeLine)
      currentHandle = Some(handle)
      currentSession = None
      handle
    }
  }

  private def currentSyncedMoves(handle: RapfiProcessHandle): Option[Vector[Move]] = synchronized {
    Option.when(currentHandle.contains(handle))(currentSession.map(_.syncedMoves)).flatten
  }

  private def canContinueMoveSession(position: EnginePosition): Boolean = synchronized {
    currentSession.exists: session =>
      session.ruleSet == position.ruleSet
        && position.moves.length == session.syncedMoves.length + 1
        && position.moves.startsWith(session.syncedMoves)
  }

  private def ensureMoveHandle(position: EnginePosition): RapfiProcessHandle =
    currentHandle.filter(_.isAlive) match
      case Some(handle) if canContinueMoveSession(position) => handle
      case Some(handle) =>
        invalidateHandle(handle)
        ensureHandle()
      case None => ensureHandle()

  private def rememberMoveSession(handle: RapfiProcessHandle, position: EnginePosition, bestMove: Move): Unit =
    synchronized {
      if currentHandle.contains(handle) then
        currentSession = Some(RapfiSessionState(position.ruleSet, position.moves :+ bestMove))
    }

  private def invalidateHandle(handle: RapfiProcessHandle): Unit =
    synchronized {
      if currentHandle.contains(handle) then
        currentHandle = None
        currentSession = None
    }
    handle.close()

  private def withFreshHandle[A](use: RapfiProcessHandle => A): A =
    currentHandle.filter(_.isAlive).foreach(invalidateHandle)
    val handle = ensureHandle()
    try use(handle)
    finally invalidateHandle(handle)

  def bestMove(request: EngineMoveRequest): Fu[EngineMoveResponse] = Future {
    val handle = ensureMoveHandle(request.position)
    try
      val batch =
        RapfiCommandBatch(
          RapfiAdapter.incrementalMoveRequest(request.position, request.limits, currentSyncedMoves(handle)),
          RapfiResponseBoundary.BestMove
        )
      batch.lines.foreach(handle.writeLine)
      val response = collectMoveResponse(handle)
      response match
        case Right(move) =>
          rememberMoveSession(handle, request.position, move.bestMove)
          move
        case Left(err)   => throw new RuntimeException(err)
    catch
      case NonFatal(err) =>
        invalidateHandle(handle)
        throw err
  }

  def analyse(request: EngineAnalysisRequest): Fu[EngineAnalysisResponse] = Future {
    withFreshHandle { handle =>
      val batch = RapfiCommandBatch.analysis(request)
      batch.lines.foreach(handle.writeLine)
      collectMoveResponse(handle) match
        case Right(move) =>
          EngineAnalysisResponse.fromMoveResponse(move)
        case Left(_) =>
          EngineAnalysisResponse.normalized(Vector.empty, Map("status" -> "phase1-scaffold"))
    }
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
