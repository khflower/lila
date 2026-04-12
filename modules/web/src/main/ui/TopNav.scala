package lila.web
package ui

import lila.ui.*
import ScalatagsTemplate.{ *, given }

final class TopNav(helpers: Helpers):
  import helpers.{ *, given }

  private def linkTitle(url: String, name: Frag)(using ctx: Context) =
    if ctx.blind then h3(name) else a(href := url)(name)

  def apply(hasClas: Boolean, hasDgt: Boolean)(using ctx: Context) =
    st.nav(id := "topnav", cls := "hover")(
      st.section(
        linkTitle(
          "/",
          frag(
            span(cls := "play")(trans.site.play()),
            span(cls := "home")("Omok.dev")
          )
        ),
        div(role := "group")(
          a(href := s"${langHref("/ko")}?any#hook")(trans.site.createLobbyGame()),
          a(href := s"${langHref("/ko")}?any#friend")(trans.site.challengeAFriend()),
          a(href := s"${langHref("/ko")}?any#ai")(trans.site.playAgainstComputer()),
          a(href := "/dev/omok/opening-guide")("Opening guide")
        )
      )
    )
