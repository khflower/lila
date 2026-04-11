package views

import lila.app.UiEnv.{ *, given }

object omokSolo2:

  private val styleAsset = s"${staticAssetUrl("omok/omok-pages.css")}?v=20260410a"
  private val solo2StyleAsset = s"${staticAssetUrl("omok/solo-board-2.css")}?v=20260411a"
  private val solo2ScriptAsset = s"${staticAssetUrl("omok/solo-board-2.js")}?v=20260411a"
  private val brandName = "Omok.dev"

  def apply(using Context) =
    Page("Solo board 2")
      .copy(fullTitle = s"Solo board 2 - $brandName".some)
      .wrap: body =>
        main(cls := "page-menu omok-solo-page")(
          div(cls := "page-menu__content box box-pad omok-page__shell")(body)
        )
      .transformHead(head => frag(head, link(rel := "stylesheet", href := styleAsset), link(rel := "stylesheet", href := solo2StyleAsset)))
      (
        frag(
          div(cls := "omok-page__hero")(
            h1(cls := "box__top")("Solo board 2 beta"),
            p(cls := "omok-page__lead")(
              "Build move trees, choose branches, attach short text labels, and save or load a working board file without replacing the original solo board yet."
            ),
            div(cls := "omok-page__actions")(
              a(cls := "button button-metal", href := "/")("Back to lobby"),
              a(cls := "button button-empty", href := "/omok/solo")("Solo board 1"),
              a(cls := "button button-empty", href := "/dev/omok/opening-guide")("Opening guide")
            )
          ),
          div(id := "omok-solo-board-2-app"),
          script(attr("type") := "module", src := solo2ScriptAsset)
        )
      )
