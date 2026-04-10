package lila.round

import reactivemongo.api.bson.*

import lila.core.id.GameId
import lila.db.dsl.*
import lila.game.OmokGameSidecar
import lila.omok.{ CoordinateNotation, Game as OmokGame, Move as OmokMove, Replay, RuleSet }

object OmokStoredState:
  object BsonFields:
    val ruleSet = "ruleSet"
    val moves = "moves"
    val swapCount = "swapCount"
    val forceSimpleFifth = "forceSimpleFifth"
    val finalSwapUsed = "finalSwapUsed"
    val candidateMode = "candidateMode"
    val candidateSelection = "candidateSelection"
    val candidates = "candidates"

  def fromState(gameId: GameId, state: OmokRoundState): OmokGameSidecar =
    OmokGameSidecar(
      _id = gameId,
      ruleSet = renderRuleSet(state.ruleSet),
      moves = state.moves.map(_.pos.key),
      swapCount = state.opening.swapCount,
      forceSimpleFifth = state.opening.forceSimpleFifth,
      finalSwapUsed = state.opening.finalSwapUsed,
      candidateMode = state.opening.candidateMode,
      candidateSelection = state.opening.candidateSelection,
      candidates = state.opening.candidateMoves.map(_.pos.key)
    )

  def toBdoc(stored: OmokGameSidecar): Bdoc =
    $doc(
      BsonFields.ruleSet -> stored.ruleSet,
      BsonFields.moves -> stored.moves,
      BsonFields.swapCount -> stored.swapCount,
      BsonFields.forceSimpleFifth -> stored.forceSimpleFifth,
      BsonFields.finalSwapUsed -> stored.finalSwapUsed,
      BsonFields.candidateMode -> stored.candidateMode,
      BsonFields.candidateSelection -> stored.candidateSelection,
      BsonFields.candidates -> stored.candidates
    )

  def toRoundState(stored: OmokGameSidecar): Either[String, OmokRoundState] =
    for
      ruleSet <- parseRuleSet(stored.ruleSet)
      normalizedMoves <- stored.moves.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[String]]):
        case (Right(acc), (raw, index)) =>
          val normalized = raw.trim
          Either.cond(
            normalized.nonEmpty,
            acc :+ normalized,
            s"invalid stored omok move at index ${index + 1}: blank"
          )
        case (left @ Left(_), _) => left
      parsedMoves <- normalizedMoves.foldLeft(Right(Vector.empty): Either[String, Vector[OmokMove]]):
        case (Right(acc), raw) =>
          CoordinateNotation
            .parse(raw)
            .map(OmokMove.apply)
            .toRight(s"invalid stored omok move '$raw'")
            .map(acc :+ _)
        case (left @ Left(_), _) => left
      game <- Replay(OmokGame.initial(ruleSet), parsedMoves)
        .left
        .map(err => s"invalid stored omok sequence at move ${err.index + 1} ${err.move.pos.key}: ${err.cause.toString.toLowerCase}")
      parsedCandidates <- stored.candidates.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[OmokMove]]):
        case (Right(acc), (raw, index)) =>
          CoordinateNotation
            .parse(raw.trim)
            .map(OmokMove.apply)
            .toRight(s"invalid stored omok candidate at index ${index + 1}: '$raw'")
            .map(acc :+ _)
        case (left @ Left(_), _) => left
    yield OmokRoundState.fromGame(
      game,
      parsedMoves,
      OmokOpeningState(
        swapCount = stored.swapCount,
        forceSimpleFifth = stored.forceSimpleFifth,
        finalSwapUsed = stored.finalSwapUsed,
        candidateMode = stored.candidateMode,
        candidateSelection = stored.candidateSelection,
        candidateMoves = parsedCandidates
      )
    )

  private def parseRuleSet(raw: String): Either[String, RuleSet] =
    raw.trim.toLowerCase match
      case "renju"     => Right(RuleSet.Renju)
      case "freestyle" => Right(RuleSet.Freestyle)
      case "taraguchi10" | "taraguchi-10" | "taraguchi" => Right(RuleSet.Taraguchi10)
      case other        => Left(s"invalid stored omok rule set '$other'")

  private def renderRuleSet(ruleSet: RuleSet): String =
    ruleSet.toString.toLowerCase
