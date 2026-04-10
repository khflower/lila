package lila.omok

import lila.core.lilaism.Core.*

final case class EngineLimits(
    nodes: Option[Long] = None,
    depth: Option[Int] = None,
    moveTime: Option[FiniteDuration] = None,
    multiPv: Int = 1
):
  require(nodes.forall(_ > 0), "nodes must be > 0 when provided")
  require(depth.forall(_ > 0), "depth must be > 0 when provided")
  require(moveTime.forall(_ > Duration.Zero), "moveTime must be > 0 when provided")
  require(multiPv > 0, "multiPv must be > 0")

final case class PositionSnapshot(
    board: Board,
    turn: Color,
    ruleSet: RuleSet,
    status: Status = Status.Ongoing,
    ply: Int = 0,
    lastMove: Option[Move] = None,
    moves: Vector[Move] = Vector.empty
):
  require(ply >= 0, "ply must be >= 0")

object PositionSnapshot:
  def fromSituation(
      situation: Situation,
      ply: Int = 0,
      lastMove: Option[Move] = None,
      moves: Vector[Move] = Vector.empty
  ): PositionSnapshot =
    PositionSnapshot(
      board = situation.board,
      turn = situation.turn,
      ruleSet = situation.ruleSet,
      status = Status.Ongoing,
      ply = ply,
      lastMove = lastMove,
      moves = moves
    )

  def fromGame(game: Game, moves: Vector[Move] = Vector.empty): PositionSnapshot =
    PositionSnapshot(
      board = game.situation.board,
      turn = game.situation.turn,
      ruleSet = game.situation.ruleSet,
      status = game.status,
      ply = game.ply,
      lastMove = game.lastMove,
      moves = moves
    )

type EnginePosition = PositionSnapshot
object EnginePosition:
  def fromSituation(
      situation: Situation,
      ply: Int = 0,
      lastMove: Option[Move] = None,
      moves: Vector[Move] = Vector.empty
  ): EnginePosition = PositionSnapshot.fromSituation(situation, ply, lastMove, moves)

  def fromGame(game: Game, moves: Vector[Move] = Vector.empty): EnginePosition =
    PositionSnapshot.fromGame(game, moves)

enum ScoreKind:
  case Relative, WinRate, ForcedWin, ForcedLoss

final case class EngineScore(kind: ScoreKind, value: Int)

final case class EngineLine(
    moves: Vector[Move],
    rank: Int = 1,
    score: Option[EngineScore] = None,
    nodes: Option[Long] = None,
    depth: Option[Int] = None
):
  require(moves.nonEmpty, "moves cannot be empty")
  require(rank > 0, "rank must be > 0")

  def bestMove: Move = moves.head

object EngineLine:
  def normalized(lines: Vector[EngineLine]): Vector[EngineLine] = lines.sortBy(_.rank)

final case class EngineMoveRequest(
    position: EnginePosition,
    limits: EngineLimits = EngineLimits(),
    searchMoves: Option[Set[Move]] = None
):
  require(searchMoves.forall(_.nonEmpty), "searchMoves cannot be empty when provided")

final case class EngineAnalysisRequest(
    position: EnginePosition,
    limits: EngineLimits = EngineLimits()
)

final case class EngineMoveResponse(
    bestMove: Move,
    principalVariation: Vector[Move] = Vector.empty,
    score: Option[EngineScore] = None,
    raw: Map[String, String] = Map.empty
):
  def normalizedPrincipalVariation: Vector[Move] =
    if principalVariation.headOption.contains(bestMove) then principalVariation
    else bestMove +: principalVariation

  def line: EngineLine = EngineLine(normalizedPrincipalVariation, rank = 1, score = score)
  def metadata: Map[String, String] = raw

object EngineMoveResult:
  def apply(
      bestMove: Move,
      pv: Vector[Move] = Vector.empty,
      score: Option[EngineScore] = None,
      raw: Map[String, String] = Map.empty
  ): EngineMoveResponse = EngineMoveResponse(bestMove, pv, score, raw)

final case class EngineAnalysisResponse(
    variations: Vector[EngineLine],
    raw: Map[String, String] = Map.empty
):
  def primaryVariation: Option[EngineLine] = EngineLine.normalized(variations).headOption
  def bestMove: Option[Move] = primaryVariation.map(_.bestMove)
  def metadata: Map[String, String] = raw

object EngineAnalysisResponse:
  def normalized(
      lines: Iterable[EngineLine],
      raw: Map[String, String] = Map.empty
  ): EngineAnalysisResponse =
    EngineAnalysisResponse(EngineLine.normalized(lines.toVector), raw)

  def fromMoveResponse(response: EngineMoveResponse): EngineAnalysisResponse =
    normalized(Vector(response.line), response.raw)

object EngineAnalysisResult:
  def apply(lines: Iterable[EngineLine], raw: Map[String, String] = Map.empty): EngineAnalysisResponse =
    EngineAnalysisResponse.normalized(lines, raw)

enum EngineHealth:
  case Ready
  case Degraded(reason: String)
  case Failed(reason: String)

enum EngineError:
  case Timeout(message: String)
  case Unavailable(message: String)
  case InvalidRequest(message: String)
  case Protocol(message: String)
  case Unexpected(message: String)

trait OmokEngineFacade:
  def play(request: EngineMoveRequest): Fu[Either[EngineError, EngineMoveResponse]]
  def analyse(request: EngineAnalysisRequest): Fu[Either[EngineError, EngineAnalysisResponse]]
  def health: Fu[EngineHealth]

object OmokEngineFacade:
  def from(client: EngineClient): OmokEngineFacade = new OmokEngineFacade:
    import scala.concurrent.ExecutionContext.Implicits.global

    def play(request: EngineMoveRequest) = client.bestMove(request).map(Right(_))
    def analyse(request: EngineAnalysisRequest) = client.analyse(request).map(Right(_))
    def health = client.health

trait EngineClient:
  def bestMove(request: EngineMoveRequest): Fu[EngineMoveResponse]
  def analyse(request: EngineAnalysisRequest): Fu[EngineAnalysisResponse]
  def health: Fu[EngineHealth] = Future.successful(EngineHealth.Ready)
