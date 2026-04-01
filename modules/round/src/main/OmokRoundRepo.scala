package lila.round

import scala.collection.concurrent.TrieMap

import lila.core.id.GameId
import lila.omok.{ Color as OmokColor, Game as OmokGame, Move as OmokMove, OmokAnalyseDto, OmokPositionDto, Pos, PositionSnapshot, RuleSet, Status as OmokStatus }

final case class OmokRoundState(
    position: PositionSnapshot,
    moves: Vector[OmokMove] = Vector.empty,
    terminalStatus: Option[OmokStatus] = None
):
  def boardSize: Int = Pos.Size
  def ruleSet: RuleSet = position.ruleSet
  def analyseDto: OmokAnalyseDto =
    terminalStatus.fold(OmokAnalyseDto.fromPosition(position)): status =>
      OmokAnalyseDto(
        position = Some(OmokPositionDto.fromPosition(position)),
        status = Some(OmokRoundState.statusKey(status)),
        winner = OmokRoundState.winnerKey(status)
      )

object OmokRoundState:
  def initial(ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    fromGame(OmokGame.initial(ruleSet))

  def fromGame(game: OmokGame, moves: Vector[OmokMove] = Vector.empty): OmokRoundState =
    OmokRoundState(
      position = PositionSnapshot.fromGame(game, moves),
      moves = moves,
      terminalStatus = terminalStatus(game.status)
    )

  private def terminalStatus(status: OmokStatus): Option[OmokStatus] = status match
    case OmokStatus.Ongoing => None
    case terminal           => Some(terminal)

  private def statusKey(status: OmokStatus): String = status match
    case OmokStatus.Ongoing => "ongoing"
    case OmokStatus.Win(_)  => "win"
    case OmokStatus.Draw    => "draw"

  private def winnerKey(status: OmokStatus): Option[String] = status match
    case OmokStatus.Win(OmokColor.Black) => Some("black")
    case OmokStatus.Win(OmokColor.White) => Some("white")
    case _                               => None

object OmokRoundRepo:
  def apply(): OmokRoundRepo = new OmokRoundRepo

final class OmokRoundRepo:

  private val states = TrieMap.empty[GameId, OmokRoundState]

  def get(gameId: GameId): Option[OmokRoundState] = states.get(gameId)

  def getOrInit(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    states.getOrElseUpdate(gameId, OmokRoundState.initial(ruleSet))

  def put(gameId: GameId, state: OmokRoundState): OmokRoundState =
    states.put(gameId, state)
    state

  def remove(gameId: GameId): Option[OmokRoundState] = states.remove(gameId)

  def update(gameId: GameId)(f: Option[OmokRoundState] => OmokRoundState): OmokRoundState =
    val next = f(states.get(gameId))
    states.put(gameId, next)
    next
