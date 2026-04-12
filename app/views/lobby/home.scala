package views.lobby

import play.api.libs.json.Json

import lila.app.UiEnv.{ *, given }
import lila.app.mashup.Preload.Homepage

object home:

  def apply(homepage: Homepage)(using ctx: Context) =
    import homepage.*
    def playerName(player: lila.core.game.Player) =
      player.aiLevel.fold(player.name.fold("Anon.")(_.value))(_ => "Rapfi AI")
    def ongoingOmokBox =
      homepage.ongoingOmokGames.nonEmpty.option(
        div(cls := "lobby__box lobby__box--ongoing-omok")(
          div(cls := "lobby__box__top")(span(cls := "title")("진행 중인 오목 경기")),
          div(cls := "lobby__box__content")(
            table(
              tbody(
                homepage.ongoingOmokGames.map: game =>
                  tr(
                    td(cls := "name")(
                      a(href := routes.Round.watcher(game.id, chess.Color.white))(
                        s"${playerName(game.whitePlayer)} vs ${playerName(game.blackPlayer)}"
                      )
                    ),
                    td(cls := "more")(
                      small(game.clock.fold("무제한")(_.config.show))
                    )
                  )
              )
            )
          )
        )
      )

    Page("")
      .copy(fullTitle = "Omok.dev".some)
      .i18n(_.variant)
      .js(
        PageModule(
          "lobby",
          Json
            .obj(
              "data" -> data,
              "showRatings" -> ctx.pref.showRatings,
              "ongoingOmokGames" -> homepage.ongoingOmokGames.map: game =>
                Json.obj(
                  "id" -> game.id.value,
                  "url" -> routes.Round.watcher(game.id, chess.Color.white).url,
                  "white" -> playerName(game.whitePlayer),
                  "black" -> playerName(game.blackPlayer),
                  "clock" -> game.clock.fold("무제한")(_.config.show),
                  "updatedAt" -> game.movedAt.toString
                )
            )
            .add("hasUnreadLichessMessage", hasUnreadLichessMessage)
            .add("bots", Granter.opt(_.Beta))
            .add("playban", playban.map(lila.playban.TempBan.lobbyJson))
        )
      )
      .css("lobby")
      .graph(
        OpenGraph(
          image = None,
          title = "Omok.dev",
          url = netBaseUrl.into(Url),
          description = "Free online omok with custom rooms, Rapfi AI, and opening study tools."
        )
      )
      .hrefLangs(lila.ui.LangPath("/")):
        main(
          cls := List(
            "lobby" -> true,
            "lobby-nope" -> (playban.isDefined || currentGame.isDefined || homepage.hasUnreadLichessMessage)
          )
        )(
          div(cls := "lobby__side")(
            div(cls := "about-side")(
              ctx.blind.option(h2(trans.site.about())),
              p("Omok.dev is a free, open source omok service focused on real-time play, browser-side AI, and opening study."),
              a(cls := "blue", href := "/source")(trans.site.sourceCode()),
              br,
              a(cls := "button button-empty", href := "/dev/omok/opening-guide")("Open the opening guide"),
              a(cls := "button button-empty", href := "/omok/solo")("Open the solo board")
            ),
            ongoingOmokBox
          ),
          currentGame
            .map(bits.currentGameInfo)
            .orElse:
              hasUnreadLichessMessage.option(bits.showUnreadLichessMessage)
            .orElse:
              playban.map(bits.playbanInfo)
            .getOrElse:
              if ctx.blind then blindLobby(blindGames) else bits.lobbyApp
          ,
          div(cls := "lobby__table")(
            div(cls := "lobby__start")(
              button(cls := "button button-metal lobby__start__button lobby__start__button--hook")(
                trans.site.createLobbyGame()
              ),
              button(cls := "button button-metal lobby__start__button lobby__start__button--friend")(
                trans.site.challengeAFriend()
              ),
              button(cls := "button button-metal lobby__start__button lobby__start__button--ai")(
                trans.site.playAgainstComputer()
              ),
              a(
                cls := "button button-metal lobby__start__button lobby__start__button--opening-guide",
                href := "/dev/omok/opening-guide"
              )("Opening guide"),
              a(
                cls := "button button-metal lobby__start__button lobby__start__button--solo",
                href := "/omok/solo"
              )("Solo board")
            )
          ),
          div(cls := "lobby__about")(
            ctx.blind.option(h2("Omok.dev")),
            a(href := "/dev/omok/opening-guide")("Opening guide"),
            a(href := "/omok/solo")("Solo board"),
            a(href := routes.Cms.tos)(trans.site.termsOfService()),
            a(href := "/privacy")(trans.site.privacy()),
            a(href := "/source")(trans.site.sourceCode())
          )
        )
