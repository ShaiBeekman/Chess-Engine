# Shared board-layout regression repair

## Root cause

The uncommitted Setup responsive patch introduced a mode-dependent board host:
`boardStack.doLayout()` fitted Setup to its host but assigned every other mode
`boardPanel.getPreferredSize().width` (640). `ChessBoardPanel.getRenderScale()`
also returned 1 outside Setup. Those two assumptions disagreed with the actual
space available after window resizing and frame insets.

The concrete h-file failure was **Setup -> Home -> Analysis/Endgame**. Home bypassed
Setup's Cancel/Analyze cleanup and the common FEN handoff did not reset the board
frame. Setup's coordinate border remained: 29px left + 11px right. The host was
640px wide, its usable interior was only 600px, and the normal-mode board still
rendered 640px. The rightmost 40px were clipped. Separately, the evaluation bar
stretched to the column height rather than the square height. On short displays,
normal-mode boards also exceeded their hosts vertically.

The pre-repair binary reproduces 60 geometry failures across the matrix and
transition checks, including the explicit 600px-interior/640px-board mismatch.
The previous Setup verifier missed this by asserting a fixed 640px board in
Analysis/Endgame instead of checking containment.

No global `UIManager`, `UIDefaults`, look-and-feel, or font overrides were found.
Setup and Palette typography are local and unchanged by this repair.

## Correction and scope

- `ChessWindow.java`: one column calculation reserves local insets, the visible
  evaluation bar, and only in Setup the palette. The host allocates one square
  from its actual available width/height; the board and loading overlay receive
  that identical rectangle. The evaluation bar uses the square's Y and height.
  The common FEN/Home/engine handoff clears Setup state, palette allocation, and
  coordinate/header insets. Setup's own Reset still restores its starting
  snapshot and remains in Setup.
- `ChessBoardPanel.java`: rendering, square hit testing, dragging, and promotion
  selection use the actual square size in every mode. The 640px preferred size
  is only the natural upper size for the column, never a rendering assumption.
- `BoardLoadingOverlay.java`: the status card is centered within and clamped to
  the current square. The board still paints the scrim with the same transform
  as its squares and pieces.

`AnalysisPanel`, `StockfishCandidatePanel`, `SetupPanel`, `PiecePalettePanel`,
`EndgameCurriculumPanel`, `EndgameSolverPanel`, and engine/tablebase code were not
changed by this repair. No Endgame redesign features were added.

`BoardGeometryVerificationMain` and `BoardInteractionVerificationMain` are new
explicit verification entry points. `SetupLayoutVerificationMain` now checks
shared board containment instead of demanding 640px outside Setup.

## Verification on 2026-09-06

The rendered tests reserve a 40px taskbar and use actual native frame/title-bar
insets. Both dark and light themes passed in Dovetail, Hybrid, Stockfish, Setup,
and Endgame Curriculum: 40 workspace captures, plus loading and transition
captures. Native insets may differ on other desktops.

| Monitor | Analysis square | Setup square | Endgame square |
| --- | ---: | ---: | ---: |
| 1920 x 1080 | 640 | 640 | 640 |
| 1600 x 900 | 640 | 515 | 640 |
| 1366 x 768 | 563 | 383 | 563 |
| 1280 x 720 | 515 | 335 | 515 |

Checks cover all 64 rendered squares, every square's mouse mapping in both
orientations, board/ancestor containment, the h-file, coordinate alignment,
evaluation-bar bounds, loading-card and scrim bounds, mode transitions, a
narrowed host, taskbar-aware frame bounds, and scaled promotion selection.

The live Swing sequence passed Dovetail initial/continuation/back, Hybrid,
Stockfish, flip, White/Black palette drops, moving/removing/replacing pieces,
drag-ghost cleanup, Clear Board, side to move, Cancel, Analyze Position, Setup
Reset, Home, normal Reset confirmation, exact KQK load, a legal exact move,
Next Position, Hint, Give Up, and returning Home. A temporary APPDATA directory
isolated curriculum progress.

Native Robot capture returned black images and native mouse clicks did not reach
the test window, including outside the sandbox. Therefore the completed
interaction pass dispatched Swing button actions and mouse events into a live
JFrame and rendered its actual components. Physical-desktop mouse testing was
not completed. The optional `--robot` argument supports it in an interactive
desktop session.

The current `ChessWindow` has no Solver route: `showEndgameCurriculum()` documents
the removal of the old chooser, and `EndgameSolverPanel` is not instantiated.
There is no active Solver board workspace to enter or redesign.

Baseline comparisons passed with **zero changed pixels** in all 8 complete Setup
workspace images and in 12 Analysis dashboard/full-size-board comparisons
(Dovetail/Hybrid/Stockfish, both themes, 1920x1080 and 1600x900). Analysis cards,
telemetry, Stockfish reference, and suggested line retain their existing design.
Only the evaluation bar's corrected height changes beside a full-size board.

Maven packaging passed. All 8 existing release regression gates passed in
505.760 seconds: core/FEN, Dovetail, Hybrid, three-piece resources, the 30-family
catalog, exact move controller/practice strength, Stockfish, and packed KPKP.

## Reproduce and artifacts

```powershell
mvn -B package
java -cp target/classes main.java.chess.release.V1ReleaseRegressionMain
java -Xmx512m -Dsun.java2d.uiScale=1 -cp target/classes main.java.chess.gui.BoardGeometryVerificationMain
java -Xmx512m -Dsun.java2d.uiScale=1 -cp target/classes main.java.chess.gui.SetupLayoutVerificationMain

# Use an isolated test profile for the interaction pass.
$boardTestProfile = Join-Path (Get-Location) 'target/board-layout/interaction-profile'
New-Item -ItemType Directory -Path $boardTestProfile -Force | Out-Null
$previousAppData = $env:APPDATA
try {
    $env:APPDATA = $boardTestProfile
    java -Xmx512m -Dsun.java2d.uiScale=1 -cp target/classes main.java.chess.gui.BoardInteractionVerificationMain
} finally {
    $env:APPDATA = $previousAppData
}
```

Maven does not automatically invoke the standalone verification mains.
The current machine's Maven cache was selected explicitly with
`-o -Dmaven.repo.local=C:/Users/shaib/.m2/repository`.

Artifacts under `target/board-layout/`:

- `before/`, `after/`: baseline and repaired resolution/theme captures.
- `before/failures.txt`, `after/failures.txt`: failing baseline and empty repaired report.
- `live/`: rendered captures of the completed interaction sequence.
- `baseline-comparison.log`: the 20 pixel comparisons.
- `geometry.log`, `setup-controls.log`, `interactions.log`, `release-regression.log`.
- `before-repair.jar`: preserved pre-repair executable for reproducing the regression.

Generated artifacts are removed by Maven clean.
