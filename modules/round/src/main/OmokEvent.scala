package lila.round

import play.api.libs.json.{ JsObject, Json, OWrites }

import lila.core.id.GameId
import lila.omok.{ Move as OmokMove, OmokMoveDto, OmokPositionDto, PositionSnapshot }

object OmokEvent:

  val moveType = "omokMove"

  final case class MovePayload(
      move: OmokMoveDto,
      position: OmokPositionDto,
      status: Option[String] = None,
      winner: Option[String] = None
  )

  object MovePayload:
    given OWrites[MovePayload] = OWrites: payload =>
      JsObject(
        List(
          Some("move" -> Json.toJson(payload.move)),
          Some("position" -> Json.toJson(payload.position)),
          payload.status.map("status" -> Json.toJson(_)),
          payload.winner.map("winner" -> Json.toJson(_))
        ).flatten
      )

    def apply(move: OmokMove, position: PositionSnapshot): MovePayload =
      MovePayload(
        move = OmokMoveDto.fromMove(move),
        position = OmokPositionDto.fromPosition(position)
      )

    def apply(move: OmokMove, state: OmokRoundState): MovePayload =
      val analyse = state.analyseDto
      MovePayload(
        move = OmokMoveDto.fromMove(move),
        position = OmokPositionDto.fromPosition(state.position),
        status = analyse.status,
        winner = analyse.winner
      )

  final case class Move(
      gameId: GameId,
      payload: MovePayload
  ):
    val typ = moveType

  object Move:
    def apply(gameId: GameId, move: OmokMove, position: PositionSnapshot): Move =
      Move(gameId = gameId, payload = MovePayload(move, position))

    def apply(gameId: GameId, move: OmokMove, state: OmokRoundState): Move =
      Move(gameId = gameId, payload = MovePayload(move, state))
