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
   - `runCommand` now forwards the raw command string to `env.api.cli.run(command)`
5. `modules/api/src/main/Cli.scala`
   - `Cli.run(command: String)` parses the raw string with `CliInput.parse(...)`
   - then `Cli.run(args: List[String])` publishes `CliCommand(args, ...)` on the shared CLI bus with `Bus.ask(...)`
6. `modules/round/src/main/OmokCli.scala`
   - the round module exposes `OmokCli.handler(omokDemoSeed)` as the actual `omok` command router
7. `modules/round/src/main/Env.scala`
   - registers that router through `lila.common.Cli.handle(OmokCli.handler(omokDemoSeed))`

So the current server-side `omok` command path is:

`bin/omok-demo` ? `bin/cli` ? `/run/cli` ? `controllers.Dev.command` ? `lila.api.Cli.run(command)` ? `CliInput.parse(...)` ? `CliCommand(args, ...)` ? `OmokCli.handler(...)` ? `OmokDemoSeed`

Supported routed shapes on this branch are:
- `omok seed <gameId> ...`
- `omok show <gameId>`
- `omok clear <gameId>`

This means the branch now has:
- a shell wrapper (`bin/omok-demo`)
- a raw command-string parser (`CliInput`)
- a dedicated round-level command router (`OmokCli`)
- the existing shared dev CLI transport (`/run/cli`)
