package lila.round

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal
import java.util.concurrent.atomic.AtomicBoolean

import lila.core.id.GameId
import lila.game.OmokGameSidecar
import lila.omok.{ Game as OmokGame, Move as OmokMove, Pos, RuleSet }

final case class OmokRoundState(
    position: lila.omok.PositionSnapshot,
    moves: Vector[OmokMove] = Vector.empty,
    terminalStatus: Option[lila.omok.Status] = None
):
  def boardSize: Int = Pos.Size
  def ruleSet: RuleSet = position.ruleSet
  def analyseDto =
    lila.omok.OmokAnalyseDto(
      position = Some(lila.omok.OmokPositionDto.fromPosition(position)),
      status = terminalStatus.map {
        case lila.omok.Status.Win(_) => "win"
        case lila.omok.Status.Draw   => "draw"
        case lila.omok.Status.Ongoing => "ongoing"
      },
      winner = terminalStatus.flatMap {
        case lila.omok.Status.Win(lila.omok.Color.Black) => Some("black")
        case lila.omok.Status.Win(lila.omok.Color.White) => Some("white")
        case _                                           => None
      }
    )

object OmokRoundState:
  private val center = Pos.unsafe(7, 7)
  private val centerMove = OmokMove(center)

  def initial(ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    if ruleSet == RuleSet.Renju then
      fromGame(OmokGame.initial(ruleSet).play(centerMove).toOption.get, Vector(centerMove))
    else fromGame(OmokGame.initial(ruleSet))

  def fromGame(
      game: OmokGame,
      moves: Vector[OmokMove] = Vector.empty
  ): OmokRoundState =
    OmokRoundState(
      position = lila.omok.PositionSnapshot.fromGame(game, moves),
      moves = moves,
      terminalStatus = Option.when(game.status != lila.omok.Status.Ongoing)(game.status)
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

  def get(gameId: GameId): Option[OmokRoundState] = states.get(gameId)

  def getOrInit(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    states.getOrElseUpdate(gameId, OmokRoundState.initial(ruleSet))

  def getOrInitStored(gameId: GameId, ruleSet: RuleSet = RuleSet.Renju): OmokRoundState =
    get(gameId).getOrElse:
      val initial = OmokRoundState.initial(ruleSet)
      storeSidecar(OmokStoredState.fromState(gameId, initial))
      states.putIfAbsent(gameId, initial).getOrElse(initial)

  def put(gameId: GameId, state: OmokRoundState): OmokRoundState =
    storeSidecar(OmokStoredState.fromState(gameId, state))
    putCacheOnly(gameId, state)

  def remove(gameId: GameId): Option[OmokRoundState] =
    // Clearing a round should also free the coalescing slot so a fresh hydrate can start immediately.
    hydrations.remove(gameId).foreach(_.cancelled.set(true))
    clearSidecar(gameId)
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

