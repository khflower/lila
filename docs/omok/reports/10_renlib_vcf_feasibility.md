# RenLib VCF Feasibility Assessment

## Scope

This assessment focuses on whether `RenLib` looks useful for the lila -> omok port, especially for:

- VCF/VCT or tactical puzzle generation
- reusable code or data-paths
- study/opening tooling ideas

Because local shell access to `/kh_code/RenLib` was unavailable in this session, the analysis is based on the visible source tree and documentation from the public mirror and official site:

- GitHub mirror: <https://github.com/gomoku/RenLib>
- README summary: <https://github.com/gomoku/RenLib/blob/master/README.md>
- User guide: <https://github.com/gomoku/RenLib/blob/master/RenLibUsersGuide.htm>
- Official page: <https://www.renju.se/renlib/>

## 1. What RenLib Seems Good For

`RenLib` looks strongest as a human-facing renju/gomoku study database tool, not as a standalone engine service.

What it appears good at:

- Maintaining a tree-structured opening or analysis library in `.lib` format.
- Navigating variants from a position and branching/editing the tree interactively.
- Annotating moves with one-line and multiline comments, marks, and board text.
- Merging many files or directories of games/branches into one library.
- Finding equivalent positions in the library, including rotated/reflected positions and "similar" positions.
- Running tactical search from the current GUI position via menu commands for `Find VCF` and `Find VCT`.
- Exporting positions/studies into presentation formats such as text board, bitmap, applet/html, and game collections.
- Supporting renju-specific study affordances such as forbidden-move markers.

This is consistent across the official page and user guide:

- The official site describes it as a program to build libraries of openings, analysis, and games, with included `VCF.lib` puzzles and sure-win opening libraries.
- The user guide exposes `Find VCF [F4]`, `Find VCT [F5]`, and `Stop VCF/VCT [F6]` as UI commands on the current position.
- The guide also documents strong study/database operations: branch extraction, library merge, comment search, mark search, start positions, and position equivalence search.

## 2. What Is Realistically Reusable

The realistic reuse story is "selective extraction or reimplementation," not "link it as a dependency."

Most plausible reusable areas:

- File-format knowledge and parser/converter behavior.
  - The tree includes format-specific files such as `LibraryFile.cpp`, `Bdt.cpp`, `Buf.cpp`, `Pdb.cpp`, `Rdf.cpp`, `RenJS.cpp`, `Sgf.cpp`, and `TextBoard.cpp`.
  - The user guide confirms import/merge support for multiple legacy renju/game formats and export support for several study/presentation formats.
  - This is useful if omok needs migration tooling, corpus ingestion, or a one-time converter.

- Tactical-search ideas, not likely drop-in code.
  - The repo structure includes `Board.cpp`, `Position.cpp`, `SearchMove.cpp`, `SearchItem.cpp`, `SearchList.cpp`, `MoveNode.cpp`, and related classes.
  - Combined with the documented `Find VCF`/`Find VCT` UI commands, this strongly suggests internal tactical search logic exists.
  - Realistically, the value is as a reference for threat-sequence search structure, candidate generation, and data flow, assuming the code can be extracted at all.

- Study-tool product ideas.
  - RenLib's strongest reusable value for omok may be UX and feature design:
  - annotated branch trees
  - puzzle/study positions stored as branching libraries
  - merge/import workflows for external collections
  - symmetry-aware position lookup
  - exportable diagrams or shareable study pages

- Potential data/test corpus value.
  - The official site lists sample libraries such as `VCF.lib`, `I7.lib`, `D4.lib`, and larger opening collections.
  - Even if not used directly in production, these could be useful as reference fixtures for parser validation or solver regression tests, subject to legal review.

Less realistic as direct reuse:

- A clean library API for server use
- A headless puzzle-generation service
- A drop-in search engine for backend deployment

Nothing visible in the docs suggests those exist as supported interfaces.

## 3. Dependency vs Reference

