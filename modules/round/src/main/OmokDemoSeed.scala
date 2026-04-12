package lila.round

import lila.core.id.{ GameFullId, GameId }
import lila.omok.{ Color as OmokColor, CoordinateNotation, Game as OmokGame, Move as OmokMove, MoveError as OmokMoveError, Replay, RuleSet }

object OmokDemoSeed:
  def apply(omokRoundRepo: OmokRoundRepo): OmokDemoSeed =
    given Executor = scala.concurrent.ExecutionContext.global
    new OmokDemoSeed(omokRoundRepo)

final class OmokDemoSeed(
    omokRoundRepo: OmokRoundRepo,
    gameRepo: lila.game.GameRepo | Null = null,
    proxyRepo: GameProxyRepo | Null = null
)(using Executor):

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

  def dumpAll: String =
    val lines =
      omokRoundRepo.snapshot.toVector.sortBy(_._1.value).map: (gameId, state) =>
        val ruleSet = renderRuleSet(state.ruleSet)
        val moves = state.moves.map(_.pos.key)
        val cmd =
          if moves.isEmpty then s"bin/omok-demo seed $gameId $ruleSet"
          else s"bin/omok-demo seed $gameId $ruleSet ${moves.mkString(" ")}"
        s"$cmd    # ${renderState(state)}"
    if lines.isEmpty then "no omok round state to dump"
    else lines.mkString("\n")

  def move(rawGameKey: String, rawMove: String): String =
    parseGameKey(rawGameKey) match
      case Left(err) => s"ERROR $err"
      case Right(gameId) =>
        omokRoundRepo.get(gameId) match
          case None => s"no omok round state for $gameId"
          case Some(state) =>
            CoordinateNotation
              .parse(rawMove.trim)
              .map(OmokMove.apply)
              .toRight(s"invalid omok move '$rawMove'; expected coordinates like H8 or A1")
              .flatMap: move =>
                Replay(OmokGame.initial(state.ruleSet), state.moves :+ move)
                  .left
                  .map(renderReplayError)
                  .map: game =>
                    val next = OmokRoundState.fromGame(game, state.moves :+ move)
                    omokRoundRepo.put(gameId, next)
                    next
              .fold(
                err => s"ERROR $err",
                next =>
                  syncFinishedGame(gameId, next)
                  s"moved omok round $gameId: ${renderState(next)}"
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

  private def parseGameKey(raw: String): Either[String, GameId] =
    parseGameId(raw).orElse {
      val trimmed = raw.trim
      if trimmed.length == GameFullId.size then
        GameId.from(trimmed.take(GameId.size)).toRight(s"invalid game id/fullId '$raw'; expected 8-char gameId or 12-char player fullId")
      else Left(s"invalid game id/fullId '$raw'; expected 8-char gameId or 12-char player fullId")
    }

  private def parseRuleSetAndMoves(rawArgs: List[String]): Either[String, (RuleSet, List[String])] =
    rawArgs match
      case rawRuleSet :: moveArgs if parseRuleSet(rawRuleSet).isDefined =>
        Right(parseRuleSet(rawRuleSet).get -> moveArgs)
      case moveArgs => Right(RuleSet.Renju -> moveArgs)

  private def parseRuleSet(raw: String): Option[RuleSet] =
    raw.trim.toLowerCase match
      case "renju"     => Some(RuleSet.Renju)
      case "freestyle" => Some(RuleSet.Freestyle)
      case "taraguchi10" | "taraguchi-10" | "taraguchi" => Some(RuleSet.Taraguchi10)
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
      OmokRoundState.fromGame(game, moves)

  private def syncFinishedGame(gameId: GameId, state: OmokRoundState): Unit =
    state.terminalStatus.foreach: terminalStatus =>
      if gameRepo != null then
        val genericStatus = terminalStatus match
          case lila.omok.Status.Win(_) => chess.Status.VariantEnd
          case lila.omok.Status.Draw   => chess.Status.Draw
          case lila.omok.Status.Ongoing => chess.Status.Started
        if genericStatus != chess.Status.Started then
          val winnerColor = terminalStatus match
            case lila.omok.Status.Win(color) => Some(colorFromOmok(color))
            case _                           => None
          val finishedGameOpt = scala.concurrent.Await.result(gameRepo.game(gameId), scala.concurrent.duration.DurationInt(3).seconds).map: game =>
            game.copy(
              status = genericStatus,
              movedAt = nowInstant,
              players = game.players.map: player =>
                player.copy(isWinner = winnerColor.map(_ == player.color))
            )
          finishedGameOpt.foreach: finishedGame =>
            val winnerId = finishedGame.winnerUserId
            scala.concurrent.Await.result(gameRepo.finish(gameId, winnerColor, winnerId, genericStatus), scala.concurrent.duration.DurationInt(3).seconds)
            if proxyRepo != null then
              scala.concurrent.Await.result(proxyRepo.updateIfPresent(gameId)(_ => finishedGame), scala.concurrent.duration.DurationInt(3).seconds)

  private def colorFromOmok(color: OmokColor): chess.Color =
    if color == lila.omok.Color.Black then chess.Color.Black else chess.Color.White

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
