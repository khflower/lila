package lila.round

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal
import java.util.concurrent.atomic.AtomicBoolean

import lila.core.id.GameId
import lila.game.OmokGameSidecar
import lila.omok.{ Color as OmokColor, Game as OmokGame, Move as OmokMove, OmokAiConfig, OmokMoveDto, OmokOpeningDto, Pos, RuleSet }

final case class OmokRoundState(
    position: lila.omok.PositionSnapshot,
    moves: Vector[OmokMove] = Vector.empty,
    terminalStatus: Option[lila.omok.Status] = None,
    opening: OmokOpeningState = OmokOpeningState(),
    ai: Option[OmokAiConfig] = None
):
  def boardSize: Int = Pos.Size
  def ruleSet: RuleSet = position.ruleSet
  def isTaraguchi: Boolean = ruleSet == RuleSet.Taraguchi10
  def activeSeat: OmokColor = opening.activeSeat(position)
  def canSwap: Boolean = opening.canSwap(position)
  def canStartCandidates: Boolean = opening.canStartCandidates(position)
  def positionDto: lila.omok.OmokPositionDto =
    val base = lila.omok.OmokPositionDto.fromPosition(position)
    if isTaraguchi then base.copy(opening = Some(openingDto))
    else base
  def analyseDto =
    lila.omok.OmokAnalyseDto(
      position = Some(positionDto),
      status = terminalStatus.map {
        case lila.omok.Status.Win(_) => "win"
        case lila.omok.Status.Draw   => "draw"
        case lila.omok.Status.Ongoing => "ongoing"
      },
      winner = terminalStatus.flatMap {
        case lila.omok.Status.Win(lila.omok.Color.Black) => Some("black")
        case lila.omok.Status.Win(lila.omok.Color.White) => Some("white")
        case _                                           => None
      },
      ai = ai
    )

  private def openingDto: OmokOpeningDto =
    OmokOpeningDto(
      activeSeat = colorKey(activeSeat),
      canSwap = canSwap,
      canStartCandidates = canStartCandidates,
      candidateMode = opening.candidateMode,
      candidateSelection = opening.candidateSelection,
      forceSimpleFifth = opening.forceSimpleFifth,
      candidateCount = opening.candidateMoves.size,
      candidateTarget = OmokOpeningState.CandidateTarget,
      candidates = opening.candidateMoves.map(OmokMoveDto.fromMove),
      history = opening.history,
      rangeRadius = opening.rangeRadius(position),
      instruction = opening.instruction(position)
    )

  private def colorKey(color: OmokColor): String =
    color match
      case OmokColor.Black => "black"
      case OmokColor.White => "white"

