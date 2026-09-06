# Endgame redesign and shared window-state preservation

The canonical references are `analysis-reference.png` (analytical density, technical
typography and path/card language) and `setup-final-reference.png` (sharp geometry,
dashboard composition, spacing and controls). Endgame now uses the same surfaces,
thin rectangular borders, Segoe UI hierarchy, monospaced telemetry, and restrained
blue action treatment. No global Swing defaults, Analysis design, Setup design,
board drawing, or board allocation were changed.

## Presentation

- Current study and mastery progress share a responsive dashboard. Family choice,
  side to move, exact proof/DTM, instructions, feedback, counts, order, cursor and
  resets retain their existing data and callbacks.
- Played-line review has its own rectangular content area (expanded in the
  [composition pass](ENDGAME-COMPOSITION-VERIFICATION.md)). Previous/Next Move, Hint,
  Give Up and Next Position are all visible at the standard resolution matrix.
- Training actions stay outside the scrollable content. Narrow hosts stack the
  cards and allow vertical scrolling; unusually large totals and long feedback
  wrap without losing data. Large dashboards increase typography and control
  sizes. Theme changes preserve those sizes.
- Solver uses the same dashboard and flat controls, including its existing
  practice-defense checkbox and strength slider. The legacy chooser shares the
  palette and rectangular card language.

The current production `ChessWindow` opens Curriculum directly and does not
instantiate `EndgameSolverPanel` or `EndgameModeChooser`. This redesign preserves
that routing. Solver's APIs, callbacks and presentation are verified directly;
window-state tests mount it temporarily in the existing auxiliary card host.
Those test-only mounts do not add a production Solver route.

## Window regression: cause and correction

`resizeForCurrentMode()` called `fitWindowToUsableBounds(true)`, reapplying the
JFrame's preferred dimensions during navigation. The fitter also called
`setMinimumSize()` / `setMaximumSize()` before checking maximization. Those native
peer updates could interfere with the user's managed window state, and AWT
fullscreen was not checked at all. Component move/resize callbacks entered the
same fitter. `pack()` and `setLocationRelativeTo(null)` occur only during startup;
the card-switch and shared board layout methods only arrange child components.

The saved pre-change executable reproduces these defects:

- Engine Home changes a manually selected 1720 x 900 frame at (20, 15) into
  1564 x 883.
- Fitting a maximized frame to a different usable rectangle changes its native
  minimum/maximum size hints despite the maximized guard.

Preferred top-level sizing now runs only at startup. Mode transitions fit the
existing normal-window bounds only when necessary for usable-screen/minimum
constraints, preserving valid custom size and location. Before changing any
native size hint or bounds, the shared fitter returns after child revalidation
and repainting if either maximization axis, iconification, or AWT fullscreen is
active. This same rule applies to Home, Setup, FEN/Analyze, Endgame, and resize/move
events. No forced `setExtendedState()` restoration, repacking, or recentering is
needed. Responsive Setup sizing and square-board allocation remain intact.

`target/endgame-redesign/window-change-only.diff` isolates this change from the
Setup and board repairs that were already uncommitted when this work began.

## Files changed in this task

| File | Purpose |
| --- | --- |
| `src/main/java/chess/gui/ChessWindow.java` | Shared frame-state guard and startup-only preferred sizing |
| `src/main/java/chess/gui/EndgameCurriculumPanel.java` | Curriculum dashboard and fixed training actions |
| `src/main/java/chess/gui/EndgameSolverPanel.java` | Solver dashboard using the same presentation primitives |
| `src/main/java/chess/gui/EndgameModeChooser.java` | Match legacy chooser surfaces and typography |
| `src/main/java/chess/gui/EndgameWorkspace.java` | Local rectangular controls, responsive cards/type, wrapping telemetry |
| `src/main/java/chess/gui/EndgameLayoutVerificationMain.java` | Render matrix, control visibility, theme and callback contracts |
| `src/main/java/chess/gui/WindowStateVerificationMain.java` | Native maximized, windowed and fullscreen transition verification |
| `src/main/java/chess/gui/BoardGeometryVerificationMain.java` | Narrow-host test uses the parent's height rather than assuming Home enlarges the frame |
| `docs/ENDGAME-REDESIGN-VERIFICATION.md` | This implementation and verification record |

The existing uncommitted changes in `SetupPanel`, `PiecePalettePanel`,
`ChessBoardPanel`, `BoardLoadingOverlay`, and the other Setup/board verification
sources were preserved. No engine, tablebase, curriculum/progress persistence,
move-controller, or practice-policy source was edited.

## Verification on 2026-09-06

Before the subsequent composition pass, the public state/configuration methods
and listener wiring in both Endgame panels were byte-for-byte unchanged from
their pre-redesign sources. That historical audit is in
`target/endgame-redesign/preservation-audit.log`.

