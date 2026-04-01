package lila.round

import lila.core.id.GameId
import lila.omok.{ Move as OmokMove, OmokMoveDto, OmokPositionDto, PositionSnapshot }

object OmokEvent:

  val moveType = "omokMove"

  final case class MovePayload(
      move: OmokMoveDto,
      position: OmokPositionDto
  )

  object MovePayload:
    def apply(move: OmokMove, position: PositionSnapshot): MovePayload =
      MovePayload(
        move = OmokMoveDto.fromMove(move),
        position = OmokPositionDto.fromPosition(position)
      )

  final case class Move(
      gameId: GameId,
      payload: MovePayload
  ):
    val typ = moveType

  object Move:
    def apply(gameId: GameId, move: OmokMove, position: PositionSnapshot): Move =
      Move(gameId = gameId, payload = MovePayload(move, position))
