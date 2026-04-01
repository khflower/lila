package lila.round

import chess.{ ByColor, Rated }

import lila.core.game.{ Game, OnStart, Source, newGame }
import lila.core.id.{ GameFullId, GameId }
import lila.omok.{ Color as OmokColor, Move as OmokMove, RuleSet }

final case class OmokStartScaffoldError(message: String)

final case class OmokNativeStartGame(
    game: Game,
    fullIds: ByColor[GameFullId]
):
  def fullId: GameFullId = fullIds.black

final class OmokNativeGameStarter(private val create: () => Fu[OmokNativeStartGame]):
  def start(): Fu[OmokNativeStartGame] = create()

object OmokNativeGameStarter:
  def apply(
      gameRepo: lila.core.game.GameRepo,
      onStart: OnStart
  )(using
      idGenerator: lila.core.game.IdGenerator,
      newPlayer: lila.core.game.NewPlayer,
      executor: Executor
  ): OmokNativeGameStarter =
    new OmokNativeGameStarter(() =>
      idGenerator.game.flatMap: gameId =>
        val game = newGame(
          chess = chess.Game(chess.variant.Standard),
          players = ByColor: color =>
            newPlayer.anon(color),
          rated = Rated.No,
          source = Source.Api,
          pgnImport = None
        ).withId(gameId).start
        val fullIds = game.fullIds
        for
          _ <- gameRepo.insertDenormalized(game)
          _ <- onStart.exec(game.id)
        yield OmokNativeStartGame(game, fullIds)
    )

  val unsupported = new OmokNativeGameStarter(() => fufail("native omok game starter unavailable"))

final case class OmokStartScaffoldResult(
    fullId: GameFullId,
    state: OmokRoundState,
    reset: Boolean,
    nativeFullIds: Option[ByColor[GameFullId]] = None
):
  def gameId: GameId = fullId.gameId
  def redirectPath: String = s"/$fullId"
  def message: String =
    nativeFullIds.fold(
      s"${if reset then "restarted" else "started"} omok scaffold $gameId -> $redirectPath: ${OmokStartScaffold.renderState(state)}"
    ): fullIds =>
      s"started native omok round $gameId: black=/${fullIds.black} white=/${fullIds.white}: ${OmokStartScaffold.renderState(state)}"

object OmokStartScaffold:
  type FullIdExists = GameFullId => Fu[Boolean]

  def apply(omokRoundRepo: OmokRoundRepo)(using Executor): OmokStartScaffold =
    new OmokStartScaffold(omokRoundRepo, _ => fuccess(true), OmokNativeGameStarter.unsupported)

  def apply(
      omokRoundRepo: OmokRoundRepo,
      nativeGameStarter: OmokNativeGameStarter
  )(using Executor): OmokStartScaffold =
    new OmokStartScaffold(omokRoundRepo, _ => fuccess(true), nativeGameStarter)

  def apply(
      omokRoundRepo: OmokRoundRepo,
      fullIdExists: FullIdExists
  )(using Executor): OmokStartScaffold =
    new OmokStartScaffold(omokRoundRepo, fullIdExists, OmokNativeGameStarter.unsupported)

  def apply(
      omokRoundRepo: OmokRoundRepo,
      fullIdExists: FullIdExists,
      nativeGameStarter: OmokNativeGameStarter
  )(using Executor): OmokStartScaffold =
    new OmokStartScaffold(omokRoundRepo, fullIdExists, nativeGameStarter)

  def parseRuleSet(raw: String): Option[RuleSet] =
    raw.trim.toLowerCase match
      case "renju"     => Some(RuleSet.Renju)
      case "freestyle" => Some(RuleSet.Freestyle)
      case _           => None

  def renderState(state: OmokRoundState): String =
    val lastMove = state.position.lastMove.fold("-")(_.pos.key)
    s"ruleSet=${renderRuleSet(state.ruleSet)} ply=${state.position.ply} turn=${renderColor(state.position.turn)} lastMove=$lastMove moves=${renderMoves(state.moves)}"

  def missingRound(fullId: GameFullId): OmokStartScaffoldError =
    OmokStartScaffoldError(
      s"no real round for full id '$fullId'; expected an existing player fullId"
    )

  private def renderRuleSet(ruleSet: RuleSet): String = ruleSet.toString.toLowerCase
  private def renderColor(color: OmokColor): String = color.toString.toLowerCase
  private def renderMoves(moves: Vector[OmokMove]): String =
    if moves.isEmpty then "-" else moves.map(_.pos.key).mkString(",")

final class OmokStartScaffold(
    omokRoundRepo: OmokRoundRepo,
    fullIdExists: OmokStartScaffold.FullIdExists,
    nativeGameStarter: OmokNativeGameStarter
)(using Executor):

  def startNew(rawRuleSet: Option[String] = None): Fu[Either[OmokStartScaffoldError, OmokStartScaffoldResult]] =
    parseRuleSet(rawRuleSet).fold(
      err => fuccess(Left(err)),
      ruleSet =>
        nativeGameStarter.start().map: started =>
          val state = OmokRoundState.initial(ruleSet)
          omokRoundRepo.put(started.game.id, state)
          Right(OmokStartScaffoldResult(started.fullId, state, reset = false, nativeFullIds = started.fullIds.some))
    )

  def start(
      rawFullId: String,
      rawRuleSet: Option[String] = None
  ): Fu[Either[OmokStartScaffoldError, OmokStartScaffoldResult]] =
    val parsed = for
      fullId <- parseFullId(rawFullId)
      ruleSet <- parseRuleSet(rawRuleSet)
    yield (fullId, ruleSet)
    parsed match
      case Left(err)                => fuccess(Left(err))
      case Right((fullId, ruleSet)) => start(fullId, ruleSet)

  def startMessage(rawFullId: String, rawRuleSet: Option[String] = None): Fu[String] =
    start(rawFullId, rawRuleSet).map(_.fold(err => s"ERROR ${err.message}", _.message))

  def start(fullId: GameFullId): Fu[Either[OmokStartScaffoldError, OmokStartScaffoldResult]] =
    start(fullId, RuleSet.Renju)

  def start(
      fullId: GameFullId,
      ruleSet: RuleSet
  ): Fu[Either[OmokStartScaffoldError, OmokStartScaffoldResult]] =
    fullIdExists(fullId).map: exists =>
      Either.cond(
        exists,
        startExisting(fullId, ruleSet),
        OmokStartScaffold.missingRound(fullId)
      )

  private def startExisting(fullId: GameFullId, ruleSet: RuleSet): OmokStartScaffoldResult =
    val state = OmokRoundState.initial(ruleSet)
    val reset = omokRoundRepo.get(fullId.gameId).isDefined
    omokRoundRepo.put(fullId.gameId, state)
    OmokStartScaffoldResult(fullId, state, reset)

  private def parseFullId(raw: String): Either[OmokStartScaffoldError, GameFullId] =
    Either.cond(
      raw.matches("""[\w-]{12}"""),
      GameFullId(raw),
      OmokStartScaffoldError(
        s"invalid full id '$raw'; expected 12 characters matching [A-Za-z0-9_-]"
      )
    )

  private def parseRuleSet(rawRuleSet: Option[String]): Either[OmokStartScaffoldError, RuleSet] =
    rawRuleSet
      .filter(_.trim.nonEmpty)
      .fold[Either[OmokStartScaffoldError, RuleSet]](Right(RuleSet.Renju)): raw =>
        OmokStartScaffold
          .parseRuleSet(raw)
          .toRight(
            OmokStartScaffoldError(
              s"invalid rule set '$raw'; expected one of: renju, freestyle"
            )
          )
