package lila.round

private[round] object OmokCli:

  private def render(result: Either[OmokStartScaffoldError, OmokStartScaffoldResult]) =
    result.fold(err => s"ERROR ${err.message}", _.message)

  def handler(
      omokDemoSeed: OmokDemoSeed,
      omokStartScaffold: OmokStartScaffold
  )(using Executor): PartialFunction[List[String], Fu[String]] =
    case "omok" :: "start" :: Nil =>
      omokStartScaffold.startNew().map(render)
    case "omok" :: "start" :: raw :: Nil if OmokStartScaffold.parseRuleSet(raw).isDefined =>
      omokStartScaffold.startNew(Some(raw)).map(render)
    case "omok" :: "start" :: fullId :: Nil =>
      omokStartScaffold.startMessage(fullId, None)
    case "omok" :: "start" :: fullId :: ruleSet :: Nil =>
      omokStartScaffold.startMessage(fullId, Some(ruleSet))
    case "omok" :: "seed" :: gameId :: rest =>
      fuccess(omokDemoSeed.seed(gameId, rest))
    case "omok" :: "show" :: gameId :: Nil =>
      fuccess(omokDemoSeed.show(gameId))
    case "omok" :: "clear" :: gameId :: Nil =>
      fuccess(omokDemoSeed.clear(gameId))
