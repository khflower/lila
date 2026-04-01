package lila.omok

object RapfiProtocol:

  /** Gomocup/Piskvork coordinates are 0-based x,y where x is column and y is row. */
  def formatCoord(pos: Pos): String = s"${pos.col},${pos.row}"

  def parseCoord(raw: String): Option[Pos] =
    raw.trim.split(',').toList match
      case x :: y :: Nil =>
        for
          col <- x.trim.toIntOption
          row <- y.trim.toIntOption
          pos <- Pos.fromCoords(row, col)
        yield pos
      case _ => None

  def start(size: Int = Pos.Size): String = s"START $size"

  def begin: String = "BEGIN"

  def turn(pos: Pos): String = s"TURN ${formatCoord(pos)}"

  def board(snapshot: PositionSnapshot): Vector[String] =
    val moves = for
      row <- 0 until Pos.Size
      col <- 0 until Pos.Size
      pos <- Pos.fromCoords(row, col).toList
      color <- snapshot.board(pos).toList
    yield s"${col},${row},${stoneId(color)}"
    Vector("BOARD") ++ moves ++ Vector("DONE")

  def info(limits: EngineLimits): Vector[String] =
    Vector(
      limits.moveTime.map(ms => s"INFO time_left ${ms.toMillis}"),
      limits.depth.map(d => s"INFO max_depth $d"),
      limits.nodes.map(n => s"INFO max_node $n")
    ).flatten

  private def stoneId(color: Color): Int = color match
    case Color.Black => 1
    case Color.White => 2

object RapfiAdapter:

  def openingHandshake(size: Int = Pos.Size): Vector[String] =
    Vector(RapfiProtocol.start(size), "INFO rule 0")

  def moveRequest(position: EnginePosition, limits: EngineLimits = EngineLimits()): Vector[String] =
    RapfiProtocol.info(limits) ++ fullSyncMove(position)

  def incrementalMoveRequest(
      position: EnginePosition,
      limits: EngineLimits = EngineLimits(),
      syncedMoves: Option[Vector[Move]] = Some(Vector.empty)
  ): Vector[String] =
    RapfiProtocol.info(limits) ++ incrementalMove(position, syncedMoves)

  def analysisRequest(position: EnginePosition, limits: EngineLimits = EngineLimits()): Vector[String] =
    RapfiProtocol.info(limits) ++ RapfiProtocol.board(position)

  private def fullSyncMove(position: EnginePosition): Vector[String] =
    if position.moves.isEmpty then Vector(RapfiProtocol.begin)
    else RapfiProtocol.board(position)

  private def incrementalMove(position: EnginePosition, syncedMoves: Option[Vector[Move]]): Vector[String] =
    if position.moves.isEmpty then Vector(RapfiProtocol.begin)
    else
      syncedMoves
        .collect:
          case knownMoves if position.moves.length == knownMoves.length + 1 && position.moves.startsWith(knownMoves) =>
            Vector(RapfiProtocol.turn(position.moves.last.pos))
        .getOrElse(RapfiProtocol.board(position))
