package lila.omok

import lila.core.chess.{ Depth, MultiPv }
import lila.core.omok as coreomok

object CoreApi:

  extension (status: Status)
    def toCore: coreomok.Status = status match
      case Status.Ongoing    => coreomok.Status.Ongoing
      case Status.Win(color) => coreomok.Status.Win(color.toCore)
      case Status.Draw       => coreomok.Status.Draw

  extension (status: coreomok.Status)
    def toLocal: Status = status match
      case coreomok.Status.Ongoing    => Status.Ongoing
      case coreomok.Status.Win(color) => Status.Win(color.toLocal)
      case coreomok.Status.Draw       => Status.Draw

  extension (color: Color)
    def toCore: coreomok.Color = color match
      case Color.Black => coreomok.Color.Black
      case Color.White => coreomok.Color.White

  extension (color: coreomok.Color)
    def toLocal: Color = color match
      case coreomok.Color.Black => Color.Black
      case coreomok.Color.White => Color.White

  extension (ruleSet: RuleSet)
    def toCore: coreomok.RuleSet = ruleSet match
      case RuleSet.Freestyle => coreomok.RuleSet.Freestyle
      case RuleSet.Renju     => coreomok.RuleSet.Renju
      case RuleSet.Taraguchi10 => coreomok.RuleSet.Renju

  extension (ruleSet: coreomok.RuleSet)
    def toLocal: RuleSet = ruleSet match
      case coreomok.RuleSet.Freestyle => RuleSet.Freestyle
      case coreomok.RuleSet.Renju     => RuleSet.Renju

  extension (pos: Pos)
    def toCore: coreomok.Pos = coreomok.Pos(pos.row, pos.col)

  extension (pos: coreomok.Pos)
    def toLocal: Pos = Pos.unsafe(pos.row, pos.col)

  extension (move: Move)
    def toCore: coreomok.Move = coreomok.Move(move.pos.toCore)

  extension (move: coreomok.Move)
    def toLocal: Move = Move(move.pos.toLocal)

  extension (snapshot: PositionSnapshot)
    def toCore: coreomok.PositionSnapshot =
      val stones =
        for
          row <- 0 until Pos.Size
          col <- 0 until Pos.Size
          pos = Pos.unsafe(row, col)
          color <- snapshot.board(pos).toList
        yield coreomok.Stone(coreomok.Pos(row, col), color.toCore)
      coreomok.PositionSnapshot(
        boardSize = Pos.Size,
        turn = snapshot.turn.toCore,
        ruleSet = snapshot.ruleSet.toCore,
        status = snapshot.status.toCore,
        stones = stones.toVector,
        ply = snapshot.ply,
        lastMove = snapshot.lastMove.map(_.toCore),
        moves = snapshot.moves.map(_.toCore)
      )

  extension (snapshot: coreomok.PositionSnapshot)
    def toLocal: PositionSnapshot =
      val board = snapshot.stones.foldLeft(Board.empty): (board, stone) =>
        board.place(stone.pos.toLocal, stone.color.toLocal).toOption.getOrElse(board)
      PositionSnapshot(
        board = board,
        turn = snapshot.turn.toLocal,
        ruleSet = snapshot.ruleSet.toLocal,
        status = snapshot.status.toLocal,
        ply = snapshot.ply,
        lastMove = snapshot.lastMove.map(_.toLocal),
        moves = snapshot.moves.map(_.toLocal)
      )

  extension (limits: EngineLimits)
    def toCore: coreomok.EngineLimits =
      coreomok.EngineLimits(
        nodes = limits.nodes,
        depth = limits.depth.map(Depth.apply),
        moveTime = limits.moveTime,
        multiPv = MultiPv(limits.multiPv)
      )

  extension (limits: coreomok.EngineLimits)
    def toLocal: EngineLimits =
      EngineLimits(
        nodes = limits.nodes,
        depth = limits.depth.map(_.value),
        moveTime = limits.moveTime,
        multiPv = limits.multiPv.value
      )

  extension (scoreKind: ScoreKind)
    def toCore: coreomok.ScoreKind = scoreKind match
      case ScoreKind.Relative   => coreomok.ScoreKind.Relative
      case ScoreKind.WinRate    => coreomok.ScoreKind.WinRate
      case ScoreKind.ForcedWin  => coreomok.ScoreKind.ForcedWin
      case ScoreKind.ForcedLoss => coreomok.ScoreKind.ForcedLoss

  extension (scoreKind: coreomok.ScoreKind)
    def toLocal: ScoreKind = scoreKind match
      case coreomok.ScoreKind.Relative   => ScoreKind.Relative
      case coreomok.ScoreKind.WinRate    => ScoreKind.WinRate
      case coreomok.ScoreKind.ForcedWin  => ScoreKind.ForcedWin
      case coreomok.ScoreKind.ForcedLoss => ScoreKind.ForcedLoss

  extension (score: EngineScore)
    def toCore: coreomok.EngineScore = coreomok.EngineScore(score.kind.toCore, score.value)

  extension (score: coreomok.EngineScore)
    def toLocal: EngineScore = EngineScore(score.kind.toLocal, score.value)
