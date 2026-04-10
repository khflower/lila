package lila.omok

object CoordinateNotation:

  private val FirstFile = 'A'
  private val LastFile = ('A'.toInt + Pos.Size - 1).toChar

  def format(pos: Pos): String =
    s"${(FirstFile.toInt + pos.col).toChar}${pos.row + 1}"

  def parse(raw: String): Option[Pos] =
    val trimmed = raw.trim
    if trimmed.length < 2 || trimmed.length > 3 then None
    else
      val file = trimmed.head.toUpper
      if file < FirstFile || file > LastFile then None
      else
        parseRank(trimmed.tail).flatMap: rank =>
          Pos.fromCoords(rank - 1, file - FirstFile)

  private def parseRank(raw: String): Option[Int] =
    raw.toIntOption.filter: rank =>
      rank >= 1 && rank <= Pos.Size && raw == rank.toString
