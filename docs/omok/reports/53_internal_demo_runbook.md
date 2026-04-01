# Internal Demo Runbook

## Objective

Show the current `omok/mvp` branch as a believable internal prototype:
- start an omok round from one internal entrypoint
- open an omok round page with visible board/state
- make one or two legal moves through the live path
- reach a finished state if desired
- show that reload/reconnect preserves the same finished omok snapshot

## Pre-Demo Setup

1. Pick one real active player `fullId` for the round you want to use.

2. Start omok on that round through either entrypoint:

```text
bin/cli omok start <fullId> [renju|freestyle]
```

or open:

```text
/dev/omok/start/<fullId>?ruleSet=renju
```

3. If you want a pre-seeded near-finish board instead of a blank start, the older seed helper still works:

```text
bin/omok-demo --help
bin/omok-demo seed demo1234 renju H8 A1 I8
```

4. Verify state exists:

```text
bin/omok-demo show demo1234
```

5. Open:
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
- verify the operator account has dev CLI permission
- rerun `bin/cli omok start <fullId>`
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
1. open `/dev/omok/start/<fullId>?ruleSet=renju`
2. open a watcher tab for the same game
3. make one legal move
4. show finished/reload behavior if available
5. explain that the remaining work is productized challenge/lobby integration, not the core live loop
