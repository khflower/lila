package views

import lila.app.UiEnv.{ *, given }

object omokSolo2:

  def apply(using Context): Page =
    omokPages.soloBoard
