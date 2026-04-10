package lila.game

import lila.core.id.GameId

final case class OmokGameSidecar(
    _id: GameId,
    ruleSet: String,
    moves: Vector[String],
    swapCount: Int = 0,
    forceSimpleFifth: Boolean = false,
    finalSwapUsed: Boolean = false,
    candidateMode: Boolean = false,
    candidateSelection: Boolean = false,
    candidates: Vector[String] = Vector.empty
)
