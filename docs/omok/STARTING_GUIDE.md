# Omok starter guide

This guide is the practical starting point for running the Omok-enabled lila fork from a fresh clone.

It is intentionally focused on the current Omok workflow in this repo, not on the full upstream lichess production stack.

## What you get

With the steps below you can:

- boot MongoDB and Redis locally
- build the frontend assets
- run the main Play app on `http://localhost:9663`
- optionally run `lila-ws` on `http://localhost:9664` for realtime Omok play
- open `/ko` and verify the Omok lobby and round flow

This guide also reflects the current Omok fork state:

- `/ko` modal-close fixes are expected to work with real clicks
- `/omok/solo` is the unified solo-board entry point
- Omok AI rounds expose local Rapfi engine settings in the round UI
- browser Rapfi defaults are conservative, Renju-based, and capped at 30 seconds think time
- `public/omok/` runtime assets, opening-guide data, and browser Rapfi bundles are now committed in this branch instead of being left only in a local runtime tree

## Prerequisites

Minimum recommended toolchain:

- Git
- JDK 21
- `sbt`
- Node `24.x`
- `pnpm` `10.x`
- Docker Desktop or Docker Engine
- Bash shell available on your machine

Notes:

- `package.json` currently expects Node `>=24` and `pnpm@10`.
- On Windows, WSL2 + Ubuntu is the easiest way to run the Scala app and `lila-ws` reliably.
- If you only want to prove the app boots, you can start without `lila-ws` by disabling socket domains.

## 1. Clone and install dependencies

```bash
git clone <your-fork-url>
cd lila-work
pnpm install
```

If `conf/application.conf` does not exist yet, `./lila.sh` will copy it from `conf/application.conf.default` automatically.

## 2. Start MongoDB and Redis

This repo now includes a small local compose file for the minimum Omok stack:

```bash
docker compose -f docker-compose.omok-dev.yml up -d
```

Default local ports:

- MongoDB: `127.0.0.1:27017`
- Redis: `127.0.0.1:6379`

These match the defaults in `conf/base.conf`.

## 3. Choose your boot mode

### Option A, fastest first boot, no websocket server

Use this if you only want to prove the app compiles and serves pages.

Create or edit `conf/application.conf` like this:

```hocon
include "base"
include "version"

net.socket.domains = []
user.password.bpass.secret = "9qEYN0ThHer1KWLNekA76Q=="
```

This disables websocket usage and removes the red disconnect warning, which is useful for a first boot.

### Option B, full Omok realtime local setup

Use this if you want `/ko`, friend invites, AI rounds, or multi-tab live Omok flows to behave properly.

Set `conf/application.conf` to a local value such as:

```hocon
include "base"
include "version"

user.password.bpass.secret = "9qEYN0ThHer1KWLNekA76Q=="

net.domain = "localhost:9663"
net.socket.domains = ["localhost:9664"]
net.asset.domain = ${net.domain}
net.asset.base_url = "http://"${net.asset.domain}
net.base_url = "http://"${net.domain}
```

Do not blindly reuse a checked-in tunnel hostname from another machine.

## 4. Build the frontend assets

From the repo root:

```bash
bash ui/build
```

For Omok-only frontend iteration, the narrower builds are often enough:

```bash
bash ui/build round -n
bash ui/build lobby -n
```

If you change Sass only:

```bash
bash ui/build round --sass -n
```

## 5. Start the main app

```bash
./lila.sh
```

Then, inside the sbt prompt:

```text
run
```

Expected local app URL:

```text
http://localhost:9663
```

## 6. Start `lila-ws` for realtime Omok

If you chose the full realtime path, run the websocket server separately.

Clone it next to your main checkout if you do not already have it:

```bash
git clone https://github.com/lichess-org/lila-ws.git ../lila-ws
```

Then start it:

```bash
cd ../lila-ws
sbt run
```

Expected local ws URL:

```text
http://localhost:9664
```

## 7. Verify the server manually

Once both services are up:

1. Open `http://localhost:9663/ko`
2. Confirm the Omok lobby renders
3. Click these three buttons and confirm the setup modal opens each time:
   - `방만들기`
   - `초대하기`
   - `ai랑 두기`
