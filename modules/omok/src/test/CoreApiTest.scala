package lila.omok

import lila.core.omok as coreomok

class CoreApiTest extends munit.FunSuite:

  import CoreApi.*

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("position snapshots round-trip between omok module and core API"):
    val moves = Vector(Move(pos(7, 7)), Move(pos(7, 8)), Move(pos(8, 8)))
    val game = Replay(Game.initial(RuleSet.Renju), moves).toOption.get
    val local = PositionSnapshot.fromGame(game, moves)

    val core = local.toCore
    val roundTrip = core.toLocal

    assertEquals(core.boardSize, 15)
    assertEquals(core.turn, coreomok.Color.White)
    assertEquals(core.ruleSet, coreomok.RuleSet.Renju)
    assertEquals(core.lastMove.map(_.pos.row), Some(8))
    assertEquals(roundTrip.turn, local.turn)
    assertEquals(roundTrip.ruleSet, local.ruleSet)
    assertEquals(roundTrip.moves, local.moves)
    assertEquals(roundTrip.board(pos(7, 7)), Some(Color.Black))
    assertEquals(roundTrip.board(pos(7, 8)), Some(Color.White))
    assertEquals(roundTrip.board(pos(8, 8)), Some(Color.Black))

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
