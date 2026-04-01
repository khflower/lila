package lila.omok

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.*

class EngineContractTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("position snapshot reflects a game"):
    val game = Game.initial(RuleSet.Renju).play(Move(pos(7, 7))).toOption.get
    val snapshot = PositionSnapshot.fromGame(game, Vector(Move(pos(7, 7))))

    assertEquals(snapshot.turn, Color.White)
    assertEquals(snapshot.ruleSet, RuleSet.Renju)
    assertEquals(snapshot.board(pos(7, 7)), Some(Color.Black))
    assertEquals(snapshot.moves, Vector(Move(pos(7, 7))))

  test("engine limits and response payloads keep optional fields lightweight"):
    val request = EngineMoveRequest(
      position = PositionSnapshot.fromGame(Game.initial(RuleSet.Freestyle)),
      limits = EngineLimits(depth = Some(8), moveTime = Some(2.seconds), multiPv = 2)
    )
    assertEquals(request.limits.depth, Some(8))
    assertEquals(request.limits.moveTime, Some(2.seconds))
    assertEquals(request.limits.multiPv, 2)

  test("engine client facade stays adapter-friendly"):
    val client = new EngineClient:
      def bestMove(request: EngineMoveRequest) =
        Future.successful(EngineMoveResponse(Move(pos(7, 7)), score = Some(EngineScore(ScoreKind.Centipawn, 32))))
      def analyse(request: EngineAnalysisRequest) =
        Future.successful(EngineAnalysisResponse.normalized(Vector(EngineLine(Vector(Move(pos(7, 7)))))))

    val moveResult = Await.result(client.bestMove(EngineMoveRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)
    val analysis = Await.result(client.analyse(EngineAnalysisRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)

    assertEquals(moveResult.bestMove, Move(pos(7, 7)))
    assertEquals(moveResult.score, Some(EngineScore(ScoreKind.Centipawn, 32)))
    assertEquals(analysis.variations.map(_.moves.head), Vector(Move(pos(7, 7))))
