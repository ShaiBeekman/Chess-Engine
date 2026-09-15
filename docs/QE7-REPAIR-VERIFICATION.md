# Qe7 repair and Windows EXE verification

Verified on Windows on September 14, 2026.

## Demonstrated failure

Qe7 is legal and exact-best in the reproduction position. Active-study Qe7 already worked in both earlier builds. The failure was in solution review: solution rows ignored clicks, and a board gesture could restore the starting board while leaving a later review move and turn indicator selected.

The original process and duplicate builds were inspected. The running copy under `target/Chess-Engine-v1.0.3-windows/Chess Engine` had an older JAR than the advertised `target/windows-v1.0.3/Chess Engine` copy. **Both JARs reproduced the defect.** Switching copies alone would not repair it.

| Earlier copy | JAR SHA-256 |
| --- | --- |
| Running extracted Windows copy | `70c126bd3c9697e4aada8650117f9d0942d8759371a4eebc59dd05d607f17de6` |
| Previously advertised rebuild | `6fe5977f8e339a4f8eb268ff3eade45098ae4273ac12a8d697af9dca136120ad` |

Desktop, Start Menu and pinned shortcut targets were inspected; no chess-targeting shortcut was found. A still older Desktop copy was found and updated along with both project app images. The launcher filename alone is insufficient to identify the build: its adjacent app JAR and configuration select the Java code.

## Before and after state

The reproduction begins with a surrendered KQK attempt:

- **P0**, root: `8/8/2K5/8/8/q7/1k6/8 b - - 0 1`
- **P1**, after Black Qe7 (a3-e7): `8/4q3/2K5/8/8/8/1k6/8 w - - 1 2`
- **P2**, after the observed exact White reply Kb5: `8/4q3/8/1K6/8/8/1k6/8 b - - 2 2`

The automated trace records the attempted move, acceptance into played history, model FEN, paint position, played-history tail, review position/index, selected row, turn label and enabled state.

| Action | Before repair | After repair |
| --- | --- | --- |
| Click Qe7 in the solution | No handler; remains P0/index 0 | P1/index 1; queen e7; White to move |
| Next Move to Qe7 | Initially P1/index 1 | P1/index 1 |
| Attempt c6-b5 while reviewing P1 | Legal P2 proposed, rejected by controller; model and paint return to P0 while review remains P1/index 1 and the turn says White | No move proposed; model, paint, review and turn stay P1 |
| Reset and play a3-e7 normally | Accepted, with P1 then P2 in played history | Accepted; model, history, review and rendering agree |
| Hint after Qe7 and defense | P2 remains | P2 remains after asynchronous completion |
| Previous / Next | Correct until board input corrupts review | Restores P1 / P2 with matching selected row and turn |

Review leaves played history unchanged: solution moves do not become played attempts.

## Cause and repair

1. `ChessBoardPanel` custom mouse listeners processed input despite `setEnabled(false)`. Input handlers now check enabled state, and disabling cancels selection, drag and pending promotion.
2. Rejected input restored the played-history tail instead of the selected review position. Restoration now uses the common review routine, synchronizing board, row, navigation and turn.
3. `EndgamePlayedLine` painted SAN rows without a selection listener. Row clicks now use the same review navigation as Previous/Next.
4. Review feedback explains selecting moves and resetting the attempt to resume play.

The rollback occurred synchronously inside the rejected-move callback. No asynchronous engine-result overwrite was demonstrated. Existing generation/request guards were reviewed, and stale-result, overlapping-help, reset and puzzle-switch regressions passed.

## Actual native EXE verification

The final EXE was launched from this exact path relative to the checkout:

`target/Chess-Engine-v1.0.3-windows/Chess Engine/Chess Engine.exe`

Windows mouse input was used, screenshots were visually inspected, and the application's Copy FEN action confirmed each expected position after the UI action completed. The process loaded its own `runtime/bin/server/jvm.dll`.

Verified in that EXE:

- Two-click Qe7 visibly places the queen on e7, followed by exact Kb5.
- The queen remains on e7 after Hint completes.
- Previous/Next restore P1/P2 and their turn indicators.
- Reset returns to P0; Give Up reveals the solution; selecting Qe7 displays P1.
- Board input during solution review leaves P1 unchanged.
- Next loads the second saved puzzle.
- Reset All followed by replay preserves original puzzle order.
- Closing and reopening restores the second puzzle/index.
- Saved sequence bytes stay unchanged, and final native stderr logs are empty.

These actions used isolated copies of an existing save. Personal progress was not reset. Some early captures preceded queued UI actions/tablebase loading or encountered an inactive window; those were not counted as passes. Final captures verified the target process, completed Copy FEN action and expected loaded FEN. Temporary input tracing was removed from the release.

## Automated checks, separately

`Qe7GuiVerificationMain` uses injected AWT mouse events and production listeners. It checks FENs and exact painted pixels against the selected review position, plus ordinary and flipped drag input, Hint, Give Up, navigation, reset and saved order.

The final packaged JAR also passed the trainer phases `first`, `restart`, `rapid-help`, `shuffle-before-close` and `shuffle-restart`, and `EndgameSequenceVerificationMain`. Output labeled "VISIBLE GUI PHASE" means an automated Swing window, not native EXE verification.

See [Endgame trainer verification](ENDGAME-TRAINER-FIX-VERIFICATION.md) for coverage and repeatable commands. The regression test can create its own fixture in a fresh isolated profile; personal saves and raw logs are not required or committed.

## Verified package

All 224 main source/resource files matched the clean packaging inputs. All 186 app-image files matched at each installed copy. Standalone, app-image and ZIP-contained JARs were identical. The source match was checked again before committing.

| Artifact | SHA-256 |
| --- | --- |
| JAR | `cd4450361f01149b2632b0aec113c605405e63df5ddd9cbf3244f44a22651055` |
| EXE launcher | `fa4a6dcd3692419b3179dfbf7310507ec97a4a2dbeb3f50526bb8c70d70e9506` |
| Windows ZIP | `95612c1ae4638212b12106f6d6407d05be7cc13ecbba822d2a3666f4fe8db73f` |

These hashes identify the locally verified package; a later rebuild can have different archive hashes because of build timestamps. Generated packages and local audit captures are excluded from Git.
