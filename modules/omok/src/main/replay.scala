package lila.omok

final case class ReplayError(
    game: Game,
    move: Move,
    cause: MoveError,
    index: Int
)

object Replay:

  def scan(initial: Game, moves: IterableOnce[Move]): Either[ReplayError, Vector[Game]] =
    val states = Vector.newBuilder[Game]
    states += initial

    var current = initial
    var index = 0
    val iterator = moves.iterator

    while iterator.hasNext do
      val move = iterator.next()
      current.play(move) match
        case Right(next) =>
          current = next
          states += next
          index += 1
        case Left(error) =>
          return Left(ReplayError(current, move, error, index))

    Right(states.result())

  def apply(initial: Game, moves: IterableOnce[Move]): Either[ReplayError, Game] =
    scan(initial, moves).map(_.last)
