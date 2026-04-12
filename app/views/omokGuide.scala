package views

import lila.app.UiEnv.{ *, given }

object omokGuide:

  private val dataAsset = staticAssetUrl("omok/opening-guide-data.json")
  private val workbookAsset = staticAssetUrl("omok/suxingge_unified_ordered.xlsx")
  private val scriptAsset = s"${staticAssetUrl("omok/opening-guide.js")}?v=20260409a"
  private val styleAsset = s"${staticAssetUrl("omok/opening-guide.css")}?v=20260409a"

  def openingGuide(using Context) =
    Page("Omok opening guide")
      .copy(fullTitle = "Omok opening guide - Omok.dev".some)
      .wrap: body =>
        main(cls := "page-menu omok-opening-guide-page")(
          div(cls := "page-menu__content box box-pad omok-opening-guide-shell")(body)
        )
      .transformHead(head => frag(head, link(rel := "stylesheet", href := styleAsset)))
      (
        frag(
          div(cls := "omok-opening-guide-hero")(
            h1(cls := "box__top")("Omok opening guide"),
            p(cls := "omok-opening-guide-lead")(
              "H8 is fixed. Place the 2nd and 3rd moves, then this page shows only the 4th and 5th moves that exist in the opening workbook."
            ),
            p(cls := "omok-opening-guide-note")(
              "Coordinate system: A1 is the bottom-left corner, letters increase to the right, and numbers increase upward."
            ),
            div(cls := "omok-opening-guide-actions")(
              a(
                cls := "button button-empty",
                href := workbookAsset,
                attr("download") := "suxingge_unified_ordered.xlsx"
              )("Download opening workbook")
            )
          ),
          div(
            id := "omok-opening-guide-app",
            attr("data-guide-url") := dataAsset,
            attr("data-workbook-url") := workbookAsset
          ),
          script(attr("type") := "module", src := scriptAsset)
        )
      )
