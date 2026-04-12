package views

import lila.app.UiEnv.{ *, given }
import lila.round.OmokRoundState

object omokPages:

  private val styleAsset = s"${staticAssetUrl("omok/omok-pages.css")}?v=20260410a"
  private val friendRoomScript = s"${staticAssetUrl("omok/friend-room.js")}?v=20260410a"
  private val soloStyleAsset = s"${staticAssetUrl("omok/solo-board.css")}?v=20260410a"
  private val soloScriptAsset = s"${staticAssetUrl("omok/solo-board.js")}?v=20260410a"
  private val solo2StyleAsset = s"${staticAssetUrl("omok/solo-board-2.css")}?v=20260411b"
  private val solo2ScriptAsset = s"${staticAssetUrl("omok/solo-board-2.js")}?v=20260411b"
  private val brandName = "Omok.dev"
  private val backToLobbyLabel = "Back to lobby"
  private val openingGuideLabel = "Opening guide"
  private val createRoomLabel = "Create omok room"
  private val invitePlayerLabel = "Invite a player"
  private val playRapfiLabel = "Play against Rapfi AI"
  private val playersTitle = "Omok players"
  private val playersLead = "Open player-related links without leaving the Omok.dev experience."
  private val gamesTitle = "Omok games"
  private val gamesLead = "Open live-game related links without jumping into legacy game pages."
  private val faqTitle = "Omok.dev FAQ"
  private val faqLead = "Answers for Omok.dev play, correspondence, autoplay, rules, and browser-side Rapfi AI."
  private val termsTitle = "Omok.dev terms of service"
  private val termsLead = "Use Omok.dev fairly, respect other players, and do not use automation or harassment to damage play."
  private val sourceTitle = "Omok.dev source"
  private val sourceLead = "This Omok.dev build adds omok rules, browser-side Rapfi AI, and opening study pages on top of the base service shell."
  private val analysisTitle = "Omok review"
  private val analysisLead = "Review an omok game without falling back to the legacy analysis board."
  private val analysisUnavailable = "This route is reserved for Omok.dev review pages. Open an omok game or use the opening guide instead."
  private val profileTitle = "Omok player page"
  private val profileLead = "Player profile links stay inside Omok.dev even while the old profile system is being phased out."
  private val lastMoveLabel = "Last move"
  private val moveListTitle = "Move list"
  private val openingLogTitle = "Opening log"

  private def withStyle(page: Page) =
    page.transformHead(head => frag(head, link(rel := "stylesheet", href := styleAsset)))

  private def pageShell(title: String, lead: Frag, active: String = "page")(content: Frag*)(using Context) =
    withStyle(
      Page(title)
        .copy(fullTitle = s"$title - $brandName".some)
        .wrap: body =>
          main(cls := "page-menu omok-page")(
            div(cls := "page-menu__content box box-pad omok-page__shell")(body)
          )
    )(
      frag(
        div(cls := "omok-page__hero")(
          h1(cls := "box__top")(title),
          p(cls := "omok-page__lead")(lead),
          div(cls := "omok-page__actions")(
            a(cls := "button button-metal", href := "/")(backToLobbyLabel),
            a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel)
          )
        ),
        content
      )
    )

  private def card(title: Frag)(content: Frag*) =
    div(cls := "omok-page__card")(
      h2(title),
      content
    )

  private def leadWithDetail(base: String, detail: String): Frag =
    detail.trim match
      case "" => base
      case value => frag(base, " Requested path: ", code(value), ".")

  private def ruleSetLabel(raw: String): String =
    raw.trim match
      case "" => "Unknown"
      case value =>
        value
          .replace('-', ' ')
          .replace('_', ' ')
          .split("\\s+")
          .toList
          .filter(_.nonEmpty)
          .map(part => part.take(1).toUpperCase + part.drop(1))
          .mkString(" ")

  private def colorLabel(raw: String): String =
    raw.trim.toLowerCase match
      case "black" => "Black"
      case "white" => "White"
      case other   => if other.nonEmpty then other else "Unknown"

  private def statusLabel(state: OmokRoundState): String =
    state.terminalStatus match
      case Some(lila.omok.Status.Win(color)) => s"${colorLabel(color.toString)} wins"
      case Some(lila.omok.Status.Draw)       => "Draw"
      case _                                 => "Ongoing"

  private def moveRows(state: OmokRoundState): Seq[Frag] =
    state.moves.zipWithIndex.grouped(2).toSeq.map: pair =>
      val cells = pair.map: (move, index) =>
        val moveNo = index + 1
        td(s"$moveNo. ${move.pos.key}")
      tr(cells)

  def players(section: String = "overview")(using Context) =
    val sectionTitle = section match
      case "online"      => "Online players"
      case "leaderboard" => "Leaderboards"
      case "bots"        => "Bots and AI"
      case "opponents"   => "Opponents"
      case _             => "Players overview"
    pageShell(playersTitle, playersLead)(
      div(cls := "omok-page__grid")(
        card("Current section")(
          p(s"You are viewing: $sectionTitle."),
          p("Use this hub to stay inside Omok.dev while player-related screens are being rebuilt for omok-specific play.")
        ),
        card("Quick actions")(
          p("Start a room, invite a player, or launch Rapfi AI without leaving the omok lobby."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#hook")(createRoomLabel),
            a(cls := "button button-empty", href := "/ko?any#friend")(invitePlayerLabel),
            a(cls := "button button-empty", href := "/ko?any#ai")(playRapfiLabel)
          )
        )
      )
    )

  def games(channel: Option[String])(using Context) =
    val lead =
      channel.filter(_.nonEmpty).fold[Frag](gamesLead): chan =>
        frag(gamesLead, " Current channel request: ", strong(chan), ".")
    pageShell(gamesTitle, lead)(
      div(cls := "omok-page__grid")(
        card("Live play")(
          p("Open live omok rooms from the lobby. Legacy TV pages are replaced by Omok.dev room and game flows."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#hook")(createRoomLabel),
            a(cls := "button button-empty", href := "/")(trans.site.play())
          )
        ),
        card("Study and review")(
          p("Use the opening guide for structured opening study, or open a live room to continue play and rematches."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel)
          )
        )
      )
    )

  def faq(using Context) =
    pageShell(faqTitle, faqLead)(
      div(cls := "omok-page__stack")(
        card(span(id := "autoplay")("Autoplay and sound"))(
          p("If your browser blocks autoplay, Omok.dev shows the mute warning icon. Click it to open this help page and review your browser audio settings.")
        ),
        card(span(id := "correspondence")("Correspondence"))(
          p("Correspondence on Omok.dev keeps the same broad idea: turns can happen over a long time instead of in one real-time sitting."),
          p("You can think before each move, but outside engine assistance is still not part of fair play unless a page explicitly says it is a study tool.")
        ),
        card("Rule sets")(
          p("Omok.dev currently supports Freestyle, Renju, and Taraguchi-10 where enabled. Live games and study pages call out the active rule set directly.")
        ),
        card("Rapfi AI")(
          p("Rapfi AI on Omok.dev runs in the browser for supported flows, so the service shell keeps AI play inside Omok.dev.")
        )
      )
    )

  def terms(using Context) =
    pageShell(termsTitle, termsLead)(
      div(cls := "omok-page__stack")(
        card("Fair play")(
          p("Do not use hidden assistance, automation, harassment, or abuse to spoil live omok play.")
        ),
        card("Service use")(
          p("Respect room owners, opponents, and shared service resources. Public rooms, chat, and AI tools should be used in good faith.")
        )
      )
    )

  def info(key: String)(using Context) =
    val (title, lead, body) = key match
      case "privacy" =>
        (
          "Omok.dev privacy",
          "Omok.dev stores the minimum session and game data needed to run rooms, turns, and browser-side features.",
          frag(
            card("What is stored")(
              p("Omok.dev stores the minimum session, room, and game state needed to run live play, rematches, and browser-side features.")
            ),
            card("Browser-side features")(
              p("Rapfi AI and opening guide work can run in your browser. That reduces server-side analysis load and keeps most calculation on your device.")
            )
          )
        )
      case "about" =>
        (
          "About Omok.dev",
          "Omok.dev is an omok-first service for live play, Rapfi AI, and opening study.",
          frag(
            card("Service focus")(
              p("Omok.dev is focused on omok play, Rapfi AI, and opening study across live games, review, and opening exploration.")
            )
          )
        )
      case "thanks" =>
        (
          "Thanks from Omok.dev",
          "Thanks for testing, reporting issues, and helping turn the old shell into an omok-first service.",
          frag(
            card("Thanks")(
              p("Thanks for testing the service, reporting problems, and helping replace legacy routes with omok-first screens.")
            )
          )
        )
      case "changelog" =>
        (
          "Omok.dev changelog",
          "Recent Omok.dev work includes Renju and Freestyle play, Taraguchi opening support, browser Rapfi AI, and the opening guide.",
          frag(
            card("Current Omok.dev work")(
              ul(
                li("Renju and Freestyle live play"),
                li("Taraguchi opening support"),
                li("Browser-side Rapfi AI"),
                li("Opening guide and workbook-backed study")
              )
            )
          )
        )
      case "ads" =>
        (
          "No ads on Omok.dev",
          "Omok.dev focuses on play and study without third-party display ads in the core experience.",
          frag(
            card("Ad policy")(
              p("The Omok.dev core experience is being kept focused on play and study rather than third-party display advertising.")
            )
          )
        )
      case _ =>
        (
          "About Omok.dev",
          "Omok.dev is an omok-first service for live play, Rapfi AI, and opening study.",
          frag(card("Omok.dev")(p("This page has been converted to an omok-first fallback.")))
        )
    pageShell(title, lead)(body)

  def source(using Context) =
    pageShell(sourceTitle, sourceLead)(
      div(cls := "omok-page__stack")(
        card("What changed")(
          ul(
            li("Live omok rooms and rematches"),
            li("Renju, Freestyle, and Taraguchi support"),
            li("Browser-side Rapfi AI"),
            li("Workbook-backed opening guide")
          )
        ),
        card("Current state")(
          p("This source page replaces the old broken CMS route and keeps Omok.dev users inside the omok service shell.")
        )
      )
    )

  def openingHub(detail: String = "")(using Context) =
    pageShell(
      "Omok opening hub",
      leadWithDetail(
        "Legacy opening routes now point to Omok.dev opening study so you stay inside the omok experience.",
        detail
      )
    )(
      div(cls := "omok-page__grid")(
        card("Opening study")(
          p("Use the workbook-backed opening guide to explore fixed-center lines, 4th-move branches, and 5th-move evaluations."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/dev/omok/opening-guide")(openingGuideLabel),
            a(cls := "button button-empty", href := "/ko?any#ai")(playRapfiLabel)
          )
        ),
        card("Live play")(
          p("For live omok opening play, start a room or invite a player instead of using the legacy opening explorer."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#hook")(createRoomLabel),
            a(cls := "button button-empty", href := "/ko?any#friend")(invitePlayerLabel)
          )
        )
      )
    )

  def reviewHub(detail: String = "")(using Context) =
    pageShell(
      "Omok review hub",
      leadWithDetail(
        "Legacy analysis and study routes now stay inside Omok.dev review pages.",
        detail
      )
    )(
      div(cls := "omok-page__grid")(
        card("Review flow")(
          p("Open a live omok game to review moves, or use the opening guide for structured study outside live play."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/")(backToLobbyLabel),
            a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel)
          )
        ),
        card("Next steps")(
          p("If you wanted a practice board or an AI opponent, launch Rapfi AI or start a fresh room from the lobby."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#ai")(playRapfiLabel),
            a(cls := "button button-empty", href := "/ko?any#hook")(createRoomLabel)
          )
        )
      )
    )

  def practiceHub(detail: String = "")(using Context) =
    pageShell(
      "Practice on Omok.dev",
      leadWithDetail(
        "Legacy puzzle, storm, streak, and racer routes now point to Omok.dev practice flows.",
        detail
      )
    )(
      div(cls := "omok-page__grid")(
        card("Practice options")(
          p("Use Rapfi AI for solo play, or use the opening guide for structured opening practice without leaving Omok.dev."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#ai")(playRapfiLabel),
            a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel)
          )
        ),
        card("Live room")(
          p("If you want a real board instead of a legacy puzzle page, create a room or invite another player."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#hook")(createRoomLabel),
            a(cls := "button button-empty", href := "/ko?any#friend")(invitePlayerLabel)
          )
        )
      )
    )

  def updatesHub(detail: String = "")(using Context) =
    pageShell(
      "Omok.dev updates",
      leadWithDetail(
        "Legacy blog and feed routes now point to Omok.dev project notes and service links.",
        detail
      )
    )(
      div(cls := "omok-page__grid")(
        card("Project links")(
          p("Use these Omok.dev pages for source notes, changelog entries, and service background instead of the old community blog shell."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/source")("Source Code"),
            a(cls := "button button-empty", href := "/changelog")("Changelog"),
            a(cls := "button button-empty", href := "/about")("About"),
            a(cls := "button button-empty", href := "/thanks")("Thanks")
          )
        ),
        card("Stay in the service")(
          p("Return to the lobby, open the opening guide, or launch Rapfi AI without falling back to old community pages."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/")(backToLobbyLabel),
            a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel),
            a(cls := "button button-empty", href := "/ko?any#ai")(playRapfiLabel)
          )
        )
      )
    )

  def competitiveHub(detail: String = "")(using Context) =
    pageShell(
      "Competitive play on Omok.dev",
      leadWithDetail(
        "Legacy broadcast and FIDE-style routes now point to Omok.dev live play and player hubs.",
        detail
      )
    )(
      div(cls := "omok-page__grid")(
        card("Where to go now")(
          p("Use live games, player pages, and correspondence help from inside Omok.dev instead of legacy broadcast pages."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/games")(gamesTitle),
            a(cls := "button button-empty", href := "/player")(playersTitle),
            a(cls := "button button-empty", href := "/faq#correspondence")("Correspondence help")
          )
        ),
        card("Service focus")(
          p("Competitive omok on this build is centered on live rooms, review, and player-focused fallback hubs while the old record pages are phased out.")
        )
      )
    )

  def insightsHub(detail: String = "")(using Context) =
    pageShell(
      "Omok player insights",
      leadWithDetail(
        "Player insight routes are being rebuilt for omok-specific statistics and dashboards.",
        detail
      )
    )(
      div(cls := "omok-page__grid")(
        card("Current status")(
          p("This Omok.dev build does not expose the legacy insight dashboards. Use player and game hubs while omok-specific insights are being rebuilt.")
        ),
        card("Useful links")(
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/player")(playersTitle),
            a(cls := "button button-empty", href := "/games")(gamesTitle),
            a(cls := "button button-empty", href := "/")(backToLobbyLabel)
          )
        )
      )
    )

  def analysisHome(using Context) =
    pageShell(analysisTitle, analysisLead)(
      div(cls := "omok-page__stack")(
        card("Review flow")(
          p(analysisUnavailable),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/")(backToLobbyLabel),
            a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel)
          )
        )
      )
    )

  def analysisEmbed(using Context) =
    div(cls := "omok-embed-fallback")(
      strong(analysisTitle),
      span(" "),
      span(analysisUnavailable)
    )

  def analysisGame(pov: Pov, state: OmokRoundState)(using Context) =
    val turnLabel = colorLabel(state.activeSeat.toString)
    pageShell(
      analysisTitle,
      frag(
        analysisLead,
        " ",
        strong(pov.gameId.value),
        "."
      )
    )(
      div(cls := "omok-page__actions omok-page__actions--inline")(
        a(cls := "button button-metal", href := routes.Round.omokClaim(pov.fullId))(s"Back to game"),
        a(cls := "button button-empty", href := "/")(backToLobbyLabel)
      ),
      div(cls := "omok-page__grid")(
        card("Summary")(
          div(cls := "omok-page__kv")(
            span("Rule set"),
            strong(ruleSetLabel(state.ruleSet.toString))
          ),
          div(cls := "omok-page__kv")(
            span("State"),
            strong(statusLabel(state))
          ),
          div(cls := "omok-page__kv")(
            span("Turn"),
            strong(turnLabel)
          ),
          div(cls := "omok-page__kv")(
            span("Ply"),
            strong(state.position.ply.toString)
          ),
          div(cls := "omok-page__kv")(
            span(lastMoveLabel),
            strong(state.moves.lastOption.map(_.pos.key).getOrElse("None"))
          )
        ),
        card(moveListTitle)(
          if state.moves.nonEmpty then
            table(cls := "omok-page__moves")(
              tbody(moveRows(state))
            )
          else p("No omok moves were stored for this review.")
        )
      ),
      if state.opening.history.nonEmpty then
        card(openingLogTitle)(
          ol(cls := "omok-page__history")(
            state.opening.history.map(item => li(item))
          )
        )
      else frag()
    )

  def userProfile(username: String, section: String = "overview")(using Context) =
    val sectionTitle = section match
      case "games"    => "Games"
      case "perf"     => "Stats"
      case "download" => "Exports"
      case "tv"       => "Live"
      case _          => "Overview"
    pageShell(profileTitle, profileLead)(
      div(cls := "omok-page__grid")(
        card("Player")(
          div(cls := "omok-page__kv")(
            span("Username"),
            strong(username)
          ),
          div(cls := "omok-page__kv")(
            span("Section"),
            strong(sectionTitle)
          )
        ),
        card("Next action")(
          p("Player profile links stay inside Omok.dev. Use an invite link, start a room, or return to the lobby from here."),
          div(cls := "omok-page__actions omok-page__actions--inline")(
            a(cls := "button button-metal", href := "/ko?any#friend")(invitePlayerLabel),
            a(cls := "button button-empty", href := "/ko?any#hook")(createRoomLabel)
          )
        )
      )
    )

  def friendInvite(
      sessionId: String,
      shareUrl: String,
      stateUrl: String,
      confirmUrl: String,
      isHost: Boolean,
      guestJoined: Boolean,
      ruleSet: String,
      timeControl: String,
      gameMode: String,
      side: String,
      requestedUser: Option[String]
  )(using Context) =
    Page("Invite a player")
      .copy(fullTitle = s"Invite a player - $brandName".some)
      .wrap: body =>
        main(cls := "page-menu omok-page")(
          div(cls := "page-menu__content box box-pad omok-page__shell")(body)
        )
      .transformHead(head => frag(head, link(rel := "stylesheet", href := styleAsset)))
      (
        frag(
          div(cls := "omok-page__hero")(
            h1(cls := "box__top")("Invite a player"),
            p(cls := "omok-page__lead")(
              requestedUser.fold("Share this invite link. The first other player to open it will be matched immediately and the room will start automatically.") { user =>
                s"Share this invite link with $user. As soon as they open it, the omok room starts automatically."
              }
            ),
            div(cls := "omok-page__actions")(
              a(cls := "button button-metal", href := "/")(backToLobbyLabel),
              a(cls := "button button-empty", href := "/omok/solo")("Solo board")
            )
          ),
          div(
            id := "omok-friend-room-app",
            attr("data-session-id") := sessionId,
            attr("data-state-url") := stateUrl,
            attr("data-confirm-url") := confirmUrl,
            attr("data-share-url") := shareUrl,
            attr("data-is-host") := isHost.toString
          )(
            div(cls := "omok-page__grid")(
              card("Invite link")(
                p("Open this link in another browser or share it directly. The room stays inside Omok.dev."),
                div(cls := "omok-page__share")(
                  input(
                    id := "omok-friend-share-url",
                    cls := "omok-page__mono",
                    readonly := true,
                    value := shareUrl
                  ),
                  button(id := "omok-friend-copy", cls := "button button-empty", attr("type") := "button")("Copy link")
                )
              ),
              card("Room settings")(
                div(cls := "omok-page__kv")(span("Rule set"), strong(ruleSet)),
                div(cls := "omok-page__kv")(span("Time"), strong(timeControl)),
                div(cls := "omok-page__kv")(span("Mode"), strong(gameMode)),
                div(cls := "omok-page__kv")(span("Host side"), strong(side))
              ),
              card("Status")(
                div(id := "omok-friend-status-pill", cls := "omok-page__status-pill")(if guestJoined then "Starting room" else "Waiting for another player"),
                p(id := "omok-friend-status-text")(
                  if isHost then
                    if guestJoined then "The other player opened the invite link. The omok room is starting automatically."
                    else "Stay on this page. The first other player to open the invite link will be matched immediately."
                  else "Joining the room. This should redirect automatically in a moment."
                ),
                div(cls := "omok-page__actions omok-page__actions--inline")(
                  span(id := "omok-friend-confirm-note", cls := "omok-page__hint")(
                    if isHost then
                      if guestJoined then "Starting automatically."
                      else "No extra confirm step."
                    else "No extra confirm step."
                  )
                )
              )
            )
          ),
          script(attr("type") := "module", src := friendRoomScript)
        )
      )

  def soloBoard(using Context) =
    Page("Solo board")
      .copy(fullTitle = s"Solo board - $brandName".some)
      .wrap: body =>
        main(cls := "page-menu omok-solo-page")(
          div(cls := "page-menu__content box box-pad omok-page__shell")(body)
        )
      .transformHead(head => frag(head, link(rel := "stylesheet", href := styleAsset), link(rel := "stylesheet", href := soloStyleAsset)))
      (
        frag(
          div(cls := "omok-page__hero")(
            h1(cls := "box__top")("Solo board"),
            p(cls := "omok-page__lead")(
              "Place stones freely on an empty 15x15 board. Black and white alternate automatically, and Undo or Reset lets you explore quickly."
            ),
            div(cls := "omok-page__actions")(
              a(cls := "button button-metal", href := "/")(backToLobbyLabel),
              a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel),
              a(cls := "button button-empty", href := "/omok/solo2")("Solo board 2 beta")
            )
          ),
          div(id := "omok-solo-board-app"),
          script(attr("type") := "module", src := soloScriptAsset)
        )
      )

  def soloBoard2(using Context) =
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
              "Build move trees, choose branches, attach short text labels, and now import or export the renju-edit-v2 style serialized board file through the server-side JVM bridge."
            ),
            div(cls := "omok-page__actions")(
              a(cls := "button button-metal", href := "/")(backToLobbyLabel),
              a(cls := "button button-empty", href := "/omok/solo")("Solo board 1"),
              a(cls := "button button-empty", href := "/dev/omok/opening-guide")(openingGuideLabel)
            )
          ),
          div(id := "omok-solo-board-2-app"),
          script(attr("type") := "module", src := solo2ScriptAsset)
        )
      )
