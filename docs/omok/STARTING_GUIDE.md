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

For realtime-specific checks, open two isolated browser sessions and test:

- friend invite first-claimer flow
- live move propagation
- resign
- rematch

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

### Frontend change does not seem to apply

You probably changed source but did not rebuild the relevant UI bundle.

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
