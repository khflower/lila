# Omok Round Runtime Follow-ups

## Scope

This checkpoint covers the next integration wave after the currently landed controller, actor, and socket seams on `omok/mvp`.

It is based on the branch as it exists now, not on the earlier pre-implementation checklist.

## Verified Current State

- `build.sbt` already makes `round` depend on `omok`.
- `modules/round/src/main/OmokRoundRepo.scala` exists and is wired from `modules/round/src/main/Env.scala`.
- `modules/round/src/main/RoundSocket.scala` already parses `r/place` into `Protocol.In.PlayerPlace(...)`.
- `modules/round/src/main/actorApi.scala` already defines `HumanPlace`.
- `modules/round/src/main/RoundAsyncActor.scala` still ignores `HumanPlace`: it logs, resyncs the player, and never applies an omok move.
- `modules/round/src/main/OmokEvent.scala` exists as a draft payload helper, but the actor publish path still only accepts `lila.core.game.Event`.
- `ui/round/src/interfaces.ts`, `ui/round/src/socket.ts`, and `ui/round/src/ctrl.ts` already know about `place`, `omokMove`, and `apiOmokMove`.
- `ui/round/src/omok.ts` is only a placeholder boot shim. `ui/round/src/round.ts` still falls through to `RoundController`, and `ui/round/src/view/main.ts` still renders `Chessground`.
- `modules/api/src/main/RoundApi.scala` still emits chess `steps` for round boot and only reserves `omok` for analyse payloads.
- `modules/round/src/main/ui/RoundUi.scala` plus `app/views/round/player.scala` and `app/views/round/watcher.scala` still preload a chess board on the server.

## Next Tasks

1. Add one authoritative omok round detector and seed path.
   - Decide what makes a `GameId` an omok round in MVP and where its initial `OmokRoundState` is created.
   - Why next: every later step needs a single source of truth for whether round boot should enter omok mode at all.

2. Emit real `data.omok` from round player and watcher JSON.
   - Extend `modules/api/src/main/RoundApi.scala` to append live omok boot data from `OmokRoundRepo` instead of the analyse-only empty namespace.
   - Why next: the frontend cannot branch into a real omok runtime until round boot carries actual omok state.

3. Stop server-preloading chess UI for omok rounds.
   - Add an omok preload branch in `modules/round/src/main/ui/RoundUi.scala` and thread it through `app/views/round/player.scala` and `app/views/round/watcher.scala`.
   - Why next: once `data.omok` exists, the HTML shell must stop hydrating a chessboard against non-chess state.

4. Replace the placeholder frontend boot with a minimal omok round runtime.
   - Turn `ui/round/src/omok.ts` into a real board/controller entry that renders from `data.omok`, sends `place`, and consumes `omokMove`, while reusing the existing round page, chat, clock, and socket shell.
   - Why next: the server-side executor should target a real client path, not the current chess controller plus DOM marker placeholder.

5. Implement the backend `HumanPlace` executor.
   - Add the omok move application path in round: load or seed state, validate turn and legality, apply `lila.omok.Game.play(...)`, persist the new snapshot, and map terminal status/clock effects.
   - Why next: the socket ingress already exists; this is the first missing step that turns a `place` packet into state.

6. Promote `OmokEvent` into the normal round publish path and emit `omokMove`.
   - Make the payload implement `lila.core.game.Event` and publish it after a successful place instead of forcing resync-only behavior.
   - Why next: once moves apply on the server, clients need an incremental event path to stay in sync without full reloads.

7. Make reload, watcher reconnect, and round cleanup depend on omok state.
   - Ensure reload paths rebuild from `data.omok`, and clear `OmokRoundRepo` entries on finish, abort, expiry, or actor teardown.
   - Why next: this closes the MVP loop after live play works and prevents stale in-memory omok state from leaking across sessions.

## Recommended Wave Boundary

Keep this wave narrow:

- identify omok rounds reliably;
- boot them with real `data.omok`;
- render a non-chess round board;
- accept `place`;
- publish `omokMove`;
- reconnect and clean up correctly.

Do not pull SAN/FEN replay, premove, promotion, crazyhouse, or broader persistence redesign into the same wave.
