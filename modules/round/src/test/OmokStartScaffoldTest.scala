package lila.round

import lila.core.id.{ GameFullId, GameId }
import lila.omok.RuleSet

class OmokStartScaffoldTest extends munit.FunSuite:

  test("start seeds a blank renju state for the requested player full id"):
    val repo = OmokRoundRepo()
    val scaffold = OmokStartScaffold(repo)

    val result = scaffold.start(GameFullId("demo1234abcd"))
    val state = repo.get(GameId("demo1234")).get

    assertEquals(result.reset, false)
    assertEquals(result.redirectPath, "/demo1234abcd")
    assertEquals(
      result.message,
      "started omok scaffold demo1234 -> /demo1234abcd: ruleSet=renju ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(state.ruleSet, RuleSet.Renju)
    assertEquals(state.position.ply, 0)
    assertEquals(state.moves, Vector.empty)

  test("start resets an existing omok sidecar state back to a blank ruleset-specific root"):
    val repo = OmokRoundRepo()
    val scaffold = OmokStartScaffold(repo)

    repo.put(
      GameId("demo1234"),
      OmokRoundState.initial().copy(moves = Vector(lila.omok.Move(lila.omok.Pos.unsafe(7, 7))))
    )

    val result = scaffold.start(GameFullId("demo1234wxyz"), RuleSet.Freestyle)
    val state = repo.get(GameId("demo1234")).get

    assertEquals(result.reset, true)
    assertEquals(
      result.message,
      "restarted omok scaffold demo1234 -> /demo1234wxyz: ruleSet=freestyle ply=0 turn=black lastMove=- moves=-"
    )
    assertEquals(state.ruleSet, RuleSet.Freestyle)
    assertEquals(state.position.ply, 0)
    assertEquals(state.moves, Vector.empty)

  test("raw start parsing rejects invalid full ids and rule sets"):
    val repo = OmokRoundRepo()
    val scaffold = OmokStartScaffold(repo)

    assertEquals(
      scaffold.start("bad-full-id").left.map(_.message),
      Left("invalid full id 'bad-full-id'; expected 12 characters matching [A-Za-z0-9_-]")
    )
    assertEquals(
      scaffold.start("demo1234abcd", Some("unknown")).left.map(_.message),
      Left("invalid rule set 'unknown'; expected one of: renju, freestyle")
    )
    assertEquals(repo.get(GameId("demo1234")), None)
