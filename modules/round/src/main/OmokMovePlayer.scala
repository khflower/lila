package lila.round

import lila.core.id.GameId
import lila.omok.{ Game as OmokGame, Move as OmokMove, MoveError as OmokMoveError, Pos, PositionSnapshot, Replay, RuleSet }

final case class PlaceRequest(
    gameId: GameId,
    pos: Pos,
    ruleSet: RuleSet = RuleSet.Renju
)

final case class PlaceError(
    request: PlaceRequest,
    state: OmokRoundState,
    cause: OmokMoveError
):
  def message: String =
    s"[omok] ${request.gameId} cannot place ${request.pos.key}: ${PlaceError.render(cause)}"

object PlaceError:
  def render(cause: OmokMoveError): String = cause match
    case OmokMoveError.Occupied => "occupied"
    case OmokMoveError.OutOfBounds => "out of bounds"
    case OmokMoveError.WrongTurn => "wrong turn"
    case OmokMoveError.GameAlreadyOver => "game already over"
    case OmokMoveError.Forbidden(reason) => s"forbidden (${reason.toString.toLowerCase})"

final case class PlaceAccepted(
    previous: OmokRoundState,
    state: OmokRoundState,
    move: OmokMove,
    payload: OmokEvent.MovePayload,
    event: OmokEvent.Move
)

final class OmokMovePlayer(omokRoundRepo: OmokRoundRepo):

  def ensure(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    omokRoundRepo.getOrInit(gameId, ruleSet)

  def place(request: PlaceRequest): Either[PlaceError, PlaceAccepted] =
    val previous = omokRoundRepo.getOrInit(request.gameId, request.ruleSet)
    OmokMovePlayer.preview(request.gameId, previous, request.pos).map: accepted =>
      omokRoundRepo.put(request.gameId, accepted.state)
      accepted

object OmokMovePlayer:

  def preview(gameId: GameId, previous: OmokRoundState, pos: Pos): Either[PlaceError, PlaceAccepted] =
    val request = PlaceRequest(gameId, pos, previous.ruleSet)
    val move = OmokMove(pos)

    currentGame(previous).play(move).left.map(PlaceError(request, previous, _)).map: nextGame =>
      val moves = previous.moves :+ move
      val state = OmokRoundState(PositionSnapshot.fromGame(nextGame, moves), moves)
      val payload = OmokEvent.MovePayload(move, state.position)
      PlaceAccepted(
        previous = previous,
        state = state,
        move = move,
        payload = payload,
        event = OmokEvent.Move(gameId, move, state.position)
      )

  private def currentGame(state: OmokRoundState): OmokGame =
    Replay(OmokGame.initial(state.ruleSet), state.moves).toOption.getOrElse(OmokGame.initial(state.ruleSet))
