package lila.round

class OmokDemoSeedTest extends munit.FunSuite:

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
