package lila.round

import scala.collection.concurrent.TrieMap

import lila.core.id.GameId
import lila.omok.{ Game as OmokGame, Move as OmokMove, Pos, PositionSnapshot, RuleSet }

final case class OmokRoundState(
    position: PositionSnapshot,
    moves: Vector[OmokMove] = Vector.empty
):
  def boardSize: Int = Pos.Size
  def ruleSet: RuleSet = position.ruleSet

object OmokRoundState:
  def initial(ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    OmokRoundState(PositionSnapshot.fromGame(OmokGame.initial(ruleSet)))

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
