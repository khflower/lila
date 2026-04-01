# Dev CLI Omok Command Path

Current `omok/mvp` command flow from the shell wrapper to the real server-side handler is:

1. `bin/omok-demo`
   - validates `seed|show|clear`
   - then runs `exec "$CLI_BIN" omok "$@"`
2. `bin/cli`
   - joins argv into one command string with `COMMAND=$*`
   - `curl`s that string to `POST http://localhost:9663/run/cli`
3. `conf/routes`
   - `POST /run/cli controllers.Dev.command`
4. `app/controllers/Dev.scala`
   - `Dev.command` reads the raw text body and calls `runCommand(ctx.body.body)`
   - `runCommand` forwards to `env.api.cli.run(command.split(" ").toList)`
5. `modules/api/src/main/Cli.scala`
   - `Cli.run` publishes `CliCommand(args, ...)` on the shared CLI bus with `Bus.ask(...)`
6. `modules/round/src/main/Env.scala`
   - the round module registers the actual omok handler through `lila.common.Cli.handle`
   - current cases are:
     - `omok seed <gameId> ...` -> `omokDemoSeed.seed(gameId, rest)`
     - `omok show <gameId>` -> `omokDemoSeed.show(gameId)`
     - `omok clear <gameId>` -> `omokDemoSeed.clear(gameId)`

So on this branch there is no separate `OmokCli` class or direct controller wiring for `omok`. The real server-side command handler is the `lila.common.Cli.handle` subscription inside `modules/round/src/main/Env.scala`, and that subscription delegates to `OmokDemoSeed`.
