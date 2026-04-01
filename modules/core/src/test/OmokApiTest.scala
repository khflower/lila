package lila.core

import lila.core.chess.{ Depth, MultiPv }
import lila.core.omok.*

class OmokApiTest extends munit.FunSuite:

  test("position snapshot stays portable but validates board bounds"):
    val center = Pos(7, 7)
    val snapshot = PositionSnapshot.initial().copy(
      status = Status.Draw,
      stones = Vector(Stone(center, Color.Black)),
      ply = 1,
      lastMove = Some(Move(center)),
      moves = Vector(Move(center))
    )

    assertEquals(snapshot.boardSize, PositionSnapshot.defaultBoardSize)
    assertEquals(snapshot.turn, Color.Black)
    assertEquals(snapshot.status, Status.Draw)
    assertEquals(snapshot.stones.map(_.pos), Vector(center))

    intercept[IllegalArgumentException]:
      PositionSnapshot(
        turn = Color.Black,
        ruleSet = RuleSet.Renju,
        stones = Vector(Stone(Pos(0, 0), Color.Black), Stone(Pos(0, 0), Color.White))
      )

    intercept[IllegalArgumentException]:
      PositionSnapshot(
        turn = Color.Black,
        ruleSet = RuleSet.Freestyle,
        stones = Vector(Stone(Pos(15, 0), Color.Black))
      )

  test("position snapshots default to ongoing but can expose a winner explicitly"):
    val initial = PositionSnapshot.initial()
    val won = initial.copy(status = Status.Win(Color.White))

    assertEquals(initial.status, Status.Ongoing)
    assertEquals(won.status, Status.Win(Color.White))

  test("engine payloads normalize candidate ordering and validate budgets"):
    val best = Move(Pos(7, 7))
    val followUp = Move(Pos(7, 8))

    val move = EngineMoveResponse(bestMove = best, principalVariation = Vector(followUp))
    assertEquals(move.normalizedPrincipalVariation, Vector(best, followUp))
    assertEquals(move.line.moves, Vector(best, followUp))

    val analysis = EngineAnalysisResponse.normalized(
      Vector(
        EngineLine(Vector(followUp), rank = 2),
        EngineLine(Vector(best), rank = 1)
      )
    )
    assertEquals(analysis.variations.map(_.rank), Vector(1, 2))
    assertEquals(analysis.bestMove, Some(best))

    val limits = EngineLimits(
      moveTime = Some(1.second),
      depth = Some(Depth(12)),
      nodes = Some(1000),
      multiPv = MultiPv(2)
    )
    assertEquals(limits.depth.map(_.value), Some(12))
    assertEquals(limits.multiPv.value, 2)

    intercept[IllegalArgumentException]:
      EngineLimits(multiPv = MultiPv(0))

    intercept[IllegalArgumentException]:
      EngineMoveRequest(position = EnginePosition.initial(), searchMoves = Some(Set.empty))
