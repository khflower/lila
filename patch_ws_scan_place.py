from pathlib import Path

base = Path("/tmp/lila-ws-scan/src/main/scala")

client_out = base / "ipc/ClientOut.scala"
text = client_out.read_text()
old = """  case class RoundPlayerForward(payload: JsValue) extends ClientOutRound
  case class RoundMove(uci: Uci, blur: Boolean, lag: ClientMoveMetrics, ackId: Option[Int])
      extends ClientOutRound
  case class RoundHold(mean: Int, sd: Int) extends ClientOutRound
"""
new = """  case class RoundPlayerForward(payload: JsValue) extends ClientOutRound
  case class RoundMove(uci: Uci, blur: Boolean, lag: ClientMoveMetrics, ackId: Option[Int])
      extends ClientOutRound
  case class RoundPlace(pos: String, ackId: Option[Int]) extends ClientOutRound
  case class RoundHold(mean: Int, sd: Int) extends ClientOutRound
"""
if old not in text:
    raise SystemExit("client_out type block not found")
text = text.replace(old, new, 1)
old = """              case "drop" =>
                for
                  d <- o.obj("d")
                  role <- d.str("role")
                  square <- d.str("pos")
                  drop <- Uci.Drop.fromStrings(role, square)
                  blur = d.int("b") contains 1
                  ackId = d.int("a")
                yield RoundMove(drop, blur, parseMetrics(d), ackId)
              case "hold" =>
"""
new = """              case "drop" =>
                for
                  d <- o.obj("d")
                  role <- d.str("role")
                  square <- d.str("pos")
                  drop <- Uci.Drop.fromStrings(role, square)
                  blur = d.int("b") contains 1
                  ackId = d.int("a")
                yield RoundMove(drop, blur, parseMetrics(d), ackId)
              case "place" =>
                for
                  d <- o.obj("d")
                  pos <- d.str("pos")
                  ackId = d.int("a")
                yield RoundPlace(pos, ackId)
              case "hold" =>
"""
if old not in text:
    raise SystemExit("client_out parse block not found")
text = text.replace(old, new, 1)
client_out.write_text(text)

lila_in = base / "ipc/LilaIn.scala"
text = lila_in.read_text()
old = """  case class RoundMove(fullId: Game.FullId, uci: Uci, blur: Boolean, lag: MoveMetrics) extends Round:
    private def centis(c: Option[Centis]) = optional(c.map(_.centis.toString))
    def write =
      s"r/move $fullId ${uci.uci} ${boolean(blur)} ${centis(lag.clientLag)} ${centis(lag.clientMoveTime)} ${centis(lag.frameLag)}"
    override def critical = true

  case class RoundBerserk(gameId: Game.Id, userId: User.Id) extends Round:
"""
new = """  case class RoundMove(fullId: Game.FullId, uci: Uci, blur: Boolean, lag: MoveMetrics) extends Round:
    private def centis(c: Option[Centis]) = optional(c.map(_.centis.toString))
    def write =
      s"r/move $fullId ${uci.uci} ${boolean(blur)} ${centis(lag.clientLag)} ${centis(lag.clientMoveTime)} ${centis(lag.frameLag)}"
    override def critical = true

  case class RoundPlace(fullId: Game.FullId, pos: String) extends Round:
    def write = s"r/place $fullId $pos"
    override def critical = true

  case class RoundBerserk(gameId: Game.Id, userId: User.Id) extends Round:
"""
if old not in text:
    raise SystemExit("lila_in block not found")
text = text.replace(old, new, 1)
lila_in.write_text(text)

round_actor = base / "actor/RoundClientActor.scala"
text = round_actor.read_text()
old = """          case ClientOut.RoundPlayerForward(payload) =>
            fullId.foreach: fid =>
              lilaIn.round(LilaIn.RoundPlayerDo(fid, payload))
            Behaviors.same

          case ClientOut.RoundFlag(color) =>
"""
new = """          case ClientOut.RoundPlayerForward(payload) =>
            fullId.foreach: fid =>
              lilaIn.round(LilaIn.RoundPlayerDo(fid, payload))
            Behaviors.same

          case ClientOut.RoundPlace(pos, ackId) =>
            fullId.foreach: fid =>
              clientIn(ClientIn.RoundPingFrameNoFlush)
              clientIn(ClientIn.Ack(ackId))
              lilaIn.round(LilaIn.RoundPlace(fid, pos))
            Behaviors.same

          case ClientOut.RoundFlag(color) =>
"""
if old not in text:
    raise SystemExit("round_actor block not found")
text = text.replace(old, new, 1)
round_actor.write_text(text)
