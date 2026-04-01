The assigned file write could not be completed in this environment because every local write path (`exec_command` and `apply_patch`) failed with the same sandbox namespace error (`bwrap: No permissions to create a new namespace`). I’m providing the finished markdown below so the work product is still complete.

```md
# Worker 11 Report: Lightweight OSS Omok/Gomoku/Renju Options

Date: 2026-04-01

## Bottom line

There are credible lighter-weight OSS paths that can cover a meaningful part of an omok product without pulling over most of lila.

The strongest near-term path is:

1. `dhbloo/gomoku-calculator` for the board/UI shell
2. `dhbloo/rapfi` for strong Gomoku/Renju engine support
3. A thin room/presence service if we need online multiplayer rather than solo play / analysis only

That combination is much lighter than a lila port because it is domain-specific, already speaks Gomoku/Renju, and avoids lila's chess-specific game model, account/community features, moderation surface, tournament/round architecture, and Scala/Play/Akka-era operational weight.

None of the projects below is a clean full replacement for all of lila's platform features. Several can absolutely accelerate or replace an MVP scope.

## Candidate projects

| Project | What it gives us | Why it is lighter than lila | Replace or accelerate? | Notes |
| --- | --- | --- | --- | --- |
| `dhbloo/gomoku-calculator` | Browser UI for Gomoku/Renju; static deploy; already wired for Rapfi WASM; good for AI play and analysis | Static frontend, no big backend required, no chess baggage | Strong accelerator; possible MVP replacement for solo/analysis product | License was not clearly surfaced in the GitHub page scrape; verify before reuse |
| `dhbloo/rapfi` | Strong engine; Gomoku/Renju rules; Piskvork protocol; can compile native or WebAssembly; includes Gomocalc subproject | Engine-only core, focused scope, no platform stack | Strong accelerator; not a standalone replacement by itself | GPL-3.0 |
| `dhbloo/Rapfi-gomocup` | Older Rapfi engine line with Gomocup/Piskvork protocol and MIT license | Still engine-focused and much smaller than lila | Accelerator only; useful if permissive licensing matters more than latest engine strength | Older 2018 branch; superseded by `rapfi` |
| `scheng20/gomoku-online` | Thin web multiplayer stack with rooms and real-time play | React + Node + Express + Socket.IO only; far smaller product surface | Good accelerator for invite-room multiplayer MVP | Appears hobby/demo quality rather than hardened platform infra |
| `rdragon/gomoku-ai` | Self-hostable play-vs-bot app; Docker path; separate Node browser server | Small codebase, one game, one bot, one web surface | Accelerator; possible replacement for "play vs AI" MVP | Gomoku, not Renju-focused |
| `plastovicka/Piskvork` | Mature GUI/manager for engines; Renju/Caro rule support; engine protocol ecosystem | Desktop manager instead of full web platform | Accelerator for engine integration, testing, and protocol validation; not product replacement | Windows-centric GUI |
| `wind23/piskvork_renju` | Renju-capable Piskvork fork / patch | Tiny targeted delta instead of platform work | Accelerator only | Useful mainly as rule/protocol reference |
| `Joker2770/qpiskvork` | Cross-platform engine manager; multiple rules; supports engines including Rapfi/Yixin | Still much smaller than lila; focused manager, not a whole site | Accelerator only, unless a desktop-first tool is acceptable | Source details here came from Snapcraft metadata rather than a direct README fetch |
| `cloxnu/Omega_Gomoku_AI` | Python MCTS engine, training pipeline, visual web server, configurable board size / n-in-a-row | Single-purpose Python app, no platform complexity | Accelerator for experimentation; weak product replacement | More research/demo oriented than production oriented |
| `gkoos/gomoku` | Very small modern Vite frontend for local human-vs-AI play | Frontend-only, vanilla JS, MIT | Accelerator for board UX / front-end structure | Good reference, but not enough by itself |

## Detailed assessment

### 1. `dhbloo/gomoku-calculator`

Repo: <https://github.com/dhbloo/gomoku-calculator>

What it gives us:

- A browser-based Gomoku/Renju interface
- Build/deploy story that ends in static hosting
- Existing integration pattern for Rapfi compiled to WebAssembly
- A product surface already aimed at AI play and position analysis

Why it is lighter than lila:

- It is a static frontend, not a large server product
- It is already domain-native to Gomoku/Renju
- No need to untangle chess-specific concepts from lila
- No need to inherit lila accounts, moderation, tournaments, relay, studies, puzzle infra, etc.

Could it replace or accelerate the plan?

- It can replace a large part of the plan if the target is "browser omok with bot play and analysis"
- It does not replace a full online community platform
- It is the best UI-first accelerator I found

Important source notes:

- The README says to compile Rapfi with Emscripten and place the engine builds under `public/build`
- After `npm run build`, deployment is just copying `dist` to a static file server
- Multi-threaded WASM needs COEP/COOP headers
- The older `gomocalc.github.io` mirror describes the product as a tool for Gomoku/Renju players to analyze positions and play against AI, powered by Rapfi and running via WebAssembly

Implication:

- This is the cleanest path to a serious playable web omok experience without porting lila's server stack

### 2. `dhbloo/rapfi`

Repo: <https://github.com/dhbloo/rapfi>

What it gives us:

- Strong Gomoku/Renju engine
- Native and WASM build path
- Piskvork protocol compatibility
- Existing connection points to GUI/board frontends
- `Gomocalc` bundled in the repository as a related subproject

Why it is lighter than lila:

- It is an engine, not a platform
- It gives us game strength and rules support directly, without platform overhead
- It already fits the Gomoku/Renju ecosystem instead of needing adaptation from chess

Could it replace or accelerate the plan?

- It cannot replace the full product alone
- It can replace most of the hardest game-logic / engine work
- It is the best engine-side accelerator

Important source notes:

- Rapfi describes itself as "a free and powerful Gomoku/Renju engine"
- It explicitly mentions compatible GUIs including Piskvork, qpiskvork, and Yixinboard
- The repo structure explicitly includes `Gomocalc`
- The README includes an Emscripten/WebAssembly build path

Implication:

- If we do not want a heavy port, we should strongly prefer building around Rapfi rather than rebuilding engine integration from scratch

### 3. `dhbloo/Rapfi-gomocup`

Repo: <https://github.com/dhbloo/Rapfi-gomocup>

What it gives us:

- Earlier Rapfi engine line
- Gomocup/Piskvork ecosystem compatibility
- MIT license instead of GPL-3.0

Why it is lighter than lila:

- Same basic reason as Rapfi: this is an engine, not a site platform
- It is easier to reuse in mixed-license product work than GPL code

Could it replace or accelerate the plan?

- Accelerator only
- Worth considering if licensing pushes us away from modern Rapfi, but it is clearly the older line

Implication:

- This is the licensing escape hatch, not the best technical base

### 4. `scheng20/gomoku-online`

Repo: <https://github.com/scheng20/gomoku-online>

What it gives us:

- Real-time online multiplayer
- Room creation / join flow
- React frontend
- Node/Express backend
- Socket.IO event model

Why it is lighter than lila:

- Simple full-stack web app instead of a large, multi-feature game platform
- Narrowly focused on one game mode
- Operational footprint is far smaller

Could it replace or accelerate the plan?

- It could replace the current plan if the plan is only "real-time invite-room omok on the web"
- It will not replace lila-style public platform features
- Very useful as a reference implementation for rooms, turn sync, and state propagation

Important source notes:

- The README states it is an online multiplayer port built with React, Node, Express, and Socket.IO
- Features include real-time multiplayer and join/create rooms with a 6-digit code

Implication:

- If we need online human-vs-human soon, this is a good thin scaffold to borrow from or partially reuse

### 5. `rdragon/gomoku-ai`

Repo: <https://github.com/rdragon/gomoku-ai>

What it gives us:

- Play-vs-bot browser experience
- A self-hosting path via Docker
- Split between command-line engine and Node browser server
- Alpha-beta + threat-space-search bot

Why it is lighter than lila:

- One bot, one game, one small web server
- No platform/community layers
- Easy local/self-host setup

Could it replace or accelerate the plan?

- It can replace the plan only for a small "play against AI in browser" MVP
- It is a useful reference for a minimal web-serving pattern around an engine
- It is less compelling than Rapfi + Gomocalc if Renju support matters

Implication:

- Good fallback if we want the simplest self-hosted bot experience and do not need a broader ecosystem

### 6. `plastovicka/Piskvork`

Repo: <https://github.com/plastovicka/Piskvork>

What it gives us:

- Established engine manager / GUI
- Rule support including Renju and Caro
- Access to the broader Gomocup-style engine ecosystem
- Good harness for engine integration and debugging

Why it is lighter than lila:

- Desktop GUI instead of full web product
- Focused on game management and engine communication
- No server platform required

Could it replace or accelerate the plan?

- Not a replacement for a web omok site
- Very useful for accelerating engine protocol work, rules validation, and local testing

Implication:

- Valuable as tooling around the product, not likely as the product itself

### 7. `wind23/piskvork_renju`

Repo: <https://github.com/wind23/piskvork_renju>

What it gives us:

- A concrete Renju-specific adaptation of Piskvork
- Evidence of how Renju rule signaling is passed to engines

Why it is lighter than lila:

- This is essentially a focused compatibility patch, not a platform

Could it replace or accelerate the plan?

- Accelerator only
- Useful if Renju rule handling is a concern and we want reference behavior quickly

Important source notes:

- The README says the Renju patch adds `INFO rule 4` signaling to the brain/engine

Implication:

- Good reference material, not a product base

### 8. `Joker2770/qpiskvork`

Project page used: <https://snapcraft.io/install/qpiskvork/ubuntu>

What it gives us:

- Cross-platform engine manager
- Many supported rules: free-style, standard Gomoku, Renju, Caro, swap2
- Support for several engines including Rapfi
- A more Linux-friendly manager story than classic Piskvork

Why it is lighter than lila:

- Narrow desktop manager scope
- No site/community/server complexity

Could it replace or accelerate the plan?

- Accelerator only for engine-management and rules work
- Not a realistic replacement for a modern web product unless desktop-first is acceptable

Confidence note:

- This assessment is based on Snapcraft metadata because I could not fetch the GitHub README directly through the available browser path

### 9. `cloxnu/Omega_Gomoku_AI`

Repo: <https://github.com/cloxnu/Omega_Gomoku_AI>

What it gives us:

- Python MCTS engine
- Training pipeline
- Visual game interface
- Web server mode
- Configurable board size and n-in-a-row

Why it is lighter than lila:

- Research/demo application instead of big production platform
- Single project with AI + UI bundled together

Could it replace or accelerate the plan?

- It can accelerate experimentation or internal prototyping
- It is not the best foundation for a production-grade public omok service

Implication:

- Good if the current plan still has unresolved questions around AI/training and we want a smaller sandbox

### 10. `gkoos/gomoku`

Repo: <https://github.com/gkoos/gomoku>

What it gives us:

- Small modern browser board/UI
- Vanilla JS + Vite
- Human-vs-AI flow
- MIT-licensed code

Why it is lighter than lila:

- Tiny frontend app
- No backend platform at all
- Easy to study and selectively transplant

Could it replace or accelerate the plan?

- Not a full replacement
- Useful accelerator for front-end board behavior, move UX, and small AI hooks

Implication:

- Best used as a UI reference or donor for a very small client, not as the main architecture

## What is actually lighter than lila?

The projects above are lighter for a few recurring reasons:

- They are omok/gomoku/renju-native instead of chess-native
- Several are static or near-static web apps
- The multiplayer examples use simple Node + Socket.IO instead of a broad platform stack
- Engine/tooling projects stay focused on protocol, rules, and analysis instead of identity/community/tournament problems

In practice, "lighter than lila" means:

- smaller codebase
- fewer moving parts
- narrower product surface
- less ops burden
- less domain translation from chess concepts

## Replacement value against the current plan

### Could replace the current plan

Only if the current target is intentionally small:

- solo play vs AI
- analysis board
- invite-room multiplayer
- maybe light persistence

For that scope, a stack like `Gomoku Calculator + Rapfi + thin room service` could reasonably replace a heavy lila port.

### Could accelerate but not replace

If the target includes anything close to lila's full platform shape:

- accounts and social graph
- moderation/admin surface
- public lobby and pairing
- tournaments and event workflows
- rich persistence/history/studies equivalents
- large-scale spectator features

Then these projects are accelerators, not replacements.

## Recommendation

### Recommended path

Use a hybrid lightweight approach instead of a lila-first port:

1. Start from `dhbloo/gomoku-calculator` for the board/UI and browser execution model
2. Use `dhbloo/rapfi` as the primary engine path
3. Add a very thin multiplayer service only if online rooms are required
4. Use `Piskvork` / `qpiskvork` as integration tooling and protocol references, not as the end-user product

### Why this is the best option

- It gets us a serious omok experience faster
- It keeps the code focused on omok rather than on adapting chess abstractions
- It minimizes the chance that we spend most of the project porting platform infrastructure nobody asked for
- It preserves the option to grow into a larger service later

### Recommendation by scenario

- If the goal is `analysis + AI play`: choose `Gomoku Calculator + Rapfi`
- If the goal is `small online multiplayer MVP`: add ideas/code from `scheng20/gomoku-online`
- If the goal is `GPL is unacceptable`: evaluate `Rapfi-gomocup` as a weaker but permissively licensed fallback
- If the goal is `engine experimentation / training`: inspect `Omega_Gomoku_AI`
- If the goal is `desktop tooling / rules / protocol validation`: use `Piskvork` or `qpiskvork`

## Risks and caveats

- License review is required before code reuse, especially around GPL projects (`Rapfi`, `Piskvork`, `piskvork_renju`, likely `qpiskvork`)
- `gomoku-calculator` license needs explicit verification before product reuse
- Multiplayer demos are much less mature than lila in reliability and product surface
- Renju rules and matchmaking/product requirements should be clarified early, because they affect whether a simple Gomoku MVP is enough

## Sources

- `dhbloo/gomoku-calculator`: <https://github.com/dhbloo/gomoku-calculator>
- `gomocalc.github.io` mirror: <https://github.com/gomocalc/gomocalc.github.io>
- `dhbloo/rapfi`: <https://github.com/dhbloo/rapfi>
- `dhbloo/Rapfi-gomocup`: <https://github.com/dhbloo/Rapfi-gomocup>
- `scheng20/gomoku-online`: <https://github.com/scheng20/gomoku-online>
- `rdragon/gomoku-ai`: <https://github.com/rdragon/gomoku-ai>
- `plastovicka/Piskvork`: <https://github.com/plastovicka/Piskvork>
- `wind23/piskvork_renju`: <https://github.com/wind23/piskvork_renju>
- `qpiskvork` project metadata: <https://snapcraft.io/install/qpiskvork/ubuntu>
- `cloxnu/Omega_Gomoku_AI`: <https://github.com/cloxnu/Omega_Gomoku_AI>
- `gkoos/gomoku`: <https://github.com/gkoos/gomoku>
```
