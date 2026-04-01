The workspace tooling is blocked at the sandbox layer: both shell commands and `apply_patch` fail immediately with `bwrap: No permissions to create a new namespace`, so I can’t actually write `/kh_code/lila/docs/omok/reports/08_dev_runtime_bootstrap.md` from this session. Report content follows in markdown so it can be dropped into that path once the sandbox is fixed.

```markdown
# Dev Runtime Bootstrap: minimum local omok prototype stack

## 1. Required services

1. MongoDB
   - `conf/base.conf` points the app at `mongodb://127.0.0.1:27017?appName=lila`.
   - Multiple subsystems inherit the same Mongo URI.
   - Treat Mongo as required for any real boot.

2. Redis
   - `conf/base.conf` sets `redis.uri = "redis://127.0.0.1"`.
   - Redis is wired into `socket` and `fishnet`, and upstream `lila-ws` describes runtime flow as `lila <-> redis <-> lila-ws <-> websocket <-> client`.
   - Treat Redis as required.

3. lila server process under sbt
   - `lila.sh` is the dev entrypoint.
   - It copies `.sbtopts.default` and `conf/application.conf.default` if absent, verifies Java, warns if a JRE is used, and launches `sbt`.
   - Local startup remains `./lila.sh` then `run`.

4. Toolchain needed for first real dev boot
   - JDK 21, not just a JRE.
   - sbt.
   - Node 24+ and pnpm 10 once frontend assets need rebuilding.

## 2. Optional services

1. `lila-ws` websocket server
   - Default config points `net.socket.domains` at `localhost:9664`.
   - `conf/application.conf.default` says setting `net.socket.domains = []` disables websockets and removes the red disconnect nag.
   - Optional for first boot.

2. External engine service
   - `externalEngine.endpoint = "http://localhost:9666"`.
   - Optional unless omok work depends on engine integration.

3. Fishnet clients
   - Analysis-specific.
   - Optional for prototype boot.

4. Elasticsearch
   - Search/indexing concern.
   - Optional.

5. Picfit
   - Configured on `127.0.0.1:3001`.
   - Optional unless media/image flows matter.

6. SMTP/mail
   - Mail is mocked in config.
   - Optional.

7. Monitoring stack
   - Prometheus/InfluxDB show up in upstream docker bootstrap.
   - Optional.

8. Reverse proxy
   - Production concern, not needed for local direct access.

9. Seed data
   - Helpful, but not required just to prove the app boots.

## 3. Likely blockers to first boot

1. MongoDB and Redis are not running locally.
2. Using a JRE instead of a full JDK 21 install.
3. Expecting websockets to be in-process; they are a separate service by default.
4. Old or missing Node/pnpm when assets need rebuilding.
5. Assuming generated `conf/application.conf` is a “minimal mode”; it still inherits all of `base.conf`.
6. Resource cost: upstream docker bootstrap warns a full build can want about 12GB RAM.
7. Routes/assets workflow friction:
   - `conf/routes` edits need `./lila.sh playRoutes`.
   - UI work usually needs a pnpm watch process.

## 4. Minimal prototype runtime recommendation

Recommended minimum stack:

1. MongoDB on `127.0.0.1:27017`
2. Redis on `127.0.0.1`
3. lila itself via `./lila.sh` then `run`

Recommended local config simplification in `conf/application.conf`:

```hocon
include "base"
include "version"

net.socket.domains = []
user.password.bpass.secret = "9qEYN0ThHer1KWLNekA76Q=="
```

Recommendation:
- Keep MongoDB and Redis.
- Disable websockets locally at first and defer `lila-ws`.
- Skip Elasticsearch, fishnet, external engine, picfit, mail, monitoring, and reverse proxy until the omok core works.
- Add Node 24 + pnpm 10 when frontend edits or asset rebuilds become necessary.

Bottom line:
- Minimum runtime for a local omok prototype is `MongoDB + Redis + lila under sbt on JDK 21`.
- The first deliberate simplification should be `net.socket.domains = []` so the prototype can boot without `lila-ws`.
```
