# Setup responsive layout verification

The follow-up [shared board repair](BOARD-LAYOUT-REGRESSION.md) corrects normal-mode
host containment and Setup exit transitions while preserving these Setup renders.

Run the usual build and release gate, then the explicit Swing verifier:

```powershell
mvn -B clean package
java -cp target/classes main.java.chess.release.V1ReleaseRegressionMain
java -Dsun.java2d.uiScale=1 -cp target/classes main.java.chess.gui.SetupLayoutVerificationMain
```

The verifier uses real Swing components and a realized, hidden JFrame. It simulates
usable monitor rectangles, reserves 40 logical pixels for the taskbar, subtracts
the native frame/title-bar insets, and renders the client area to PNG. It does not
change the desktop resolution or open a visible window. Run in a desktop session;
headless AWT cannot create the frame.

Screenshots are written to `target/setup-layout/` (or an output directory supplied
as the first argument). The verification entry point follows the repository's
existing standalone regression-main convention; Maven does not execute it automatically.

## Verified on 2026-09-06

| Simulated monitor | Rendered client | Square board | Palette | Dark/light |
| --- | --- | --- | --- | --- |
| 1920 x 1080 | 1902 x 993 | 640 x 640 | 680 x 158 | PASS |
| 1600 x 900 | 1582 x 813 | 515 x 515 | 555 x 158 | PASS |
| 1366 x 768 | 1348 x 681 | 383 x 383 | 423 x 158 | PASS |
| 1280 x 720 | 1262 x 633 | 335 x 335 | 375 x 158 | PASS |

These renders show the application header, complete board, White and Black palette
rows, Clear Board, dashboard cards, FEN, and all Setup actions simultaneously.
Neither Setup column contains a scroll pane. Native decoration sizes vary by OS
and display scaling, so exact rendered dimensions may differ on another desktop.

Checks also cover window minimum/maximum bounds, a monitor left of the primary
with top/left taskbar insets, every square in both orientations, negative/outside
palette drop coordinates, board drag events, undo/redo, side-to-move/FEN updates,
clear/validation/Analyze enablement, theme changes without resizing, Cancel, and
square board containment in Analysis and Endgame containers. The Endgame
layout check does not launch a new tablebase study; the release gate covers the
endgame backend.

An additional run with `-Dsun.java2d.uiScale=1.25` passed on the current desktop's
scaled usable bounds. Its output is in `target/setup-layout-scale125/`.

Maven packaging passed. The existing `V1ReleaseRegressionMain` passed all 8 gates
in 525.219 seconds, including core/FEN, Dovetail, Hybrid, three-piece assets,
the 30-family four-piece catalog, Endgame move control, Stockfish, and packed KPKP.
The log is `target/setup-release-regression.log`.