final case class OmokOpeningState(
    swapCount: Int = 0,
    lastSwapPly: Option[Int] = None,
    forceSimpleFifth: Boolean = false,
    finalSwapUsed: Boolean = false,
    candidateMode: Boolean = false,
    candidateSelection: Boolean = false,
    candidateMoves: Vector[OmokMove] = Vector.empty,
    history: Vector[String] = Vector.empty
):
  def swapped: Boolean = swapCount % 2 == 1

  def log(entry: String): OmokOpeningState =
    copy(history = history :+ entry)

  def logAll(entries: Vector[String]): OmokOpeningState =
    copy(history = history ++ entries)

  def seatFor(stoneColor: OmokColor): OmokColor =
    if swapped then stoneColor.other else stoneColor

  def activeSeat(position: lila.omok.PositionSnapshot): OmokColor =
    if position.ruleSet == RuleSet.Taraguchi10 && candidateMode && (candidateSelection || candidateMoves.size >= OmokOpeningState.CandidateTarget) then
      seatFor(OmokColor.White)
    else seatFor(position.turn)

  def canSwap(position: lila.omok.PositionSnapshot): Boolean =
    position.ruleSet == RuleSet.Taraguchi10 &&
      !candidateMode &&
      position.status == lila.omok.Status.Ongoing &&
      !lastSwapPly.contains(position.ply) &&
      (
        position.ply == 1 ||
          position.ply == 2 ||
          position.ply == 3 ||
          (position.ply == 4 && !forceSimpleFifth) ||
          (position.ply == 5 && !finalSwapUsed)
      )

  def canStartCandidates(position: lila.omok.PositionSnapshot): Boolean =
    position.ruleSet == RuleSet.Taraguchi10 &&
      position.status == lila.omok.Status.Ongoing &&
      position.ply == 4 &&
      !forceSimpleFifth &&
      !candidateMode &&
      candidateMoves.isEmpty

  def rangeRadius(position: lila.omok.PositionSnapshot): Option[Int] =
    Option.when(position.ruleSet == RuleSet.Taraguchi10 && !candidateMode):
      position.ply match
        case 1 => 1
        case 2 => 2
        case 3 => 3
        case 4 => 4
        case _ => -1
    .filter(_ >= 0)

  def instruction(position: lila.omok.PositionSnapshot): String =
    val seat = activeSeat(position) match
      case OmokColor.Black => "Black"
      case OmokColor.White => "White"
    if position.ruleSet != RuleSet.Taraguchi10 then ""
    else if candidateMode && !candidateSelection && candidateMoves.size < OmokOpeningState.CandidateTarget then
      s"Black proposes ${OmokOpeningState.CandidateTarget} fifth-move candidates (${candidateMoves.size}/${OmokOpeningState.CandidateTarget})."
    else if candidateMode then "White selects one candidate, then White plays the sixth move."
    else if lastSwapPly.contains(position.ply) then
      position.ply match
        case 1 => s"$seat places the second move within 3x3 from H8."
        case 2 => s"$seat places the third move within 5x5 from H8."
        case 3 => s"$seat places the fourth move within 7x7 from H8."
        case 4 => s"$seat places the fifth move within 9x9 from H8."
        case 5 => "Continue under Renju rules."
        case _ => "Continue under Renju rules."
    else
      position.ply match
        case 1 => s"$seat may swap, or place the second move within 3x3 from H8."
        case 2 => s"$seat may swap, or place the third move within 5x5 from H8."
        case 3 => s"$seat may swap, or place the fourth move within 7x7 from H8."
        case 4 if forceSimpleFifth => "Black places the fifth move within 9x9 from H8."
        case 4 => s"$seat may swap, place one fifth move within 9x9, or propose 10 fifth-move candidates."
        case 5 if !finalSwapUsed => s"$seat may make the final swap, or continue Renju play."
        case _ => "Continue under Renju rules."

object OmokOpeningState:
  val CandidateTarget = 10
  val Center = Pos.unsafe(7, 7)

  def colorName(color: OmokColor): String = color match
    case OmokColor.Black => "Black"
    case OmokColor.White => "White"

  def moveEntry(moveNumber: Int, actor: OmokColor, move: OmokMove, suffix: Option[String] = None): String =
    s"$moveNumber. ${colorName(actor)} placed ${move.pos.key}${suffix.fold("")(s => s" ($s)")}"

  def swapEntry(actor: OmokColor, afterMove: Int, finalSwap: Boolean = false): String =
    if finalSwap then s"${colorName(actor)} made the final swap after move $afterMove"
    else s"${colorName(actor)} swapped after move $afterMove"

  def startCandidatesEntry(actor: OmokColor): String =
    s"${colorName(actor)} started the 10-candidate fifth-move proposal"

  def candidateProposalEntry(actor: OmokColor, move: OmokMove, index: Int): String =
    s"Candidate $index. ${colorName(actor)} proposed ${move.pos.key}"

  def candidateRemoveEntry(actor: OmokColor, move: OmokMove, remaining: Int): String =
    s"${colorName(actor)} removed candidate ${move.pos.key} ($remaining left)"

  def candidateSelectEntry(actor: OmokColor, move: OmokMove): String =
    s"${colorName(actor)} selected candidate ${move.pos.key}"

  def initialTaraguchiHistory(centerMove: OmokMove): Vector[String] =
    Vector(moveEntry(1, OmokColor.Black, centerMove, Some("fixed center")))

  def withinCenterRadius(pos: Pos, radius: Int): Boolean =
    math.max(math.abs(pos.row - Center.row), math.abs(pos.col - Center.col)) <= radius

  def symmetryKey(pos: Pos): (Int, Int) =
    val dx = pos.col - Center.col
    val dy = pos.row - Center.row
    (math.max(math.abs(dx), math.abs(dy)), math.min(math.abs(dx), math.abs(dy)))

  def patternSymmetryKey(moves: Vector[OmokMove], candidate: OmokMove): String =
    val stones = (moves :+ candidate).zipWithIndex.map:
      case (move, index) =>
        val color = if index % 2 == 0 then "b" else "w"
        val dx = move.pos.col - Center.col
        val dy = move.pos.row - Center.row
        (color, dx, dy)

    val transforms: Vector[(Int, Int) => (Int, Int)] = Vector(
      (x, y) => (x, y),
      (x, y) => (y, -x),
      (x, y) => (-x, -y),
      (x, y) => (-y, x),
      (x, y) => (-x, y),
      (x, y) => (x, -y),
      (x, y) => (y, x),
      (x, y) => (-y, -x)
    )

    transforms
      .map: transform =>
        stones
          .map:
            case (color, dx, dy) =>
              val (x, y) = transform(dx, dy)
              f"$color%s$x%+03d$y%+03d"
          .sorted
          .mkString("|")
      .min

