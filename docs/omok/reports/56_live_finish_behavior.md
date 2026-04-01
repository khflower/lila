# Live Finish Behavior

Current `omok/mvp` behavior on the winning move:

- Live client: the socket sends `omokMove` first, so the last stone appears on the board immediately. If that move wins, `RoundAsyncActor` then runs the normal finisher path, so the round UI receives generic `endData` and marks the game finished with the winner.
- Result surface: live omok UI therefore ends on the final board position, with finished state coming from generic round status/winner rather than from the `omokMove` payload itself.

After reload:

- Finished omok state is retained in `OmokRoundRepo`, and `RoundApi.withOmok(...)` reboots `data.omok` from that retained state.
- The page should reload onto the same final board, with `omok.position` still showing the winning move and `omok.status` / `omok.winner` present for the finished result.
