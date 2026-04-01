package lila.round

import lila.core.id.GameId
import lila.omok.{ CoordinateNotation, Game as OmokGame, Move as OmokMove, Replay, RuleSet }

final case class OmokStoredState(
    _id: GameId,
    ruleSet: String,
    moves: Vector[String]
):
  def toRoundState: Either[String, OmokRoundState] =
    OmokStoredState.toRoundState(this)

object OmokStoredState:

  def fromState(gameId: GameId, state: OmokRoundState): OmokStoredState =
    OmokStoredState(
      _id = gameId,
      ruleSet = renderRuleSet(state.ruleSet),
      moves = state.moves.map(_.pos.key)
    )

  def toRoundState(stored: OmokStoredState): Either[String, OmokRoundState] =
    for
      ruleSet <- parseRuleSet(stored.ruleSet)
      parsedMoves <- stored.moves.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[OmokMove]]):
        case (Right(acc), (raw, _)) =>
          CoordinateNotation
            .parse(raw)
            .map(OmokMove.apply)
            .toRight(s"invalid stored omok move '$raw'")
            .map(acc :+ _)
        case (left @ Left(_), _) => left
      game <- Replay(OmokGame.initial(ruleSet), parsedMoves)
        .left
        .map(err => s"invalid stored omok sequence at move ${err.index + 1} ${err.move.pos.key}: ${err.cause.toString.toLowerCase}")
    yield OmokRoundState.fromGame(game, parsedMoves)

  private def parseRuleSet(raw: String): Either[String, RuleSet] =
    raw.trim.toLowerCase match
      case "renju"     => Right(RuleSet.Renju)
      case "freestyle" => Right(RuleSet.Freestyle)
      case other        => Left(s"invalid stored omok rule set '$other'")

  private def renderRuleSet(ruleSet: RuleSet): String =
    ruleSet.toString.toLowerCase
