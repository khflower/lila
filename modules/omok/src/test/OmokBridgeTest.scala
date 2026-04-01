package lila.omok

class OmokBridgeTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)

  test("game dto keeps a serializable snapshot of the finished game state"):
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
    val game = Replay(Game.initial(RuleSet.Freestyle), moves).toOption.get

    val dto = OmokGameDto.fromGame(game, moves)
    val json = dto.asJson

    assertEquals(dto.status, "win")
    assertEquals(dto.winner, Some("black"))
    assertEquals(dto.position.turn, "white")
    assertEquals(dto.position.ruleSet, "freestyle")
    assertEquals(dto.position.ply, moves.size)
    assertEquals(dto.position.lastMove.map(_.key), Some("J8"))
    assertEquals(dto.position.moves.map(_.key), moves.map(_.pos.key))
    assertEquals(dto.position.boardRows(0), "wwww...........")
    assertEquals(dto.position.boardRows(7), ".....bbbbb.....")
    assertEquals(Board.fromRows(dto.position.boardRows.toList), game.situation.board)
    assertEquals((json \ "status").as[String], "win")
    assertEquals((json \ "winner").asOpt[String], Some("black"))
    assertEquals((json \ "position" \ "lastMove" \ "key").asOpt[String], Some("J8"))

  test("position dto can bridge a lightweight engine snapshot without game status"):
    val moves = Vector(Move(pos(7, 7)), Move(pos(7, 8)))
    val game = Replay(Game.initial(RuleSet.Renju), moves).toOption.get
    val snapshot = PositionSnapshot.fromGame(game, moves)

    val dto = OmokPositionDto.fromPosition(snapshot)

    assertEquals(dto.turn, "black")
    assertEquals(dto.ruleSet, "renju")
    assertEquals(dto.ply, 2)
    assertEquals(dto.lastMove.map(_.key), Some("I8"))
    assertEquals(dto.moves.map(_.key), Vector("H8", "I8"))
    assertEquals(dto.boardRows(7), ".......bw......")
    assertEquals((dto.asJson \ "turn").as[String], "black")
    assertEquals((dto.asJson \ "moves").as[play.api.libs.json.JsArray].value.size, 2)

  test("analyse dto keeps the boot namespace compact and can reuse game snapshots"):
    val moves = Vector(
      Move(pos(7, 7)),
      Move(pos(0, 0)),
      Move(pos(7, 8)),
      Move(pos(0, 1)),
      Move(pos(7, 9)),
      Move(pos(0, 2)),
      Move(pos(7, 10)),
      Move(pos(0, 3)),
      Move(pos(7, 11))
    )
    val game = Replay(Game.initial(RuleSet.Renju), moves).toOption.get

    val empty = OmokAnalyseDto.empty
    val dto = OmokAnalyseDto.fromGame(game, moves)

    assertEquals(empty.asJson.keys, Set.empty)
    assertEquals(dto.status, Some("win"))
    assertEquals(dto.winner, Some("black"))
    assertEquals(dto.position.flatMap(_.lastMove).map(_.key), Some("L8"))
    assertEquals((dto.asJson \ "status").asOpt[String], Some("win"))
    assertEquals((dto.asJson \ "position" \ "moves").as[play.api.libs.json.JsArray].value.size, moves.size)
