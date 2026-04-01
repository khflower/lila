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
    moves: Vector[Move] = Vector.empty
)

object PositionSnapshot:
  def fromGame(game: Game, moves: Vector[Move] = Vector.empty): PositionSnapshot =
    PositionSnapshot(
      board = game.situation.board,
      turn = game.situation.turn,
      ruleSet = game.situation.ruleSet,
      moves = moves
    )

type EnginePosition = PositionSnapshot
object EnginePosition:
  def fromGame(game: Game, moves: Vector[Move] = Vector.empty): EnginePosition =
    PositionSnapshot.fromGame(game, moves)

enum ScoreKind:
  case Centipawn, Mate

final case class EngineScore(kind: ScoreKind, value: Int)

final case class EngineLine(
    moves: Vector[Move],
    rank: Int = 1,
    score: Option[EngineScore] = None,
    nodes: Option[Long] = None,
    depth: Option[Int] = None
):
  require(rank > 0, "rank must be > 0")

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
  def bestMove: Option[Move] = primaryVariation.flatMap(_.moves.headOption)

object EngineAnalysisResponse:
  def normalized(lines: Vector[EngineLine], raw: Map[String, String] = Map.empty): EngineAnalysisResponse =
    EngineAnalysisResponse(EngineLine.normalized(lines), raw)

object EngineAnalysisResult:
  def apply(lines: Vector[EngineLine], raw: Map[String, String] = Map.empty): EngineAnalysisResponse =
    EngineAnalysisResponse.normalized(lines, raw)

trait EngineClient:
  def bestMove(request: EngineMoveRequest): Fu[EngineMoveResponse]
  def analyse(request: EngineAnalysisRequest): Fu[EngineAnalysisResponse]
