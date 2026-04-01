package lila.game

import lila.core.id.GameId

final case class OmokGameSidecar(
    _id: GameId,
    ruleSet: String,
    moves: Vector[String]
)
