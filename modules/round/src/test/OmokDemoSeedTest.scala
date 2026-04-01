package lila.round

class OmokDemoSeedTest extends munit.FunSuite:

  test("show clear and reseed reflect the current omok demo state"):
    val repo = OmokRoundRepo()
    val helper = OmokDemoSeed(repo)

    assertEquals(helper.show("demo1234"), "no omok round state for demo1234")

    assertEquals(
      helper.seed("demo1234", List("H8", "A1")),
      "seeded omok round demo1234: ruleSet=renju ply=2 turn=black lastMove=A1 moves=H8,A1"
    )
    assertEquals(
      helper.show("demo1234"),
      "omok round demo1234: ruleSet=renju ply=2 turn=black lastMove=A1 moves=H8,A1"
    )

    assertEquals(
      helper.seed("demo1234", List("freestyle", "J10")),
      "reseeded omok round demo1234: ruleSet=freestyle ply=1 turn=white lastMove=J10 moves=J10"
    )
    assertEquals(
      helper.show("demo1234"),
      "omok round demo1234: ruleSet=freestyle ply=1 turn=white lastMove=J10 moves=J10"
    )

    assertEquals(
      helper.clear("demo1234"),
      "cleared omok round demo1234: ruleSet=freestyle ply=1 turn=white lastMove=J10 moves=J10"
    )
    assertEquals(helper.show("demo1234"), "no omok round state for demo1234")
    assertEquals(repo.get(lila.core.id.GameId("demo1234")), None)

  test("seed stores a replayed omok state for a known game id"):
    val repo = OmokRoundRepo()
    val helper = OmokDemoSeed(repo)

    val result = helper.seed("demo1234", List("renju", "H8", "A1", "I8"))
    val state = repo.get(lila.core.id.GameId("demo1234")).get

    assertEquals(
      result,
      "seeded omok round demo1234: ruleSet=renju ply=3 turn=white lastMove=I8 moves=H8,A1,I8"
    )
    assertEquals(state.position.ply, 3)
    assertEquals(state.position.lastMove.map(_.pos.key), Some("I8"))
    assertEquals(state.moves.map(_.pos.key), Vector("H8", "A1", "I8"))

  test("seed rejects invalid game ids and leaves the repo untouched"):
    val repo = OmokRoundRepo()
    val helper = OmokDemoSeed(repo)

    val result = helper.seed("bad", List("H8"))

    assertEquals(
      result,
      "ERROR invalid game id 'bad'; expected 8 characters matching [A-Za-z0-9_-]"
    )
    assertEquals(repo.get(lila.core.id.GameId("abcdefgh")), None)