Recommendation: treat `RenLib` primarily as inspiration/reference, not as a dependency.

Why:

- The source tree is that of an old Windows desktop application, with `RenLib.dsp`, `RenLib.dsw`, `MainFrm.cpp`, `RenLibDoc.cpp`, `RenLibView.cpp`, `res/`, `Debug/`, and `Release/`.
- The README says the project is made in Microsoft Visual Studio C++.
- The documented feature surface is GUI-first and menu-driven, not API-first.
- Export targets like applets, bitmap, and HTML study pages reinforce that it was built as an interactive desktop study tool.
- For backend omok puzzle generation, the needed shape is the opposite: deterministic, headless, scriptable, testable, and easy to isolate from UI state.

Practical conclusion:

- Use RenLib as a reference for:
  - tactical-search concepts
  - study-tree UX
  - file-format interoperability
  - renju/gomoku analysis workflows
- Do not plan on embedding it as a runtime dependency for server-side puzzle generation.

## 4. Top Risks / Blockers

### 4.1 No obvious modern dependency surface

The visible docs expose RenLib as a GUI program. I found no evidence of:

- a CLI for batch solving
- a documented library API
- a server mode
- a protocol suitable for backend integration

That makes direct reuse expensive even if the core algorithms are good.

### 4.2 Windows/MFC and legacy build system

The repo structure strongly suggests an older MFC/Win32 codebase. That creates obvious blockers for a modern backend stack:

- hard extraction cost
- portability problems
- likely UI/state coupling
- obsolete build tooling

### 4.3 Unclear licensing

From the visible repository listing and README summary, there is no obvious license file or license declaration. Unless local inspection proves otherwise, that is a major blocker for copying code or distributing derived implementations based closely on source.

### 4.4 Renju-specific assumptions may not transfer cleanly to omok

RenLib is explicitly a renju-oriented tool. The docs mention forbidden-move markers, and the official site positions it in renju/opening-analysis workflows. If omok in this port uses different rules or does not include renju forbidden rules/opening constraints, then:

- tactical search may encode renju-specific legality assumptions
- imported libraries may not match omok rule semantics
- study content may mix solved gomoku motifs with renju-specific positions

### 4.5 Tactical search is documented only at the feature level

The user guide confirms `Find VCF` and `Find VCT`, but not:

- search limits
- correctness guarantees
- search depth strategy
- batch enumeration capabilities
- whether it can generate puzzles versus only solve/display a line from the current position

For server-side puzzle generation, those missing details matter.

### 4.6 Corpus/format lock-in

If `.lib` becomes a key input format, omok tooling inherits dependency on a niche legacy ecosystem. That is acceptable for conversion/import, but risky as a primary internal representation.

## Bottom Line

RenLib looks valuable for omok mostly as a reference implementation and product reference, especially for:

- opening/study tree organization
- symmetry-aware position lookup
- renju/gomoku corpus import ideas
- tactical-search concepts behind VCF/VCT lookup

It does **not** currently look like a good dependency candidate for server-side puzzle generation.

Best use:

1. Borrow concepts and possibly reverse-engineer file formats.
2. Reimplement any genuinely useful tactical-search logic in omok-native, headless code.
3. Treat RenLib datasets and workflows as reference material, not as production runtime infrastructure.

## Evidence Summary

- Official site says RenLib is for building libraries of openings, analysis, and games, and ships sample opening and `VCF` libraries: <https://www.renju.se/renlib/>
- README describes it as a popular Renju software program with comments and VCF search, made in Microsoft Visual Studio C++: <https://github.com/gomoku/RenLib/blob/master/README.md>
- User guide documents:
  - `Find VCF` / `Find VCT` / stop commands
  - branch extraction and merging
  - import of several renju/game formats
  - export to text board, applet/html, bitmap, and game collection
  - search for same/similar positions via symmetry
  - comment/mark/search workflows
  Source: <https://github.com/gomoku/RenLib/blob/master/RenLibUsersGuide.htm>
