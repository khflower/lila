package lila.omok

enum Color:
  case Black, White

  def other: Color = this match
    case Black => White
    case White => Black

enum RuleSet:
  case Freestyle, Renju

enum ForbiddenReason:
  case Overline, DoubleFour, DoubleThree

enum MoveError:
  case Occupied, OutOfBounds, WrongTurn, GameAlreadyOver
  case Forbidden(reason: ForbiddenReason)

enum Status:
  case Ongoing
  case Win(color: Color)
  case Draw

final case class Pos private (index: Int) extends AnyVal:
  def row: Int = index / Pos.Size
  def col: Int = index % Pos.Size
  def key: String = s"${('A'.toInt + col).toChar}${row + 1}"

object Pos:
  val Size = 15
  val Area = Size * Size

  def fromCoords(row: Int, col: Int): Option[Pos] =
    Option.when(0 <= row && row < Size && 0 <= col && col < Size)(Pos(row * Size + col))

  def unsafe(row: Int, col: Int): Pos =
    fromCoords(row, col).getOrElse(throw new IllegalArgumentException(s"Invalid pos: ($row,$col)"))

final case class Move(pos: Pos)

final case class Board private (cells: Vector[Option[Color]]):

  def apply(pos: Pos): Option[Color] = cells(pos.index)

  def isEmpty(pos: Pos): Boolean = apply(pos).isEmpty

  def place(pos: Pos, color: Color): Either[MoveError, Board] =
    if !isEmpty(pos) then Left(MoveError.Occupied)
    else Right(copy(cells = cells.updated(pos.index, Some(color))))

  def cellCode(row: Int, col: Int, perspective: Color): Char =
    Pos.fromCoords(row, col) match
      case None => '#'
      case Some(pos) =>
        apply(pos) match
          case None        => 'X'
          case Some(color) => if color == perspective then 'B' else 'W'

  def lineLength(pos: Pos, color: Color, dr: Int, dc: Int): Int =
    1 + countDirection(pos, color, dr, dc) + countDirection(pos, color, -dr, -dc)

  private def countDirection(pos: Pos, color: Color, dr: Int, dc: Int): Int =
    var row = pos.row + dr
    var col = pos.col + dc
    var n = 0
    while Pos.fromCoords(row, col).exists(apply(_) == Some(color)) do
      n += 1
      row += dr
      col += dc
    n

  def hasFive(pos: Pos, color: Color): Boolean =
    Board.Directions.exists: (dr, dc) =>
      lineLength(pos, color, dr, dc) == 5

  def hasOverline(pos: Pos, color: Color): Boolean =
    Board.Directions.exists: (dr, dc) =>
      lineLength(pos, color, dr, dc) >= 6

object Board:
  val Directions = List((0, 1), (1, 0), (1, 1), (1, -1))
  val empty = Board(Vector.fill(Pos.Area)(None))

  def fromRows(rows: List[String]): Board =
    require(rows.size == Pos.Size, s"Expected ${Pos.Size} rows")
    val cells = rows.flatMap: row =>
      require(row.length == Pos.Size, s"Expected row width ${Pos.Size}: $row")
      row.toList.map {
        case '.' | 'X' | '_' => None
        case 'B' | 'b'       => Some(Color.Black)
        case 'W' | 'w'       => Some(Color.White)
        case other           => throw new IllegalArgumentException(s"Invalid cell: $other")
      }
    Board(cells.toVector)

