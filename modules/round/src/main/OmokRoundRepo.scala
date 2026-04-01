package lila.round

import scala.collection.concurrent.TrieMap

import lila.core.id.GameId
import lila.game.OmokGameSidecar
import lila.omok.{ Game as OmokGame, Move as OmokMove, Pos, RuleSet }

final case class OmokRoundState(
    position: lila.omok.PositionSnapshot,
    moves: Vector[OmokMove] = Vector.empty,
    terminalStatus: Option[lila.omok.Status] = None
):
  def boardSize: Int = Pos.Size
  def ruleSet: RuleSet = position.ruleSet
  def analyseDto = lila.omok.OmokAnalyseDto.fromPosition(position)

object OmokRoundState:
  def initial(ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    fromGame(OmokGame.initial(ruleSet))

  def fromGame(
      game: OmokGame,
      moves: Vector[OmokMove] = Vector.empty
  ): OmokRoundState =
    OmokRoundState(
      position = lila.omok.PositionSnapshot.fromGame(game, moves),
      moves = moves,
      terminalStatus = Option.when(game.status != lila.omok.Status.Ongoing)(game.status)
    )

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

  def getStored(gameId: GameId): Option[OmokGameSidecar] =
    get(gameId).map(OmokStoredState.fromState(gameId, _))

  def getOrHydrate(gameId: GameId)(loadStored: => Option[OmokGameSidecar]): Either[String, Option[OmokRoundState]] =
    get(gameId) match
      case some @ Some(_) => Right(some)
      case None =>
        loadStored match
          case Some(stored) => putStored(gameId, stored).map(Some(_))
          case None         => Right(None)

  def putStored(gameId: GameId, stored: OmokGameSidecar): Either[String, OmokRoundState] =
    val normalized = stored.copy(_id = gameId)
    putStored(normalized)

  def putStored(stored: OmokGameSidecar): Either[String, OmokRoundState] =
    OmokStoredState.toRoundState(stored).map: state =>
      put(stored._id, state)

  def buildState(game: OmokGame, moves: Vector[OmokMove]): OmokRoundState =
    OmokRoundState.fromGame(game, moves)
