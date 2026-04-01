# Omok Internal Demo Runbook

## Scope

This is the presenter version of the internal playtest flow.

It is based on the current `omok/mvp` branch plus the earlier playtest checklist:

- keep the demo on one manually seeded round;
- show player + watcher boot, one legal live move, and reload/resync recovery;
- do not claim production-ready round creation, finish handling, or polished replay surfaces.

## Current Demo-Safe Story

What is safe to show on this branch:

- player and watcher pages boot into omok mode when `OmokRoundRepo` already has state for the round;
- the page renders a visible 15x15 board plus the omok state pill;
- a legal `place` goes through the round socket path and broadcasts `omokMove`;
- the frontend now redraws from the live `{ move, position }` payload;
- reload still rebuilds from the same `data.omok.position` snapshot;
- an invalid follow-up click resyncs the sender back to server state.

What is still not demo-safe:

- normal omok game creation or start;
- a polished finish/winner story;
- repeated ad hoc resets without reseeding or clearing the cached state.

## Setup

1. Start the normal local stack used by the playtest checklist.
   - MongoDB on `127.0.0.1:27017`
   - Redis on `127.0.0.1`
   - lila via `./lila.sh` then `run`
   - `lila-ws` on `localhost:9664`
   - optional while iterating UI: `ui/build -w`

2. Prepare one fresh ordinary live round between two local test accounts.
   - Prefer a casual game with a long clock so the demo is not about clock pressure.
   - Save the black player URL, white player URL, and watcher URL before the audience part starts.

3. Seed omok state for that existing `GameId` inside the running server JVM.
   - Important: `OmokRoundRepo` is in-memory. Seed the repo inside the live app process, not in a separate standalone console.
   - In the snippets below, `env` means your live `lila.app.Env` handle for the running server process.
   - Empty-board seed:

```scala
import lila.core.id.GameId
import lila.round.OmokRoundState

val gameId = GameId("replace_me")
env.round.omokRoundRepo.put(gameId, OmokRoundState.initial())
```

   - Reset between takes if needed:

```scala
import lila.core.id.GameId

env.round.omokMovePlayer.remove(GameId("replace_me"))
```

   - If you want a preplayed seed instead of an empty board:

```scala
import lila.core.id.GameId
import lila.omok.{ Game as OmokGame, Move as OmokMove, Pos, PositionSnapshot, Replay, RuleSet }
import lila.round.OmokRoundState

val gameId = GameId("replace_me")
val moves = Vector(
  OmokMove(Pos.unsafe(7, 7)),
  OmokMove(Pos.unsafe(0, 0))
)
val game = Replay(OmokGame.initial(RuleSet.Renju), moves).toOption.get
env.round.omokRoundRepo.put(gameId, OmokRoundState(PositionSnapshot.fromGame(game, moves), moves))
```

4. Open three tabs only after seeding.
   - black player
   - white player
   - watcher

5. Keep DevTools open on black and watcher tabs.
   - preserve websocket frames
   - keep console visible

## What To Say

- "This is the current live omok branch running on the existing lila round shell."
- "The round itself is still seeded manually, but once booted it uses the real round socket path."
- "I’m showing the narrow MVP loop: board boot, live place, watcher sync, and reload/resync recovery."

## Safest Demo Path

1. Start on the black player tab.
   - Show the visible 15x15 board and the omok state pill.
   - Say that both player and watcher pages boot from the same `data.omok.position`.

2. Flip to the watcher tab briefly.
   - Confirm the same empty board and same state pill values are visible there.

3. Back on black, play one obvious legal first move.
   - Use `H8`.
   - Keep the watcher tab visible soon after so the audience sees the same stone appear there.

4. Call out the live path, not the polish.
   - Mention that the accepted click sent `place` and the round broadcast `omokMove`.
   - Do not spend time on chess-side surfaces around the board.

5. Prove recovery immediately after the first accepted move.
   - Hard refresh the watcher tab.
   - If needed, hard refresh the black tab too.
   - Show that both rebuild to the same one-stone board from server state.

6. Show the reject/resync safety net.
   - Stay on black after `H8`.
   - Click another empty point such as `I8`.
   - Explain that it is now white's turn, so black is rejected and the sender resyncs back to the authoritative board.

7. Stop there unless the room wants one more move.
   - Optional extension: switch to white and place `A1`.
   - Do not rely on a finish sequence for the demo.

## Fallback Recovery

If live redraw flakes after a legal move:

- hard refresh the affected player or watcher tab;
- if there is any doubt, refresh both black and watcher;
- the recovery path is the boot JSON from `data.omok.position`, so the page should come back to the latest accepted state.

If the sender gets out of sync after a rejected click:

- wait for automatic resync;
- if it does not visibly recover, hard refresh that tab once.

If a page comes back without the omok overlay:

- the repo entry is missing or you opened the page before seeding;
- reseed the same `GameId` in the running JVM and reopen the tabs.

If you need a clean second take:

- clear the cached omok state with `env.round.omokMovePlayer.remove(gameId)` or use a new fresh round;
- reseed before opening the tabs again.

## Presenter Notes

- Keep the claim small: this is a live omok round loop, not a production-complete omok product.
- Focus the audience on board boot, one accepted move, watcher sync, and reload/resync recovery.
- Avoid demoing creation flow, finish flow, rematch, or any chess-specific side panels as if they were already ported.