object OmokRoundState:
  private val center = Pos.unsafe(7, 7)
  private val centerMove = OmokMove(center)

  def initial(ruleSet: RuleSet = RuleSet.Renju, ai: Option[OmokAiConfig] = None): OmokRoundState =
    if ruleSet == RuleSet.Renju || ruleSet == RuleSet.Taraguchi10 then
      fromGame(
        OmokGame.initial(ruleSet).play(centerMove).toOption.get,
        Vector(centerMove),
        opening =
          if ruleSet == RuleSet.Taraguchi10 then OmokOpeningState(history = OmokOpeningState.initialTaraguchiHistory(centerMove))
          else OmokOpeningState(),
        ai = ai
      )
    else fromGame(OmokGame.initial(ruleSet), ai = ai)

  def fromGame(
      game: OmokGame,
      moves: Vector[OmokMove] = Vector.empty,
      opening: OmokOpeningState = OmokOpeningState(),
      ai: Option[OmokAiConfig] = None
  ): OmokRoundState =
    OmokRoundState(
      position = lila.omok.PositionSnapshot.fromGame(game, moves),
      moves = moves,
      terminalStatus = Option.when(game.status != lila.omok.Status.Ongoing)(game.status),
      opening = opening,
      ai = ai
    )

object OmokRoundRepo:
  type StoreSidecar = OmokGameSidecar => Unit
  type ClearSidecar = GameId => Unit

  val noStoreSidecar: StoreSidecar = _ => ()
  val noClearSidecar: ClearSidecar = _ => ()

  def apply(): OmokRoundRepo = new OmokRoundRepo(noStoreSidecar, noClearSidecar)

  def apply(storeSidecar: StoreSidecar): OmokRoundRepo = new OmokRoundRepo(storeSidecar, noClearSidecar)

  def apply(storeSidecar: StoreSidecar, clearSidecar: ClearSidecar): OmokRoundRepo =
    new OmokRoundRepo(storeSidecar, clearSidecar)

