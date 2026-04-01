# Rapfi Feasibility For Early Omok Use

## Bottom line

Rapfi looks like a practical early-phase engine option if the goal is:

- a strong CPU-only bot quickly
- server-side analysis via a child process
- no early investment in training infrastructure

It does **not** look like the best fit if the goal is:

- a tiny embedded engine with minimal deployment baggage
- an in-process library integration
- a permissive-license dependency with low compliance overhead

My overall read is: **good candidate for a strong external engine service, not ideal as a deeply embedded lightweight library**.

## Scope and evidence

This report is based on the public upstream Rapfi repository pages corresponding to the checked-out repo layout, because shell access to inspect the local clone directly was unavailable in this session.

Primary sources used:

- https://github.com/dhbloo/rapfi
- https://github.com/dhbloo/rapfi/releases
- https://github.com/dhbloo/gomoku-calculator
- https://raw.githubusercontent.com/dhbloo/rapfi/master/Rapfi/CMakeLists.txt
- https://raw.githubusercontent.com/dhbloo/rapfi/master/.gitmodules

## 1. Build and run expectations

### Repository / source layout

The repo is split into an engine plus related subprojects:

- `Rapfi/`: engine source
- `Gomocalc/`: web frontend subproject
- `Trainer/`: NNUE training subproject
- `Networks/`: network/config submodule

Within `Rapfi/`, the engine layout is reasonably clean and suggests a mature standalone executable rather than a reusable library:

- `command/`
- `core/`
- `database/`
- `emscripten/`
- `eval/`
- `external/`
- `game/`
- `search/`
- `tuning/`
- top-level engine files such as `main.cpp`, `config.cpp`, `internalConfig.cpp`

`CMakeLists.txt` builds a single executable target named `rapfi` and sets the output binary name to `pbrain-rapfi`. That strongly suggests the intended runtime shape is a standalone engine process speaking Gomocup/Piskvork protocol, not a library API.

### Build expectations

Rapfi expects:

- CMake
- a C++17-capable compiler
- Clang/GCC/MSVC support, with Clang explicitly recommended

Documented native build path:

```bash
cd Rapfi
cmake --preset x64-clang-Native
cmake --build build/x64-clang-Native
```

The documented defaults favor:

- multi-threading
- host-native CPU instruction targeting on x86-64

The build system exposes a lot of CPU-feature flags:

- `USE_SSE`
- `USE_AVX2`
- `USE_AVX512`
- `USE_BMI2`
- `USE_VNNI`
- `USE_NEON`
- `USE_NEON_DOTPROD`
- `USE_WASM_SIMD`
- `USE_WASM_SIMD_RELAXED`

There are also deployment-relevant slimming flags:

- `NO_MULTI_THREADING`
- `NO_COMMAND_MODULES`
- `NO_PREFETCH`

Inference: for a conservative production build on generic cloud CPUs, you would likely avoid the native preset and instead pin a safer ISA level such as SSE/AVX2 on x86-64 or NEON on ARM64.

### Runtime expectations

Rapfi does not appear self-contained at runtime. The README says the engine expects:

- the executable
- a preset config file
- classical evaluation weights
- NNUE evaluation weights

These are expected to sit next to the executable so Rapfi can auto-load them.

That means deployment is not just "drop in one binary". The minimal production artifact is more like:

- `pbrain-rapfi`
- config file(s)
- weight file(s)

This is still workable, but it is more operationally involved than a tiny heuristic bot.

### WebAssembly path

Rapfi also has a documented Emscripten/WebAssembly build. The engine’s CMake exports `_main` and `_gomocupLoopOnce` in wasm builds, and the separate `gomoku-calculator` project uses Rapfi in-browser.

That is evidence Rapfi can run outside native desktop GUIs, but for an early server deployment I would still treat native child-process execution as the primary path. Wasm is interesting later for browser-side analysis or demo play.

## 2. Likely integration shape

### Most likely shape: managed child process

The README says Rapfi communicates with a UI or match manager by sending and receiving text commands over a process pipe using the Piskvork protocol by default. Given that and the `pbrain-rapfi` executable target, the obvious integration model is:

1. Start `pbrain-rapfi` as a subprocess from the server.
2. Keep stdin/stdout pipes open.
3. Send `START`, `BOARD` / `TURN`, `INFO`, and game lifecycle commands per the Gomocup/Piskvork flow.
4. Parse engine responses and map them back into the site game state.
5. Reuse the process for a game or an analysis session, then recycle it.

This is a good early-phase shape because it avoids C++ ABI embedding, custom FFI, or modifying the engine internals.

### What the server wrapper likely needs

