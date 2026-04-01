package lila.round

private[round] object OmokCli:

  def handler(omokDemoSeed: OmokDemoSeed): PartialFunction[List[String], Fu[String]] =
    case "omok" :: "seed" :: gameId :: rest =>
      fuccess(omokDemoSeed.seed(gameId, rest))
    case "omok" :: "show" :: gameId :: Nil =>
      fuccess(omokDemoSeed.show(gameId))
    case "omok" :: "clear" :: gameId :: Nil =>
      fuccess(omokDemoSeed.clear(gameId))
