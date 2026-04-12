package lila.round

import lila.core.id.GameId
import lila.omok.{ Color as OmokColor, Game as OmokGame, Move as OmokMove, MoveError as OmokMoveError, Pos, Replay, RuleSet, Status as OmokStatus }

final case class PlaceRequest(
    gameId: GameId,
    pos: Pos,
    expectedTurn: Option[OmokColor] = None,
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
    case OmokMoveError.Occupied        => "occupied"
    case OmokMoveError.OutOfBounds     => "out of bounds"
    case OmokMoveError.WrongTurn       => "wrong turn"
    case OmokMoveError.GameAlreadyOver => "game already over"
    case OmokMoveError.Forbidden(reason) =>
      s"forbidden (${reason.toString.toLowerCase})"

final case class PlaceAccepted(
    previous: OmokRoundState,
    state: OmokRoundState,
    move: OmokMove,
    payload: OmokEvent.MovePayload,
    event: OmokEvent.Move,
    terminalStatus: Option[OmokStatus]
)

final case class OpeningActionAccepted(
    previous: OmokRoundState,
    state: OmokRoundState,
    event: OmokEvent.Move,
    terminalStatus: Option[OmokStatus] = None
)

final class OmokMovePlayer(omokRoundRepo: OmokRoundRepo):

  def get(gameId: GameId): Option[OmokRoundState] =
    omokRoundRepo.get(gameId)

  def remove(gameId: GameId): Option[OmokRoundState] =
    omokRoundRepo.remove(gameId)

  def evict(gameId: GameId): Option[OmokRoundState] =
    omokRoundRepo.evict(gameId)

  def ensure(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    omokRoundRepo.getOrInitStored(gameId, ruleSet)

  def place(request: PlaceRequest): Either[PlaceError, PlaceAccepted] =
    placeFromState(request, omokRoundRepo.getOrInit(request.gameId, request.ruleSet))

  def placeIfPresent(request: PlaceRequest): Option[Either[PlaceError, PlaceAccepted]] =
    omokRoundRepo.get(request.gameId).map(placeFromState(request, _))

  def swapIfPresent(gameId: GameId, seat: OmokColor): Option[Either[String, OpeningActionAccepted]] =
    omokRoundRepo.get(gameId).map: previous =>
      if !previous.canSwap then Left(s"[omok] $gameId cannot swap now")
      else if previous.activeSeat != seat then Left(s"[omok] $gameId wrong seat for swap")
      else
        val opening = previous.opening.copy(
          swapCount = previous.opening.swapCount + 1,
          lastSwapPly = Some(previous.position.ply),
          forceSimpleFifth = previous.opening.forceSimpleFifth || previous.position.ply == 4,
          finalSwapUsed = previous.opening.finalSwapUsed || previous.position.ply == 5
        ).log(OmokOpeningState.swapEntry(seat, previous.position.ply, finalSwap = previous.position.ply == 5))
        val state = previous.copy(opening = opening)
        omokRoundRepo.put(gameId, state)
        Right(
          OpeningActionAccepted(
            previous,
            state,
            OmokEvent.Move(gameId, previous.moves.lastOption.getOrElse(OmokMove(Pos.unsafe(7, 7))), state)
          )
        )

  def startCandidatesIfPresent(gameId: GameId, seat: OmokColor): Option[Either[String, OpeningActionAccepted]] =
    omokRoundRepo.get(gameId).map: previous =>
      if !previous.canStartCandidates then Left(s"[omok] $gameId cannot start candidate mode now")
      else if previous.activeSeat != seat then Left(s"[omok] $gameId wrong seat for candidate mode")
      else
        val state = previous.copy(
          opening = previous.opening
            .copy(candidateMode = true, candidateSelection = false, finalSwapUsed = true)
            .log(OmokOpeningState.startCandidatesEntry(seat))
        )
        omokRoundRepo.put(gameId, state)
        Right(
          OpeningActionAccepted(
            previous,
            state,
            OmokEvent.Move(gameId, previous.moves.lastOption.getOrElse(OmokMove(Pos.unsafe(7, 7))), state)
          )
        )

  private def placeFromState(request: PlaceRequest, previous: OmokRoundState): Either[PlaceError, PlaceAccepted] =
    request.expectedTurn
      .filterNot(_ == previous.activeSeat)
      .map(_ => PlaceError(request, previous, OmokMoveError.WrongTurn))
      .toLeft(())
      .flatMap: _ =>
        OmokMovePlayer.preview(request.gameId, previous, request.pos).map: accepted =>
          omokRoundRepo.put(request.gameId, accepted.state)
          accepted

object OmokMovePlayer:

  def preview(gameId: GameId, previous: OmokRoundState, pos: Pos): Either[PlaceError, PlaceAccepted] =
    val request = PlaceRequest(gameId, pos, ruleSet = previous.ruleSet)
    val move = OmokMove(pos)

    candidatePreview(gameId, previous, move).getOrElse:
      val rangeError =
        previous.opening.rangeRadius(previous.position).filterNot(OmokOpeningState.withinCenterRadius(pos, _))
      rangeError
        .map(_ => Left(PlaceError(request, previous, OmokMoveError.OutOfBounds)))
        .getOrElse(currentGame(previous).play(move).left.map(PlaceError(request, previous, _))).map: nextGame =>
          val moves = previous.moves :+ move
          val opening = appendMoveLog(previous, previous.opening, move)
          val state = OmokRoundState.fromGame(nextGame, moves, opening, previous.ai)
          val payload = OmokEvent.MovePayload(move, state)
          PlaceAccepted(
            previous = previous,
            state = state,
            move = move,
            payload = payload,
            event = OmokEvent.Move(gameId, move, state),
            terminalStatus = state.terminalStatus
          )

  private def candidatePreview(
      gameId: GameId,
      previous: OmokRoundState,
      move: OmokMove
  ): Option[Either[PlaceError, PlaceAccepted]] =
    Option.when(previous.ruleSet == RuleSet.Taraguchi10 && previous.opening.candidateMode):
      val request = PlaceRequest(gameId, move.pos, ruleSet = previous.ruleSet)
      if !previous.opening.candidateSelection && previous.opening.candidateMoves.size < OmokOpeningState.CandidateTarget then
        validateCandidate(previous, move, request).map: opening =>
          val state = previous.copy(opening = opening)
          PlaceAccepted(previous, state, move, OmokEvent.MovePayload(move, state), OmokEvent.Move(gameId, move, state), None)
      else selectCandidate(gameId, previous, move, request)

  private def validateCandidate(
      previous: OmokRoundState,
      move: OmokMove,
      request: PlaceRequest
  ): Either[PlaceError, OmokOpeningState] =
    if !previous.position.board.isEmpty(move.pos) then Left(PlaceError(request, previous, OmokMoveError.Occupied))
    else if previous.opening.candidateMoves.exists(_.pos == move.pos) then Left(PlaceError(request, previous, OmokMoveError.Occupied))
    else
      val key = OmokOpeningState.patternSymmetryKey(previous.moves, move)
      if previous.opening.candidateMoves.exists(candidate => OmokOpeningState.patternSymmetryKey(previous.moves, candidate) == key)
      then Left(PlaceError(request, previous, OmokMoveError.Forbidden(lila.omok.ForbiddenReason.DoubleThree)))
      else
        val nextCandidates = previous.opening.candidateMoves :+ move
        Right(
          previous.opening
            .copy(candidateMoves = nextCandidates, candidateSelection = nextCandidates.size >= OmokOpeningState.CandidateTarget)
            .log(OmokOpeningState.candidateProposalEntry(previous.activeSeat, move, nextCandidates.size))
        )

  private def selectCandidate(
      gameId: GameId,
      previous: OmokRoundState,
      move: OmokMove,
      request: PlaceRequest
  ): Either[PlaceError, PlaceAccepted] =
    if !previous.opening.candidateMoves.exists(_.pos == move.pos) then Left(PlaceError(request, previous, OmokMoveError.OutOfBounds))
    else
      val finalMove = move
      val finalOpening = appendMoveLog(
        previous,
        previous.opening
          .copy(candidateMode = false, candidateSelection = false, candidateMoves = Vector.empty, finalSwapUsed = true)
          .log(OmokOpeningState.candidateSelectEntry(previous.activeSeat, finalMove)),
        finalMove,
        actor = Some(previous.opening.seatFor(OmokColor.Black)),
        suffix = Some("selected from candidates")
      )
      currentGame(previous).play(finalMove).left.map(PlaceError(request, previous, _)).map: nextGame =>
        val moves = previous.moves :+ finalMove
        val state = OmokRoundState.fromGame(nextGame, moves, finalOpening, previous.ai)
        PlaceAccepted(
          previous,
          state,
          finalMove,
          OmokEvent.MovePayload(finalMove, state),
          OmokEvent.Move(gameId, finalMove, state),
          state.terminalStatus
        )

  private def appendMoveLog(
      previous: OmokRoundState,
      opening: OmokOpeningState,
      move: OmokMove,
      actor: Option[OmokColor] = None,
      suffix: Option[String] = None
  ): OmokOpeningState =
    if previous.ruleSet == RuleSet.Taraguchi10 && previous.position.ply < 6 then
      opening.log(OmokOpeningState.moveEntry(previous.position.ply + 1, actor.getOrElse(previous.activeSeat), move, suffix))
    else opening

  private def currentGame(state: OmokRoundState): OmokGame =
    Replay(OmokGame.initial(state.ruleSet), state.moves).toOption.getOrElse(OmokGame.initial(state.ruleSet))
