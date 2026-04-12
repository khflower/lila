package lila.round

import chess.*
import chess.format.Fen
import chess.variant.*

import lila.game.Rematches
import lila.omok.OmokAiConfig

import Rematcher.*

class RematcherTest extends munit.FunSuite:

  test("chess960 && shouldRepeatChessPosition"):
    val originalBoard = Position(Board.fromMap(Chess960.initialPieces(317)), Chess960, Color.White)
    val x = returnChessGame(Chess960, none, Fen.write(originalBoard).some, true)
    assertEquals(x.position, originalBoard)

  test("FromPosition with custom Fen"):
    val originalFen = Fen.Full("rqbnknrb/pppppppp/8/8/8/8/PPPPPPPP/RQBNKNRB w Qq - 0 1")
    val originalBoard = Fen.read(FromPosition, originalFen)
    val x = returnChessGame(FromPosition, none, originalFen.some, false)
    assertEquals(x.position.some, originalBoard)

  test("FromPosition with no Fen"):
    val x = returnChessGame(FromPosition, none, none, false)
    assertEquals(x.position.variant, FromPosition)
    assertEquals(x.position.pieces, Standard.initialPieces)

  test("all variants except Chess960"):
    Variant.list.all
      .filter(_ != Chess960)
      .foreach: variant =>
        val x = returnChessGame(variant, none, none, false)
        assertEquals(x.position, variant.initialPosition)

  test("accepted rematch redirects instead of creating a fresh offer"):
    val nextId = lila.core.id.GameId("abcdefgh")
    val action = decideAction(
      existing = Some(Rematches.NextGame.Accepted(nextId)),
      color = Color.White,
      opponentIsAi = false,
      declined = false,
      canOffer = true
    )
    assertEquals(action, YesAction.RedirectAccepted(nextId))

  test("opponent offer joins existing rematch"):
    val action = decideAction(
      existing = Some(Rematches.NextGame.Offered(Color.Black, lila.core.id.GameId("abcdefgh"))),
      color = Color.White,
      opponentIsAi = false,
      declined = false,
      canOffer = true
    )
    assertEquals(action, YesAction.JoinExisting)

  test("ai rematch joins immediately without waiting for an offer"):
    val action = decideAction(
      existing = None,
      color = Color.White,
      opponentIsAi = true,
      declined = false,
      canOffer = true
    )
    assertEquals(action, YesAction.JoinExisting)

  test("omok ai rematch preserves config but updates ai color for the new seat"):
    val previous = Some(
      OmokAiConfig(
        provider = "rapfi",
        mode = "browser",
        aiColor = Some("black"),
        threads = 1,
        moveTimeMs = Some(800),
        analysisEnabled = true
      )
    )

    val updated = rematchOmokAi(previous, Some("white"))

    assertEquals(updated.map(_.provider), Some("rapfi"))
    assertEquals(updated.map(_.mode), Some("browser"))
    assertEquals(updated.flatMap(_.aiColor), Some("white"))
    assertEquals(updated.flatMap(_.moveTimeMs), Some(800))
    assertEquals(updated.map(_.analysisEnabled), Some(true))