- a process pool, not one process per HTTP request
- request serialization per engine process
- hard move-time and wall-time timeouts
- crash detection and fast respawn
- a board/rule adapter between site coordinates and Gomocup coordinates
- startup validation that config and weight files were loaded correctly

### What likely should not be first choice

In-process embedding looks unattractive early on because:

- the build system is centered on `add_executable`, not a reusable engine library
- engine internals are spread across search/eval/game/command modules
- you would own a larger C++ integration surface immediately

If the site only needs "give me a move" and "analyze this position", subprocess wrapping is the lower-risk option.

### Lean deployment variant

The CMake options suggest a slimmer server build is possible:

- disable command modules with `NO_COMMAND_MODULES=ON` if you do not need self-play / tuning / data-prep style modules
- consider `NO_MULTI_THREADING=ON` for very cheap low-concurrency workers or deterministic single-thread workers
- target conservative CPU features rather than `Native`

That looks better aligned with an early omok site than the full strongest-engine build.

## 3. Strengths and weaknesses for early omok site use

### Strengths

- **Strong out-of-the-box engine**: the project positions itself as one of the strongest Gomoku/Renju engines, so the site gets credible bot strength immediately.
- **CPU-first design**: classical + NNUE evaluation is documented as CPU/SIMD oriented, with x86-64 and ARM64 support. Optional ONNX/GPU paths exist in CMake, but the default engine does not depend on them.
- **Server-process-friendly protocol**: text protocol over stdin/stdout is easy to supervise from a web service.
- **Configurable for different hardware**: SIMD feature flags and ARM64/wasm support reduce lock-in to one deployment target.
- **Evidence of non-desktop use**: the separate Gomocalc project demonstrates Rapfi outside a native GUI context.
- **No early training dependency**: usable pretrained weights/configs already exist via the `Networks` submodule/repo.

### Weaknesses

- **GPLv3 license**: this is the biggest non-technical issue. If the omok site distributes Rapfi binaries, bundles modified builds, or otherwise ships it in a way that triggers GPL obligations, compliance work is required. This may be acceptable, but it is not lightweight.
- **Not a library-first engine**: the build shape favors a standalone executable, so deep integration is heavier than with an embeddable engine library.
- **Runtime assets required**: config + evaluation weights are part of deployment, not just a binary.
- **Performance tuning is hardware-sensitive**: the documented default native preset is fast, but risky for generic heterogeneous fleets. You need intentional build targeting.
- **Potentially heavier than needed for day-one usage**: for simple casual bot play, Rapfi may be stronger and more operationally complex than necessary.
- **Gomoku/Renju protocol fit, not omok-native API fit**: integration will need an adapter layer for board/rule conventions and site semantics.

### Practical early-use fit

For these early use cases, Rapfi looks good:

- "Play against bot"
- "Analyze this position"
- "Run a stronger analysis worker offline"

For these early use cases, Rapfi looks less ideal:

- massive low-cost concurrent bot games on very small CPU budgets
- deep engine customization inside the main app process
- shipping a permissively licensed engine inside a broader product without copyleft concerns

## 4. Is lightweight pretraining / fine-tuning necessary early?

Short answer: **probably no**.

Reasons:

- The project already ships with pretrained evaluation assets and a dedicated `Networks` repo.
- The engine is clearly intended to be useful immediately after build + weights/config placement.
- There is a separate `Trainer` subproject, which implies training is a specialized workflow, not a prerequisite for normal deployment.
- Early site needs are usually product/integration problems, not model-quality problems.

### When training would start to make sense later

- you want a deliberately weaker or more human-like house bot personality
- you want style-specific openings or pedagogical analysis behavior
- you are optimizing for a specific board size/rule distribution that differs from the engine’s main target
- you have enough usage data to justify tuning toward your own traffic

### Early recommendation

Use stock Rapfi first. Spend effort on:

- stable server wrapper
- move-time limits
- process supervision
- concurrency strategy
- UX around analysis depth / latency

Only revisit fine-tuning if product needs show a clear gap. Early on, training would almost certainly be premature complexity.

## Recommendation

For the lila -> omok port, Rapfi appears to be a **credible early engine/bot/analysis option** if you treat it as an external engine service:

- build a conservative CPU-only binary
- package weights/config with it
- run it behind a supervised subprocess wrapper
- avoid engine modifications at first

I would classify it as:

- **Engine strength**: strong
- **Integration difficulty**: moderate
- **Operational weight**: moderate
- **CPU-only suitability**: good
- **Need for early pretraining/fine-tuning**: low

If the main decision criterion is "strongest practical thing we can stand up early without building our own engine/training pipeline", Rapfi looks like a reasonable choice.
