package lila.round

private[round] object OmokCli:

  def handler(
      omokDemoSeed: OmokDemoSeed,
      omokStartScaffold: OmokStartScaffold
  ): PartialFunction[List[String], Fu[String]] =
    case "omok" :: "start" :: fullId :: rest =>
      val ruleSet = rest.headOption
      fuccess(
        omokStartScaffold
          .start(fullId, ruleSet)
          .fold(err => s"ERROR ${err.message}", _.message)
      )
    case "omok" :: "seed" :: gameId :: rest =>
      fuccess(omokDemoSeed.seed(gameId, rest))
    case "omok" :: "show" :: gameId :: Nil =>
      fuccess(omokDemoSeed.show(gameId))
    case "omok" :: "clear" :: gameId :: Nil =>
      fuccess(omokDemoSeed.clear(gameId))
