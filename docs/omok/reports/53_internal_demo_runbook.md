# Internal Demo Runbook

## Objective

Show the current `omok/mvp` branch as a believable internal prototype:
- seed a round with `bin/omok-demo`
- open an omok round page with visible board/state
- make one or two legal moves through the live path
- reach a finished state if desired
- show that reload/reconnect preserves the same finished omok snapshot

## Pre-Demo Setup

1. Confirm the local/internal CLI path works:

```text
bin/omok-demo --help
```

2. Seed a known round id before opening any tabs:

```text
bin/omok-demo seed demo1234 renju H8 A1 I8
```

3. Verify state exists:

```text
bin/omok-demo show demo1234
```

4. Open:
- black player page
- white player page
- watcher page

## Safest Demo Script

### A. Opening

Say:
- "This branch now boots an omok-specific round shell on top of lila round pages."
- "The board, current turn, and last move come from the omok sidecar, not from chessground state."

Show:
- visible omok overlay board
- omok state card
- watcher page in the same position

### B. Live move

Do:
- on the currently allowed side, click one empty point

Say:
- "This click sends `place`, the backend applies it through `OmokMovePlayer`, then emits `omokMove`."

Watch for:
- outbound `place`
- inbound `omokMove`
- visible state change on board/state card

### C. Finish story

If the seed was chosen so the next move wins, show:
- final move lands
- state changes to finished/winner
- normal round finish path follows

Say:
- "The winning move now carries terminal omok status/winner, and finished omok state is retained for reload."

### D. Reload story

Hard refresh player or watcher page.

Say:
- "Reload/reconnect uses retained omok state, so the page comes back on the same final board with the same result metadata."

## Fallback Steps If Something Flakes

### If seeding fails
- verify `bin/cli` can reach the internal CLI transport
- verify `LILA_CLI_TOKEN_DEV`
- rerun `bin/omok-demo seed <gameId>`

### If live redraw flakes
- show the inbound `omokMove` frame in DevTools
- hard refresh the tabs
- confirm the same board/result comes back from retained `data.omok`

### If the round id is in a bad state

```text
bin/omok-demo clear demo1234
bin/omok-demo seed demo1234 renju H8 A1 I8
```

Then refresh the tabs.

## Best Short Demo Version

If you only have 2?3 minutes:
1. `bin/omok-demo seed demo1234 renju H8 A1 I8`
2. open player + watcher tabs
3. make one legal move
4. show finished/reload behavior if available
5. explain that the remaining work is productized start flow and broader site integration, not the core live loop
