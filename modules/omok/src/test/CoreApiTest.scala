package lila.omok

import lila.core.omok as coreomok

class CoreApiTest extends munit.FunSuite:

  import CoreApi.*

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("position snapshots round-trip between omok module and core API"):
    val moves = Vector(
      Move(pos(7, 5)),
      Move(pos(0, 0)),
      Move(pos(7, 6)),
      Move(pos(0, 1)),
      Move(pos(7, 7)),
      Move(pos(0, 2)),
      Move(pos(7, 8)),
      Move(pos(0, 3)),
      Move(pos(7, 9))
    )
    val game = Replay(Game.initial(RuleSet.Renju), moves).toOption.get
    val local = PositionSnapshot.fromGame(game, moves)

    val core = local.toCore
    val roundTrip = core.toLocal

    assertEquals(core.boardSize, 15)
    assertEquals(core.turn, coreomok.Color.White)
    assertEquals(core.ruleSet, coreomok.RuleSet.Renju)
    assertEquals(core.status, coreomok.Status.Win(coreomok.Color.Black))
    assertEquals(core.lastMove.map(_.pos.row), Some(7))
    assertEquals(core.lastMove.map(_.pos.col), Some(9))
    assertEquals(roundTrip.turn, local.turn)
    assertEquals(roundTrip.ruleSet, local.ruleSet)
    assertEquals(roundTrip.status, local.status)
    assertEquals(roundTrip.moves, local.moves)
    assertEquals(roundTrip.board(pos(7, 5)), Some(Color.Black))
    assertEquals(roundTrip.board(pos(0, 3)), Some(Color.White))
    assertEquals(roundTrip.board(pos(7, 9)), Some(Color.Black))

  test("core snapshots preserve draw status when converting back to local"):
    val core = coreomok.PositionSnapshot(
      turn = coreomok.Color.Black,
      ruleSet = coreomok.RuleSet.Freestyle,
      status = coreomok.Status.Draw,
      stones = Vector(
        coreomok.Stone(coreomok.Pos(7, 7), coreomok.Color.Black),
        coreomok.Stone(coreomok.Pos(7, 8), coreomok.Color.White)
      ),
      ply = 2,
      lastMove = Some(coreomok.Move(coreomok.Pos(7, 8))),
      moves = Vector(coreomok.Move(coreomok.Pos(7, 7)), coreomok.Move(coreomok.Pos(7, 8)))
    )

    val local = core.toLocal

    assertEquals(local.status, Status.Draw)
    assertEquals(local.turn, Color.Black)
    assertEquals(local.ruleSet, RuleSet.Freestyle)
    assertEquals(local.lastMove.map(_.pos), Some(pos(7, 8)))
    assertEquals(local.moves, Vector(Move(pos(7, 7)), Move(pos(7, 8))))

  test("engine limits and scores convert cleanly to core types"):
    val limits = EngineLimits(nodes = Some(1000), depth = Some(12), multiPv = 2)
    val score = EngineScore(ScoreKind.Relative, 37)

    val coreLimits = limits.toCore
    val roundTripLimits = coreLimits.toLocal
    val coreScore = score.toCore

    assertEquals(coreLimits.depth.map(_.value), Some(12))
    assertEquals(coreLimits.multiPv.value, 2)
    assertEquals(roundTripLimits, limits)
    assertEquals(coreScore.kind, coreomok.ScoreKind.Relative)
    assertEquals(coreScore.value, 37)
