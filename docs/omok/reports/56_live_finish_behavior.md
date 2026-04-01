# Live Finish Behavior

Current `omok/mvp` behavior on the winning move:

- Live client: the socket still sends `omokMove` first, and that payload now includes terminal `status` / `winner` alongside the final `position`. The last stone appears immediately, and the Omok state block can show `Finished: ...` from the `omokMove` payload itself.
- Finish banner surface: `RoundAsyncActor` then runs the normal finisher path and emits generic `endData`, so the shared round result/status block still flips to the finished winner/draw state through the standard round UI path.

After reload:

- Finished omok state is retained in `OmokRoundRepo`, and `RoundApi.withOmok(...)` reboots `data.omok` from that retained state.
- The page reloads onto the same final board, with `omok.position`, `omok.status`, and `omok.winner` present again; the shared finish banner/result block still comes from persisted `game.status` / `game.winner`.
