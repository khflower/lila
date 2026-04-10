package lila.core
package omok

import lila.core.chess.{ Depth, MultiPv }

enum Color:
  case Black, White

  def other: Color = this match
    case Black => White
    case White => Black

enum RuleSet:
  case Freestyle, Renju

enum Status:
  case Ongoing
  case Win(color: Color)
  case Draw

final case class Pos(row: Int, col: Int):
  require(row >= 0, "row must be >= 0")
  require(col >= 0, "col must be >= 0")

final case class Move(pos: Pos)

final case class Stone(pos: Pos, color: Color)

// Stable omok snapshot for cross-module APIs. It carries both occupied stones
// and explicit game status plus optional move history so adapters can choose
// the cheapest encoding without hidden terminal state.
final case class PositionSnapshot(
    boardSize: Int = PositionSnapshot.defaultBoardSize,
    turn: Color,
    ruleSet: RuleSet,
    status: Status = Status.Ongoing,
    stones: Vector[Stone] = Vector.empty,
    ply: Int = 0,
    lastMove: Option[Move] = None,
    moves: Vector[Move] = Vector.empty
):
  require(boardSize > 0, "boardSize must be > 0")
  require(ply >= 0, "ply must be >= 0")
  require(
    stones.map(_.pos).distinct.size == stones.size,
    "stones must not contain duplicate positions"
  )
  require(
    stones.forall(stone => PositionSnapshot.contains(boardSize, stone.pos)),
    "stone positions must fit inside the board"
  )
  require(
    lastMove.forall(move => PositionSnapshot.contains(boardSize, move.pos)),
    "lastMove must fit inside the board"
  )
  require(
    moves.forall(move => PositionSnapshot.contains(boardSize, move.pos)),
    "moves must fit inside the board"
  )

object PositionSnapshot:
  val defaultBoardSize = 15

  def initial(
      ruleSet: RuleSet = RuleSet.Renju,
      boardSize: Int = defaultBoardSize
  ): PositionSnapshot =
    PositionSnapshot(boardSize = boardSize, turn = Color.Black, ruleSet = ruleSet)

  def contains(boardSize: Int, pos: Pos): Boolean =
    pos.row < boardSize && pos.col < boardSize

type EnginePosition = PositionSnapshot
object EnginePosition:
  def initial(
      ruleSet: RuleSet = RuleSet.Renju,
      boardSize: Int = PositionSnapshot.defaultBoardSize
  ): EnginePosition =
    PositionSnapshot.initial(ruleSet, boardSize)

final case class EngineLimits(
    nodes: Option[Long] = None,
    depth: Option[Depth] = None,
    moveTime: Option[FiniteDuration] = None,
    multiPv: MultiPv = MultiPv(1)
):
  require(nodes.forall(_ > 0), "nodes must be > 0 when provided")
  require(depth.forall(_.value > 0), "depth must be > 0 when provided")
  require(moveTime.forall(_ > Duration.Zero), "moveTime must be > 0 when provided")
  require(multiPv.value > 0, "multiPv must be > 0")

enum ScoreKind:
  case Relative, WinRate, ForcedWin, ForcedLoss

final case class EngineScore(kind: ScoreKind, value: Int)

final case class EngineLine(
    moves: Vector[Move],
    rank: Int = 1,
    score: Option[EngineScore] = None,
    nodes: Option[Long] = None,
    depth: Option[Depth] = None
):
  require(moves.nonEmpty, "moves cannot be empty")
  require(rank > 0, "rank must be > 0")
  require(nodes.forall(_ > 0), "nodes must be > 0 when provided")
  require(depth.forall(_.value > 0), "depth must be > 0 when provided")

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

trait OmokEngineApi:
  def play(request: EngineMoveRequest): Fu[Either[EngineError, EngineMoveResponse]]
  def analyse(request: EngineAnalysisRequest): Fu[Either[EngineError, EngineAnalysisResponse]]
  def health: Fu[EngineHealth]
