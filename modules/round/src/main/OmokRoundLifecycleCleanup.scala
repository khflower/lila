package lila.round

import lila.common.Bus
import lila.core.game.FinishGame
import lila.core.id.GameId
import lila.core.round.DeleteUnplayed

private[round] final class OmokRoundLifecycleCleanup(omokMovePlayer: OmokMovePlayer):

  private val finishSubscription = Bus.sub[FinishGame]:
    case FinishGame(game, _) => evict(game.id)

  private val deleteUnplayedSubscription = Bus.sub[DeleteUnplayed]:
    case DeleteUnplayed(gameId) => cleanup(gameId)

  private[round] def cleanup(gameId: GameId): Option[OmokRoundState] =
    omokMovePlayer.remove(gameId)

  private[round] def evict(gameId: GameId): Option[OmokRoundState] =
    omokMovePlayer.evict(gameId)

  private[round] def unsubscribe(): Unit =
    Bus.unsub[FinishGame](finishSubscription)
    Bus.unsub[DeleteUnplayed](deleteUnplayedSubscription)