object Forbidden:

  def judge(board: Board, move: Move, color: Color, ruleSet: RuleSet): Option[ForbiddenReason] =
    if ruleSet != RuleSet.Renju || color != Color.Black then None
    else
      board.place(move.pos, color).toOption.flatMap: placed =>
        if placed.hasFive(move.pos, color) then None
        else if placed.hasOverline(move.pos, color) then Some(ForbiddenReason.Overline)
        else if countFour(placed, move.pos, color) >= 2 then Some(ForbiddenReason.DoubleFour)
        else if countOpenThree(placed, move.pos, color) >= 2 then Some(ForbiddenReason.DoubleThree)
        else None

  private val directions = Vector((0, 1), (1, 0), (1, 1), (1, -1))
  private val openThreePatterns = Vector(
    ("XXBBBX", 1),
    ("XBXBBX", 2),
    ("XBBXBX", 3),
    ("XBBBXX", 4)
  )

  private def countStraightFour(board: Board, pos: Pos, color: Color): Int =
    var result = 0
    for (dr, dc) <- directions do
      for t <- -4 until 0 do
        if board.cellCode(pos.row + t * dr, pos.col + t * dc, color) == 'X'
            && board.cellCode(pos.row + (t + 5) * dr, pos.col + (t + 5) * dc, color) == 'X'
        then
          var ok = true
          var k = 1
          while k < 5 && ok do
            if board.cellCode(pos.row + (t + k) * dr, pos.col + (t + k) * dc, color) != 'B' then ok = false
            k += 1
          if ok
              && board.cellCode(pos.row + (t - 1) * dr, pos.col + (t - 1) * dc, color) != 'B'
              && board.cellCode(pos.row + (t + 6) * dr, pos.col + (t + 6) * dc, color) != 'B'
          then result += 1
    result

  private def countFour(board: Board, pos: Pos, color: Color): Int =
    var result = 0
    for (dr, dc) <- directions do
      for s <- -4 to 0 do
        if board.cellCode(pos.row + (s - 1) * dr, pos.col + (s - 1) * dc, color) != 'B'
            && board.cellCode(pos.row + (s + 5) * dr, pos.col + (s + 5) * dc, color) != 'B'
        then
          var cntB = 0
          var cntX = 0
          var ok = true
          var k = 0
          while k < 5 && ok do
            board.cellCode(pos.row + (s + k) * dr, pos.col + (s + k) * dc, color) match
              case 'B' => cntB += 1
              case 'X' => cntX += 1
              case _   => ok = false
            k += 1
          if ok && cntB == 4 && cntX == 1 then result += 1
    result - countStraightFour(board, pos, color)

  private def countOpenThree(board: Board, pos: Pos, color: Color): Int =
    import scala.collection.mutable
    val candidates = mutable.Map.empty[(Int, Vector[Int]), mutable.Set[Int]]

    for ((dr, dc), dirIndex) <- directions.zipWithIndex do
      for t <- -5 to 0 do
        if board.cellCode(pos.row + (t - 1) * dr, pos.col + (t - 1) * dc, color) != 'B'
            && board.cellCode(pos.row + (t + 6) * dr, pos.col + (t + 6) * dc, color) != 'B'
        then
          val s = (0 until 6).map: k =>
            board.cellCode(pos.row + (t + k) * dr, pos.col + (t + k) * dc, color)
          val str = s.mkString
          for (pattern, extIdx) <- openThreePatterns if str == pattern do
            val offs = pattern.zipWithIndex.collect { case ('B', k) => t + k }.toVector
            val key = (dirIndex, offs)
            val set = candidates.getOrElseUpdate(key, mutable.Set.empty[Int])
            set += (t + extIdx)

    var result = 0
    for (((dirIndex, _), extSet) <- candidates) do
      val (dr, dc) = directions(dirIndex)
      val valid = extSet.exists: ext =>
        Pos.fromCoords(pos.row + ext * dr, pos.col + ext * dc).exists: extPos =>
          board.isEmpty(extPos) &&
            board.place(extPos, color).toOption.exists: placed =>
              !placed.hasFive(extPos, color)
                && !placed.hasOverline(extPos, color)
                && countFour(placed, extPos, color) < 2
                && countOpenThree(placed, extPos, color) < 2
      if valid then result += 1
    result

final case class Situation(board: Board, turn: Color, ruleSet: RuleSet):

  def play(move: Move): Either[MoveError, Situation] =
    Forbidden.judge(board, move, turn, ruleSet) match
      case Some(reason) => Left(MoveError.Forbidden(reason))
      case None         => board.place(move.pos, turn).map(Situation(_, turn.other, ruleSet))

final case class Game(
    situation: Situation,
    status: Status = Status.Ongoing,
    ply: Int = 0,
    lastMove: Option[Move] = None
):

  def play(move: Move): Either[MoveError, Game] =
    status match
      case Status.Ongoing =>
        situation.play(move).map: nextSituation =>
          val placedBoard = nextSituation.board
          val playedColor = situation.turn
          val nextStatus =
            if placedBoard.hasFive(move.pos, playedColor) then Status.Win(playedColor)
            else Status.Ongoing
          copy(
            situation = nextSituation,
            status = nextStatus,
            ply = ply + 1,
            lastMove = Some(move)
          )
      case _ => Left(MoveError.GameAlreadyOver)

object Game:
  def initial(ruleSet: RuleSet = RuleSet.Renju): Game =
    Game(Situation(Board.empty, Color.Black, ruleSet))
