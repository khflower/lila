package lila.round

import lila.core.id.GameId
import lila.omok.{ Color as OmokColor, CoordinateNotation, Game as OmokGame, Move as OmokMove, MoveError as OmokMoveError, Replay, RuleSet }

object OmokDemoSeed:
  def apply(omokRoundRepo: OmokRoundRepo): OmokDemoSeed = new OmokDemoSeed(omokRoundRepo)

final class OmokDemoSeed(omokRoundRepo: OmokRoundRepo):

  def seed(rawGameId: String, rawArgs: List[String]): String =
    parseSpec(rawGameId, rawArgs).fold(
      err => s"ERROR $err",
      spec =>
        buildState(spec.ruleSet, spec.moves).fold(
          err => s"ERROR $err",
          state =>
            val replaced = omokRoundRepo.get(spec.gameId).isDefined
            omokRoundRepo.put(spec.gameId, state)
            s"${if replaced then "reseeded" else "seeded"} omok round ${spec.gameId}: ${renderState(state)}"
        )
    )

  def show(rawGameId: String): String =
    parseGameId(rawGameId).fold(
      err => s"ERROR $err",
      gameId =>
        omokRoundRepo
          .get(gameId)
          .fold(s"no omok round state for $gameId")(state => s"omok round $gameId: ${renderState(state)}")
    )

  def clear(rawGameId: String): String =
    parseGameId(rawGameId).fold(
      err => s"ERROR $err",
      gameId =>
        omokRoundRepo
          .remove(gameId)
          .fold(s"no omok round state to clear for $gameId")(state => s"cleared omok round $gameId: ${renderState(state)}")
    )

  private case class SeedSpec(gameId: GameId, ruleSet: RuleSet, moves: Vector[OmokMove])

  private def parseSpec(rawGameId: String, rawArgs: List[String]): Either[String, SeedSpec] =
    for
      gameId <- parseGameId(rawGameId)
      (ruleSet, moveArgs) <- parseRuleSetAndMoves(rawArgs)
      moves <- parseMoves(moveArgs)
    yield SeedSpec(gameId, ruleSet, moves)

  private def parseGameId(raw: String): Either[String, GameId] =
    GameId
      .from(raw)
      .toRight(s"invalid game id '$raw'; expected 8 characters matching [A-Za-z0-9_-]")

  private def parseRuleSetAndMoves(rawArgs: List[String]): Either[String, (RuleSet, List[String])] =
    rawArgs match
      case rawRuleSet :: moveArgs if parseRuleSet(rawRuleSet).isDefined =>
        Right(parseRuleSet(rawRuleSet).get -> moveArgs)
      case moveArgs => Right(RuleSet.Renju -> moveArgs)

  private def parseRuleSet(raw: String): Option[RuleSet] =
    raw.trim.toLowerCase match
      case "renju"     => Some(RuleSet.Renju)
      case "freestyle" => Some(RuleSet.Freestyle)
      case _           => None

  private def parseMoves(rawArgs: List[String]): Either[String, Vector[OmokMove]] =
    normalizedMoveArgs(rawArgs).zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[OmokMove]]):
      case (Right(moves), (rawMove, _)) =>
        CoordinateNotation
          .parse(rawMove)
          .map(OmokMove.apply)
          .toRight(s"invalid omok move '$rawMove'; expected coordinates like H8 or A1")
          .map(moves :+ _)
      case (left @ Left(_), _) => left

  private def normalizedMoveArgs(rawArgs: List[String]): List[String] =
    rawArgs.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)

  private def buildState(ruleSet: RuleSet, moves: Vector[OmokMove]): Either[String, OmokRoundState] =
    Replay(OmokGame.initial(ruleSet), moves).left.map(renderReplayError).map: game =>
      OmokRoundState(lila.omok.PositionSnapshot.fromGame(game, moves), moves)

  private def renderReplayError(err: lila.omok.ReplayError): String =
    val moveNumber = err.index + 1
    s"illegal omok move #$moveNumber ${err.move.pos.key}: ${renderMoveError(err.cause)}"

  private def renderMoveError(err: OmokMoveError): String = err match
    case OmokMoveError.Occupied          => "occupied"
    case OmokMoveError.OutOfBounds       => "out of bounds"
    case OmokMoveError.WrongTurn         => "wrong turn"
    case OmokMoveError.GameAlreadyOver   => "game already over"
    case OmokMoveError.Forbidden(reason) => s"forbidden (${reason.toString.toLowerCase})"

  private def renderState(state: OmokRoundState): String =
    val lastMove = state.position.lastMove.fold("-")(_.pos.key)
    s"ruleSet=${renderRuleSet(state.ruleSet)} ply=${state.position.ply} turn=${renderColor(state.position.turn)} lastMove=$lastMove moves=${renderMoves(state.moves)}"

  private def renderRuleSet(ruleSet: RuleSet): String = ruleSet.toString.toLowerCase

  private def renderColor(color: OmokColor): String = color.toString.toLowerCase

  private def renderMoves(moves: Vector[OmokMove]): String =
    if moves.isEmpty then "-"
    else moves.map(_.pos.key).mkString(",")
