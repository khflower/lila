package lila.omok

import scala.concurrent.duration.DurationInt

class OmokEngineContractTest extends munit.FunSuite:

  private def move(row: Int, col: Int) = Move(Pos.unsafe(row, col))

  test("move response normalizes the principal variation around the best move"):
    val best = move(7, 7)
    val followUp = move(7, 8)

    val missingHead = EngineMoveResponse(bestMove = best, principalVariation = Vector(followUp))
    assertEquals(missingHead.normalizedPrincipalVariation, Vector(best, followUp))
    assertEquals(missingHead.line.moves, Vector(best, followUp))

    val existingHead = EngineMoveResponse(bestMove = best, principalVariation = Vector(best, followUp))
    assertEquals(existingHead.normalizedPrincipalVariation, Vector(best, followUp))

  test("analysis response normalizes candidate ordering by rank"):
    val first = EngineLine(Vector(move(7, 7), move(7, 8)), rank = 1)
    val second = EngineLine(Vector(move(6, 6), move(6, 7)), rank = 2)

    val response = EngineAnalysisResponse.normalized(Vector(second, first))

    assertEquals(response.variations.map(_.rank), Vector(1, 2))
    assertEquals(response.primaryVariation, Some(first))
    assertEquals(response.bestMove, Some(move(7, 7)))

  test("engine limits validate search budgets"):
    val limits = EngineLimits(moveTime = Some(1.second), depth = Some(12), nodes = Some(1000), multiPv = 2)
    assertEquals(limits.multiPv, 2)

    intercept[IllegalArgumentException]:
      EngineLimits(moveTime = Some(0.seconds))

    intercept[IllegalArgumentException]:
      EngineLimits(depth = Some(0))

    intercept[IllegalArgumentException]:
      EngineLimits(nodes = Some(0))

    intercept[IllegalArgumentException]:
      EngineLimits(multiPv = 0)

  test("move requests reject empty search move filters"):
    val position = EnginePosition.fromGame(Game.initial())

    intercept[IllegalArgumentException]:
      EngineMoveRequest(position = position, limits = EngineLimits(), searchMoves = Some(Set.empty))