| Gate | Result |
| --- | --- |
| Maven package | PASS |
| `V1ReleaseRegressionMain` | All 8 gates PASS on the final packaged implementation (363.114 seconds); see `target/endgame-redesign/release-final.log` |
| `EndgameTrainerVerificationMain quick` | PASS, all 30 exact families and repeated Next Position generation (261.062 seconds) |
| `EndgameStudyProgressVerificationMain` | PASS, isolated temporary persistence file |
| `BoardGeometryVerificationMain` | PASS, all 40 mode/theme/resolution captures plus transitions/loading/flip/narrow-host checks |
| `SetupLayoutVerificationMain` | PASS, existing responsive layout and editing checks |
| `BoardInteractionVerificationMain` | PASS, live Swing moves, Setup editing, exact Endgame/Hint/Give Up/Next/Home |
| `EndgameLayoutVerificationMain` | PASS at 100% and 125% UI scale, both themes, plus 1000/700/460px standalone panel widths |
| `WindowStateVerificationMain` | PASS, native frame launched maximized, repeated transitions in maximized/custom windowed states, and actual AWT fullscreen |
| Analysis / Setup screenshot preservation | 32 / 32 saved PNGs byte-identical |
| `git diff --check` | PASS |

Layout captures simulate 1920 x 1080, 1600 x 900, 1366 x 768, and 1280 x 720
monitors, reserve 40 logical pixels for a taskbar, and include actual native frame
insets. The desktop resolution is not changed. Native state transitions run on
the current Windows display, with the requested usable-screen rectangles also
passed into the shared fitter while maximized. AWT fullscreen is entered and
restored by the verifier.

Transitions include Analysis -> Setup, Setup -> Cancel/Analyze/Home/Endgame,
Analysis -> Endgame, Endgame -> Home/Setup, and Solver-host -> Curriculum/Home.
Bounds and extended state must remain identical before and after each navigation.
Native Solver navigation cannot be tested because that route is absent; its
shared-host mounting and complete UI contracts are covered instead.

The interaction verifier dispatches actions/mouse events into a live JFrame.
These are automated Swing interaction checks, not a physical mouse/keyboard
operator session. All tests that navigate real studies run with a separate
APPDATA directory so they cannot change the user's curriculum progress.

## Reproduce

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/shaib/.m2/repository' -B package
java -Xmx512m -cp target/classes main.java.chess.release.V1ReleaseRegressionMain
java -Xmx512m -cp target/classes main.java.chess.endgame.EndgameStudyProgressVerificationMain
java -Xmx512m -cp target/classes main.java.chess.endgame.EndgameTrainerVerificationMain quick
java -Xmx512m '-Dsun.java2d.uiScale=1' -cp target/classes main.java.chess.gui.EndgameLayoutVerificationMain
java -Xmx512m '-Dsun.java2d.uiScale=1.25' -cp target/classes main.java.chess.gui.EndgameLayoutVerificationMain target/endgame-redesign/layout-scale125
java -Xmx512m '-Dsun.java2d.uiScale=1' -cp target/classes main.java.chess.gui.BoardGeometryVerificationMain target/endgame-redesign/geometry
java -Xmx512m '-Dsun.java2d.uiScale=1' -cp target/classes main.java.chess.gui.SetupLayoutVerificationMain target/endgame-redesign/setup

$endgameTestProfile = Join-Path (Get-Location) 'target/endgame-redesign/test-profile'
New-Item -ItemType Directory -Path $endgameTestProfile -Force | Out-Null
$savedAppData = $env:APPDATA
try {
    $env:APPDATA = $endgameTestProfile
    java -Xmx512m '-Dsun.java2d.uiScale=1' -cp target/classes main.java.chess.gui.WindowStateVerificationMain
    java -Xmx512m '-Dsun.java2d.uiScale=1' -cp target/classes main.java.chess.gui.BoardInteractionVerificationMain target/endgame-redesign/interactions
} finally {
    $env:APPDATA = $savedAppData
}
```

Maven packaging does not automatically invoke these standalone verification mains.

## Review artifacts

- [Large dark Curriculum](../target/endgame-redesign/layout/curriculum-1920x1080-dark.png)
- [Compact light Curriculum](../target/endgame-redesign/layout/curriculum-1280x720-light.png)
- [Narrow Curriculum](../target/endgame-redesign/layout/narrow-curriculum-460-dark.png)
- [Solver](../target/endgame-redesign/layout/solver-1000-dark.png)
- [Chooser](../target/endgame-redesign/layout/chooser-dark.png)

All logs, scale/theme captures, native transition captures, original source copies,
and the pre-change executable are under `target/endgame-redesign/`. Maven clean
removes those generated artifacts. The application artifact is
`target/chess-engine-1.0.0.jar`.
