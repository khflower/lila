package lila.round

import lila.core.id.{ GameFullId, GameId }
import lila.omok.RuleSet

final case class OmokStartScaffoldError(message: String)

final case class OmokStartScaffoldResult(
    fullId: GameFullId,
    state: OmokRoundState,
    reset: Boolean
):
  def gameId: GameId = fullId.gameId
  def redirectPath: String = s"/$fullId"
  def message: String =
    s"${if reset then "restarted" else "started"} omok scaffold $gameId -> $redirectPath: ${OmokDemoSeed.renderState(state)}"

object OmokStartScaffold:
  def apply(omokRoundRepo: OmokRoundRepo): OmokStartScaffold = new OmokStartScaffold(omokRoundRepo)

final class OmokStartScaffold(omokRoundRepo: OmokRoundRepo):

  def start(rawFullId: String, rawRuleSet: Option[String] = None): Either[OmokStartScaffoldError, OmokStartScaffoldResult] =
    for
      fullId <- parseFullId(rawFullId)
      ruleSet <- parseRuleSet(rawRuleSet)
    yield start(fullId, ruleSet)

  def start(fullId: GameFullId): OmokStartScaffoldResult =
    start(fullId, RuleSet.Renju)

  def start(fullId: GameFullId, ruleSet: RuleSet): OmokStartScaffoldResult =
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
        OmokDemoSeed
          .parseRuleSet(raw)
          .toRight(
            OmokStartScaffoldError(
              s"invalid rule set '$raw'; expected one of: renju, freestyle"
            )
          )
