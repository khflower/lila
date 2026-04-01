package lila.round

import play.api.libs.json.{ JsObject, Json }

import lila.core.id.GameId
import lila.core.socket.SocketVersion
import lila.omok.{ Color, Game, Move, PositionSnapshot, Pos, Replay, RuleSet, Status }

class OmokSocketTest extends munit.FunSuite:

  private def pos(row: Int, col: Int) = Pos.unsafe(row, col)
  private def move(row: Int, col: Int) = Move(pos(row, col))

  private val moves = Vector(
    move(7, 7),
    move(0, 0),
    move(7, 8)
  )

  private val game = Replay(Game.initial(RuleSet.Renju), moves).toOption.get
  private val snapshot = PositionSnapshot.fromGame(game, moves)
  private val payload = OmokEvent.MovePayload(moves.last, snapshot)

  private val expectedPayloadJson: JsObject =
    Json.obj(
      "move" -> Json.obj(
        "key" -> "I8",
        "row" -> 7,
        "col" -> 8
      ),
      "position" -> Json.obj(
        "boardSize" -> 15,
        "boardRows" -> Json.arr(
          "w..............",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          ".......bb......",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "...............",
          "..............."
        ),
        "turn" -> "white",
        "ruleSet" -> "renju",
        "ply" -> 3,
        "lastMove" -> Json.obj(
          "key" -> "I8",
          "row" -> 7,
          "col" -> 8
        ),
        "moves" -> Json.arr(
          Json.obj("key" -> "H8", "row" -> 7, "col" -> 7),
          Json.obj("key" -> "A1", "row" -> 0, "col" -> 0),
          Json.obj("key" -> "I8", "row" -> 7, "col" -> 8)
        )
      )
    )

  test("move payload JSON keeps the omok move and position shape stable"):
    assertEquals(Json.toJson(payload).as[JsObject], expectedPayloadJson)

  test("socket omok move output keeps the payload JSON intact inside the versioned envelope"):
    val gameId = GameId("abcdefgh")
    val version = SocketVersion(42)
    val event = OmokEvent.Move(gameId, moves.last, snapshot)
    val prefix = s"r/ver $gameId $version - ${OmokEvent.moveType} "

    val message = RoundSocket.Protocol.Out.omokMove(version, event)

    assert(message.startsWith(prefix))
    assertEquals(Json.parse(message.drop(prefix.length)), expectedPayloadJson)

  test("live place path keeps the frontend redraw payload contract stable after a rejected move"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("livepath")
    val version = SocketVersion(42)
    val prefix = s"r/ver $gameId $version - ${OmokEvent.moveType} "

    player.place(PlaceRequest(gameId, pos(7, 7))).toOption.get
    player.place(PlaceRequest(gameId, pos(0, 0))).toOption.get
    val rejected = player.place(PlaceRequest(gameId, pos(0, 0))).left.toOption.get
    val accepted = player.place(PlaceRequest(gameId, pos(7, 8))).toOption.get

    assertEquals(rejected.message, s"[omok] $gameId cannot place A1: occupied")
    assertEquals(Json.toJson(accepted.payload).as[JsObject], expectedPayloadJson)

    val message = RoundSocket.Protocol.Out.omokMove(version, accepted.event)

    assert(message.startsWith(prefix))
    assertEquals(Json.parse(message.drop(prefix.length)), expectedPayloadJson)

  test("terminal omok move payload includes additive status and winner fields"):
    val repo = OmokRoundRepo()
    val player = OmokMovePlayer(repo)
    val gameId = GameId("termmove")
    val version = SocketVersion(7)
    val prefix = s"r/ver $gameId $version - ${OmokEvent.moveType} "
    val winningMoves = List(
      pos(7, 7),
      pos(0, 0),
      pos(7, 8),
      pos(0, 1),
      pos(7, 9),
      pos(0, 2),
      pos(7, 10),
      pos(0, 3),
      pos(7, 11)
    )

    val accepted = winningMoves.foldLeft(Option.empty[PlaceAccepted]):
      case (_, nextPos) => Some(player.place(PlaceRequest(gameId, nextPos)).toOption.get)

    assertEquals(accepted.flatMap(_.terminalStatus), Some(Status.Win(Color.Black)))

    val payloadJson = Json.toJson(accepted.get.payload).as[JsObject]

    assertEquals((payloadJson \ "status").as[String], "win")
    assertEquals((payloadJson \ "winner").as[String], "black")
    assertEquals((payloadJson \ "position" \ "lastMove" \ "key").as[String], "L8")

    val message = RoundSocket.Protocol.Out.omokMove(version, accepted.get.event)
    val messageJson = Json.parse(message.drop(prefix.length)).as[JsObject]

    assert(message.startsWith(prefix))
    assertEquals((messageJson \ "status").as[String], "win")
    assertEquals((messageJson \ "winner").as[String], "black")
    assertEquals((messageJson \ "position" \ "lastMove" \ "key").as[String], "L8")