4. Confirm the rules include:
   - `Taraguchi-10`
   - `Renju`
   - `Freestyle`
5. Start an AI game and verify the round page loads
6. Open the Omok round-side Rapfi settings panel and confirm:
   - the settings toggle renders
   - default think time is at or below `30000ms`
   - default thinking rule is `Renju`
   - changing think time / hash size / threads persists after a reload

For realtime-specific checks, open two isolated browser sessions and test:

- friend invite first-claimer flow
- live move propagation
- resign
- rematch
- timed hook room start, where the first player's clock should begin ticking as soon as the second player joins
- spectator discoverability, where a fresh `/ko` page should show a `진행 중인 오목 경기` box while a live Omok game exists

## 8. Optional, rough public exposure

The current Omok demo flow used a temporary reverse proxy plus Cloudflare Quick Tunnel.

That setup is intentionally rough and is not required for a normal local dev bring-up. If you need public exposure, make sure:

- app traffic reaches `9663`
- websocket traffic reaches `9664`
- origin / CSRF settings are updated for the public hostname
- both app and ws are checked independently, because `/ko` can load while realtime is dead

## 9. Common traps

### `9663` is up but gameplay is broken

That usually means `lila-ws` on `9664` is down or misrouted.

### `/ko` loads but buttons or modals behave strangely

Check asset build and manifest state first. This Omok fork has had several live regressions caused by stale or mismatched compiled assets.

If the live runtime already has the right manifest entries and the right modal CSS, but a human still reports the 3 buttons doing nothing, do not stop there. This has repeatedly turned out to be stale compiled JS still being served through the public app shell. In that case, bump `compiledAssetBust` in `modules/web/src/main/ui/layout.scala`, restart the app on `9663`, and then re-run a real click check.

### `진행 중인 오목 경기` appears in HTML but not in the real page

The lobby client re-renders `.lobby__table` after boot. Do not rely on a server-rendered card that lives inside that subtree. Keep the preload data, but mount the visible ongoing-game box in a server-owned area outside `.lobby__table`, then verify the real page after boot.

### Frontend change does not seem to apply

You probably changed source but did not rebuild the relevant UI bundle.

For the public tunnel path, also consider stale page-shell asset busting. A rebuilt bundle plus correct manifest can still look broken to users until the app serves a fresh `?v=` asset version.

### Omok round page loads but live Rapfi streaming still looks static

The current round UI is wired to accept partial Rapfi analysis updates, but the browser Rapfi worker path still needs to emit intermediate analysis info for truly live streaming.

In live verification so far, the worker has reliably returned final move output and engine capability info, but not the depth/eval/winrate stream the UI would need for continuous repainting.

So if the settings panel is present but the analysis view only updates at the end, treat that as a worker/protocol limitation first, not immediately as a round-view rendering bug.

### Commercial use or redistribution

If you are shipping this Omok fork commercially, do a license review before bundling or redistributing Rapfi assets, Renju tooling, opening data, or other imported Omok resources.

Useful internal references:

- `docs/omok/reports/09_rapfi_feasibility.md`
- `docs/omok/reports/10_renlib_vcf_feasibility.md`
- `docs/omok/reports/11_lightweight_omok_oss.md`

### Public tunnel config leaked into local config

If `conf/application.conf` still points at an old Cloudflare hostname, reset it to localhost values before debugging local startup.

## 10. Important files for Omok maintainers

Main Omok paths in this repo:

- `app/controllers/Setup.scala`
- `app/controllers/Round.scala`
- `app/views/omokPages.scala`
- `modules/round/src/main/OmokMovePlayer.scala`
- `modules/round/src/main/OmokRoundRepo.scala`
- `modules/round/src/main/Rematcher.scala`
- `ui/round/src/ctrl.ts`
- `ui/round/src/omokRapfi.ts`
- `ui/round/src/view/omokPlaceholder.ts`
- `public/omok/`

## 11. Recommended first commands for a new maintainer

```bash
docker compose -f docker-compose.omok-dev.yml up -d
pnpm install
bash ui/build
./lila.sh
```

Then separately:

```bash
cd ../lila-ws
sbt run
```

After that, open:

```text
http://localhost:9663/ko
```

If that page works and the setup modal opens, you have a real starting point.