final class OmokRoundRepo(
    private val storeSidecar: OmokRoundRepo.StoreSidecar,
    private val clearSidecar: OmokRoundRepo.ClearSidecar
):

  private type HydrationResult = Either[String, Option[OmokRoundState]]
  private case class InflightHydration(
      future: Future[HydrationResult],
      cancelled: AtomicBoolean = new AtomicBoolean(false)
  )

  private val states = TrieMap.empty[GameId, OmokRoundState]
  private val hydrations = TrieMap.empty[GameId, InflightHydration]
  private val knownRuleSets = TrieMap.empty[GameId, RuleSet]
  private val knownAiConfigs = TrieMap.empty[GameId, OmokAiConfig]

  def get(gameId: GameId): Option[OmokRoundState] = states.get(gameId)

  def ruleSetOf(gameId: GameId): Option[RuleSet] =
    get(gameId).map(_.ruleSet).orElse(knownRuleSets.get(gameId))

  def aiOf(gameId: GameId): Option[OmokAiConfig] =
    get(gameId).flatMap(_.ai).orElse(knownAiConfigs.get(gameId))

  def getOrInit(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    val state = states.getOrElseUpdate(gameId, OmokRoundState.initial(ruleSet))
    knownRuleSets.put(gameId, state.ruleSet)
    state

  def getOrInitStored(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    get(gameId).getOrElse:
      val initial = OmokRoundState.initial(ruleSet)
      storeSidecar(OmokStoredState.fromState(gameId, initial))
      val state = states.putIfAbsent(gameId, initial).getOrElse(initial)
      knownRuleSets.put(gameId, state.ruleSet)
      state

  def put(gameId: GameId, state: OmokRoundState): OmokRoundState =
    storeSidecar(OmokStoredState.fromState(gameId, state))
    putCacheOnly(gameId, state)

  def remove(gameId: GameId): Option[OmokRoundState] =
    // Clearing a round should also free the coalescing slot so a fresh hydrate can start immediately.
    hydrations.remove(gameId).foreach(_.cancelled.set(true))
    clearSidecar(gameId)
    knownRuleSets.remove(gameId)
    knownAiConfigs.remove(gameId)
    states.remove(gameId)

  def evict(gameId: GameId): Option[OmokRoundState] =
    // Drop only the in-memory state so finished rounds can still hydrate from the
    // durable omok sidecar later (for rematch, analysis, review, etc.).
    hydrations.remove(gameId).foreach(_.cancelled.set(true))
    states.remove(gameId)

  def update(gameId: GameId)(f: Option[OmokRoundState] => OmokRoundState): OmokRoundState =
    put(gameId, f(states.get(gameId)))

  def getStored(gameId: GameId): Option[OmokGameSidecar] =
    get(gameId).map(OmokStoredState.fromState(gameId, _))

  def snapshot: Map[GameId, OmokRoundState] = states.readOnlySnapshot().toMap

  def getOrHydrate(gameId: GameId)(loadStored: => Option[OmokGameSidecar]): Either[String, Option[OmokRoundState]] =
    get(gameId) match
      case some @ Some(_) => Right(some)
      case None =>
        loadStored match
          case Some(stored) => putStored(gameId, stored).map(Some(_))
          case None         => Right(None)

  def getOrHydrateAsync(gameId: GameId)(loadStored: => Fu[Option[OmokGameSidecar]])(using
      Executor
  ): Fu[HydrationResult] =
    get(gameId) match
      case some @ Some(_) => fuccess(Right(some))
      case None =>
        val placeholder = Promise[Either[String, Option[OmokRoundState]]]()
        val hydration = InflightHydration(placeholder.future)
        hydrations.putIfAbsent(gameId, hydration) match
          case Some(existing) => existing.future
          case None =>
            hydrate(gameId, hydration)(loadStored).onComplete: result =>
              placeholder.tryComplete(result)
              if hydrations.get(gameId).contains(hydration) then
                hydrations.remove(gameId)
            placeholder.future

  def putStored(gameId: GameId, stored: OmokGameSidecar): Either[String, OmokRoundState] =
    val normalized = stored.copy(_id = gameId)
    putStored(normalized)

  def putStored(stored: OmokGameSidecar): Either[String, OmokRoundState] =
    OmokStoredState.toRoundState(stored).map: state =>
      writeCanonicalSidecarIfNeeded(stored, state)
      putCacheOnly(stored._id, state)

  def buildState(game: OmokGame, moves: Vector[OmokMove]): OmokRoundState =
    OmokRoundState.fromGame(game, moves)

  private def putCacheOnly(gameId: GameId, state: OmokRoundState): OmokRoundState =
    knownRuleSets.put(gameId, state.ruleSet)
    state.ai match
      case Some(ai) => knownAiConfigs.put(gameId, ai)
      case None     => knownAiConfigs.remove(gameId)
    states.put(gameId, state)
    state

  private def writeCanonicalSidecarIfNeeded(stored: OmokGameSidecar, state: OmokRoundState): Unit =
    val canonical = OmokStoredState.fromState(stored._id, state)
    if canonical != stored then
      storeSidecar(canonical)

  private def hydrate(gameId: GameId, hydration: InflightHydration)(loadStored: => Fu[Option[OmokGameSidecar]])(using
      Executor
  ): Fu[HydrationResult] =
    try
      loadStored
        .map: stored =>
          // If lifecycle cleanup wins the race, ignore the late lookup result and preserve the current cache.
          if hydration.cancelled.get then cancelledResult(gameId)
          else getOrHydrate(gameId)(stored)
        .recover { case NonFatal(err) =>
          if hydration.cancelled.get then cancelledResult(gameId)
          else Left(renderHydrationError(gameId, err))
        }
    catch
      case NonFatal(err) =>
        fuccess:
          if hydration.cancelled.get then cancelledResult(gameId)
          else Left(renderHydrationError(gameId, err))

  private def cancelledResult(gameId: GameId): HydrationResult =
    Right(get(gameId))

  private def renderHydrationError(gameId: GameId, err: Throwable): String =
    val detail = Option(err.getMessage).filter(_.nonEmpty).getOrElse(err.getClass.getSimpleName)
    s"failed to load stored omok for $gameId: $detail"
