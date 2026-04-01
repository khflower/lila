```md
# Omok Implementation Roadmap

## MVP Scope

The MVP should deliver a complete two-player Omok game loop with deterministic rules, a playable UI, and a minimal game session service. The target is a stable internal release rather than a feature-rich public launch.

Included in MVP:

- 15x15 board rendering and interaction
- Turn-based two-player play
- Legal move validation on the server-side game engine
- Win detection for five in a row
- Basic game session lifecycle: create game, join game, start, play, finish
- Turn synchronization between clients
- Basic reconnect/resume for active games
- Minimal match history metadata for debugging and QA
- Clear game-end states: win, resign, disconnect timeout if already part of current platform norms
- Focused QA coverage around rule correctness and turn ordering

MVP explicitly assumes:

- Human vs human first
- Single ruleset first
- Internal or lightweight identity model only
- Basic UI polish only

## Exact Phase Ordering

The implementation should follow this order and should not parallelize phases until the dependency boundary is clear.

### Phase 0: Contract and Boundaries

- Freeze the MVP ruleset and service boundaries
- Define canonical game state shape
- Define move event payloads and game-end payloads
- Define non-goals for MVP so downstream work does not expand scope

Exit criteria:

- One agreed state model
- One agreed ruleset
- One agreed API/event contract

### Phase 1: Core Rule Engine

- Implement board model
- Implement move application
- Implement legal move checks
- Implement turn ownership
- Implement five-in-a-row detection
- Add exhaustive unit tests for edge cases and regression cases

Exit criteria:

- Pure deterministic engine passes full rule test suite
- No UI or transport dependency in the engine

### Phase 2: Game Session Service

- Wrap the rule engine in a game/session domain service
- Add create/join/start/play/resign/reconnect flows
- Add persistence for active session state if required by platform architecture
- Enforce server-authoritative move ordering
- Add integration tests for race conditions and invalid actions

Exit criteria:

- One backend path can host a full game from start to finish
- Session service rejects illegal or out-of-order moves

### Phase 3: Playable Client

- Implement board UI and stone placement interactions
- Bind client to session state and move events
- Render turn state, win state, and reconnect state
- Prevent local invalid actions before sending requests
- Add basic loading/error states

Exit criteria:

- Two players can complete a full game through the real client
- UI state stays in sync with server state

### Phase 4: Hardening and Ship Readiness

- Add reconnection verification
- Add telemetry/logging needed for QA and triage
- Add smoke tests for the full game path
- Close UX gaps that block normal play
- Run focused bug bash on duplicate moves, stale state, disconnects, and endgame behavior

Exit criteria:

- Internal QA can repeatedly finish games without state corruption
- Top operational failure cases are observable and debuggable

## What To Defer

These items should be deferred until after the MVP is stable:

- AI opponent
- Matchmaking beyond simple invite/direct join
- Ranked play, MMR, seasons, or ladders
- Spectators
- Chat and social features
- Rich replay viewer
- Variant rule support beyond the initial selected ruleset
- Tournament structures
- Cosmetics, themes, sound, and animation-heavy polish
- Advanced anti-cheat beyond basic server authority and validation
- Deep analytics and progression systems
- Cross-device long-term history UX beyond minimal internal debugging needs

## First 3 Coding Milestones

### Milestone 1: Rule Engine Complete

Deliverables:

- `GameState` model
- Board coordinate model
- Move validation
- Turn progression
- Win detection
- Unit test matrix for core rules

Why first:

- Everything else depends on deterministic rule behavior

### Milestone 2: Server-Authoritative Session Flow

Deliverables:

- Session aggregate/service around the rule engine
- Endpoints or event handlers for create/join/play/resign/reconnect
- Integration tests for move ordering and invalid action rejection

Why second:

- It turns the pure engine into a real playable game path and fixes backend semantics before UI work spreads assumptions

### Milestone 3: End-to-End Playable Client

Deliverables:

- Interactive board UI
- Real-time or request/response session sync
- Turn/game-over/reconnect UX
- Smoke test proving two-player completion path

Why third:

- This is the earliest milestone that proves the MVP is actually shippable to internal testers

## Recommended Branch Strategy For Parallel Implementation

Use one protected integration branch for the MVP plus short-lived workstream branches with clear ownership.

Recommended structure:

- `omok/mvp` as the integration branch for all MVP work
- `omok/rules-engine` for Phase 1 domain logic and tests
- `omok/session-service` for Phase 2 backend orchestration
- `omok/client-board` for Phase 3 board UI and client state handling
- `omok/hardening` for Phase 4 telemetry, reconnect fixes, and QA issues after the first playable path exists

Recommended merge order:

1. `omok/rules-engine` merges first
2. `omok/session-service` rebases on `omok/mvp` after the rule engine lands
3. `omok/client-board` can begin against mocked contracts, then rebase once the session service contract is stable
4. `omok/hardening` starts only after the end-to-end path exists

Parallel implementation guidance:

- Keep the rule engine isolated and dependency-free so other branches can mock it cleanly
- Freeze payload contracts before parallel backend/client work
- Avoid long-lived feature divergence; rebase frequently onto `omok/mvp`
- Route bug fixes to the owning branch first, then merge forward into `omok/mvp`
- Cut release candidates only from `omok/mvp`, not from workstream branches

## Recommended Execution Summary

The shortest path to a reliable Omok MVP is: freeze the ruleset, build the deterministic engine, wrap it in a server-authoritative session service, then ship the thinnest client that can complete a real game. Everything that does not directly improve correctness, session integrity, or basic playability should be deferred until after internal validation.
```
