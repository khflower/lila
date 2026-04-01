package lila.omok

import play.api.libs.json.{ Json as PlayJson, JsObject, OWrites }

final case class OmokMoveDto(key: String, row: Int, col: Int)

object OmokMoveDto:

  given OWrites[OmokMoveDto] = PlayJson.writes

  def fromMove(move: Move): OmokMoveDto =
    OmokMoveDto(
      key = move.pos.key,
      row = move.pos.row,
      col = move.pos.col
    )

final case class OmokPositionDto(
    boardSize: Int,
    boardRows: Vector[String],
    turn: String,
    ruleSet: String,
    ply: Int,
    lastMove: Option[OmokMoveDto] = None,
    moves: Vector[OmokMoveDto] = Vector.empty
):
  require(boardSize > 0, "boardSize must be > 0")
  require(boardRows.size == boardSize, s"boardRows must contain $boardSize rows")
  require(boardRows.forall(_.length == boardSize), s"boardRows must be $boardSize columns wide")
  require(ply >= 0, "ply must be >= 0")

  def asJson: JsObject = PlayJson.toJsObject(this)

object OmokPositionDto:

  given OWrites[OmokPositionDto] = PlayJson.writes

  def fromSituation(
      situation: Situation,
      ply: Int = 0,
      lastMove: Option[Move] = None,
      moves: Vector[Move] = Vector.empty
  ): OmokPositionDto =
    fromParts(
      board = situation.board,
      turn = situation.turn,
      ruleSet = situation.ruleSet,
      ply = ply,
      lastMove = lastMove,
      moves = moves
    )

  def fromPosition(position: PositionSnapshot): OmokPositionDto =
    fromParts(
      board = position.board,
      turn = position.turn,
      ruleSet = position.ruleSet,
      ply = position.ply,
      lastMove = position.lastMove,
      moves = position.moves
    )

  def fromGame(game: Game, moves: Vector[Move] = Vector.empty): OmokPositionDto =
    fromPosition(PositionSnapshot.fromGame(game, moves))

  private def fromParts(
      board: Board,
      turn: Color,
      ruleSet: RuleSet,
      ply: Int,
      lastMove: Option[Move],
      moves: Vector[Move]
  ): OmokPositionDto =
    OmokPositionDto(
      boardSize = Pos.Size,
      boardRows = renderBoard(board),
      turn = colorKey(turn),
      ruleSet = ruleSetKey(ruleSet),
      ply = ply,
      lastMove = lastMove.map(OmokMoveDto.fromMove),
      moves = moves.map(OmokMoveDto.fromMove)
    )

  private def renderBoard(board: Board): Vector[String] =
    Vector.tabulate(Pos.Size): row =>
      (0 until Pos.Size)
        .map: col =>
          cellKey(board(Pos.unsafe(row, col)))
        .mkString

  private def cellKey(cell: Option[Color]): Char = cell match
    case Some(Color.Black) => 'b'
    case Some(Color.White) => 'w'
    case None              => '.'

final case class OmokGameDto(
    position: OmokPositionDto,
    status: String,
    winner: Option[String] = None
):

  def asJson: JsObject = PlayJson.toJsObject(this)

object OmokGameDto:

  given OWrites[OmokGameDto] = PlayJson.writes

  def fromGame(game: Game, moves: Vector[Move] = Vector.empty): OmokGameDto =
    OmokGameDto(
      position = OmokPositionDto.fromGame(game, moves),
      status = statusKey(game.status),
      winner = winnerKey(game.status)
    )

private def colorKey(color: Color): String = color match
  case Color.Black => "black"
  case Color.White => "white"

private def ruleSetKey(ruleSet: RuleSet): String = ruleSet match
  case RuleSet.Freestyle => "freestyle"
  case RuleSet.Renju     => "renju"

private def statusKey(status: Status): String = status match
  case Status.Ongoing => "ongoing"
  case Status.Win(_)  => "win"
  case Status.Draw    => "draw"

private def winnerKey(status: Status): Option[String] = status match
  case Status.Win(color) => Some(colorKey(color))
  case _                 => None
