package lila.omok

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.*

class EngineContractTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("position snapshot reflects a game"):
    val firstMove = Move(pos(7, 7))
    val game = Game.initial(RuleSet.Renju).play(firstMove).toOption.get
    val snapshot = PositionSnapshot.fromGame(game, Vector(firstMove))

    assertEquals(snapshot.turn, Color.White)
    assertEquals(snapshot.ruleSet, RuleSet.Renju)
    assertEquals(snapshot.ply, 1)
    assertEquals(snapshot.lastMove, Some(firstMove))
    assertEquals(snapshot.board(pos(7, 7)), Some(Color.Black))
    assertEquals(snapshot.moves, Vector(firstMove))

  test("engine limits and response payloads keep optional fields lightweight"):
    val request = EngineMoveRequest(
      position = PositionSnapshot.fromGame(Game.initial(RuleSet.Freestyle)),
      limits = EngineLimits(depth = Some(8), moveTime = Some(2.seconds), multiPv = 2)
    )
    assertEquals(request.limits.depth, Some(8))
    assertEquals(request.limits.moveTime, Some(2.seconds))
    assertEquals(request.limits.multiPv, 2)

  test("engine facade stays adapter-friendly"):
    val client = new EngineClient:
      def bestMove(request: EngineMoveRequest) =
        Future.successful(EngineMoveResponse(Move(pos(7, 7)), score = Some(EngineScore(ScoreKind.Relative, 32))))
      def analyse(request: EngineAnalysisRequest) =
        Future.successful(EngineAnalysisResponse.normalized(Vector(EngineLine(Vector(Move(pos(7, 7)))))))

    val facade = OmokEngineFacade.from(client)
    val moveResult =
      Await.result(facade.play(EngineMoveRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)
    val analysis =
      Await.result(facade.analyse(EngineAnalysisRequest(PositionSnapshot.fromGame(Game.initial()))), 2.seconds)
    val health = Await.result(facade.health, 2.seconds)

    assertEquals(moveResult.map(_.bestMove), Right(Move(pos(7, 7))))
    assertEquals(moveResult.map(_.score), Right(Some(EngineScore(ScoreKind.Relative, 32))))
    assertEquals(analysis.map(_.variations.map(_.bestMove)), Right(Vector(Move(pos(7, 7)))))
    assertEquals(health, EngineHealth.Ready)
