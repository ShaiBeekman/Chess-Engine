# Endgame trainer fixes and verification

Verified on Windows on September 14, 2026. See the [Qe7 repair report](QE7-REPAIR-VERIFICATION.md) for the final Windows package and the distinction between automated Swing checks and actual EXE interaction.

## Problems and resulting behavior

- Manual moves, move review, the selected row and the turn indicator now share the same displayed position. Disabled review boards reject mouse input; selecting a solution row navigates to that position.
- Hint previously only recorded assistance. It now displays an exact legal move and the side to move for the displayed position.
- Give Up previously opened an empty analysis view. It now resolves the attempt without awarding mastery and reveals a complete exact continuation through the existing move review controls. Solution positions remain separate from played history.
- A pending Give Up result survives overlapping Hint/Give Up clicks and review navigation. Reset, puzzle switches and leaving Endgame invalidate stale background results.
- Header Reset restores the current puzzle's original FEN. Family and all-progress resets replay the saved collection from its beginning.
- Delivered puzzles, repeated occurrences, generation settings and their playback order persist separately from progress. Next replays saved entries and extends the collection only beyond its tail. Only explicit Ordered/Shuffle selections reorder it.
- Closing and reopening restores the current puzzle, played moves and assistance/surrender state.

`EndgameStudySequenceStore` atomically writes puzzle definitions and order to `endgame-study-sequence.properties`. `EndgameStudyProgressStore` keeps attempts, mastery, queues and session records in `endgame-study-progress.tsv`. V1-V4 progress files remain readable. New progress files retain the V4 header and add optional records ignored by older readers.

On Windows these files live under `%APPDATA%\Dovetail Engine`. Tests use isolated temporary profiles. Legacy migration recovers the FENs and insertion order actually present in old saves; it cannot recover positions or delivery order that older builds never recorded.

The Windows packager accepts the verified original tablebase-only or combined release archive. It extracts only tablebases, preventing an old bundled JAR from replacing the rebuilt application. See [Runtime Assets](RUNTIME-ASSETS.md).

## Verification of the final production code

Before committing, all 224 main source/resource files, the packaging script, POM and logo assets were compared with the clean package's inputs; every file matched. The previously completed verification therefore applies to this production code.

| Check | Result and coverage |
| --- | --- |
| `Qe7GuiVerificationMain after` | Passed: ordinary clicks, flipped drag, accepted Qe7 and automatic reply, completed Hint, clickable solution rows, disabled review input, Previous/Next, reset, Give Up and next saved puzzle. Checks model FEN, review FEN/index, selected row, turn and painted pixels. |
| `EndgameTrainerGuiVerificationMain first` | Passed: KQK, Black-to-move KRK, KPK promotion and KQRK; exact hints and terminal solution lines; individual/family/all resets; saved replay; generation at the collection tail; shuffle and stale results after reset. |
| `restart` | Passed: session index, moves, assistance, all collections/settings, missing/unsupported proof feedback and stale results after a family switch. |
| `rapid-help` | Passed: Give Up followed immediately by Hint, and Give Up/review/Give Up without losing the solution or changing played history. |
| `shuffle-before-close` and `shuffle-restart` | Passed in separate JVMs: stored permutation, current index, reset/replay and unchanged collection bytes across restart. |
| `EndgameSequenceVerificationMain` | Passed: sequence/session round trips, repeated roots, reset isolation, explicit shuffle/original order, malformed version rejection and V1-V4 compatibility. |
| Clean Windows packaging | Maven, jdeps, jlink and jpackage passed. Packaged runtime and all 36 tablebases verified. |
| Actual Windows EXE | Native mouse input and Copy FEN verified Qe7, completed Hint, review, reset, Give Up, switching, Reset All and restart. Details are in the Qe7 report. |

An initial broad regression run exhausted native memory while multiple application/build processes were active. Sequential reruns with a 1 GB test heap passed all five trainer phases. The final native EXE checks used its normal launcher configuration.

The Qe7 test was subsequently made self-contained: a fresh isolated profile creates the surrendered Qe7 fixture and a second saved puzzle. This test-only change was compiled and rerun against the same packaged production JAR before committing.

## Running the automated checks

These are explicit Java entry points, **not tests automatically discovered by Maven Surefire**. GUI checks need Windows, a graphical desktop and the runtime tablebases described in [Runtime Assets](RUNTIME-ASSETS.md). They open automated Swing windows and invoke production listeners; this is separate from launching the native EXE.

Build test classes with JDK 26 and Maven, then run from the repository root:

```powershell
mvn -B test-compile
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }

$chessRoot = (Get-Location).Path
$chessApp = Join-Path $chessRoot 'target\windows-v1.0.4\Chess Engine'
$chessJava = Join-Path $chessApp 'runtime\bin\java.exe'
$chessClasspath = (Join-Path $chessRoot 'target\test-classes') + ';' +
    (Join-Path $chessApp 'app\chess-engine-1.0.4.jar')
$chessRun = Join-Path $env:TEMP ('chess-trainer-fix-' + [guid]::NewGuid())
$chessSavedAppData = $env:APPDATA

try {
    $env:APPDATA = Join-Path $chessRun 'profile'
    & $chessJava -Xmx1024m "-Duser.dir=$chessApp" -cp $chessClasspath `
        main.java.chess.endgame.EndgameSequenceVerificationMain
    if ($LASTEXITCODE -ne 0) { throw 'Sequence verification failed.' }
    foreach ($phase in @('first', 'restart', 'rapid-help', 'shuffle-before-close', 'shuffle-restart')) {
        & $chessJava -Xmx1024m "-Duser.dir=$chessApp" -cp $chessClasspath `
            main.java.chess.gui.EndgameTrainerGuiVerificationMain $phase (Join-Path $chessRun 'output')
        if ($LASTEXITCODE -ne 0) { throw "Trainer verification failed: $phase" }
    }

    # A fresh profile seeds the Qe7 fixture; no personal save files are needed.
    $chessQe7Run = Join-Path $env:TEMP ('chess-qe7-audit-' + [guid]::NewGuid())
    $env:APPDATA = Join-Path $chessQe7Run 'profile'
    & $chessJava -Xmx1024m "-Duser.dir=$chessApp" -cp $chessClasspath `
        main.java.chess.gui.Qe7GuiVerificationMain after (Join-Path $chessQe7Run 'output')
    if ($LASTEXITCODE -ne 0) { throw 'Qe7 verification failed.' }
} finally {
    $env:APPDATA = $chessSavedAppData
}
```

Each trainer phase runs in a separate JVM; restart phases reuse the first phase's profile and output. Use a fresh Qe7 profile for each complete run. Additional trainer phases cover completed/surrendered restarts and unavailable assets; they require their corresponding prepared state.

Builds, captures, logs and personal save files are excluded from the source commit.
