# Release Verification

## M87 automated gate

Build identity:

```text
M87-V1-RELEASE-REGRESSION-V1
```

Entry point:

```text
main.java.chess.release.V1ReleaseRegressionMain
```

Each gate is run in a fresh child JVM. This prevents cached tablebase/search state from leaking between subsystem checks and gives failures a hard process boundary.

The release gate also verifies the frozen v1.0 subsystem identities:

```text
M71-SEPARATE-DOVETAIL-HYBRID-V1
M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1
M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1
M81-COMMON-DEPTH-MULTIPV-V1
M86-KPKP-PACKED-RUNTIME-V1
```

## Recorded release-candidate run

| # | Gate | Result | Time |
| ---: | --- | :---: | ---: |
| 1 | Core chess / graph / FEN | PASS | 5.394 s |
| 2 | M76 Dovetail frozen search | PASS | 11.625 s |
| 3 | M77 Hybrid frozen search | PASS | 94.981 s |
| 4 | Three-piece packaged tablebases | PASS | 0.597 s |
| 5 | 30-family four-piece catalog | PASS | 198.667 s |
| 6 | Endgame move controller / practice strength | PASS | 14.980 s |
| 7 | Stockfish process / forced-mate smoke | PASS | 2.241 s |
| 8 | M86 KPKP packed runtime / 128-MiB heap | PASS | 19.200 s |

```text
Automated gates passed: 8 / 8
Total elapsed: 347.700 sec

M87 AUTOMATED V1.0 REGRESSION GATE PASSED
```

The Stockfish smoke gate used Stockfish 18 and verified a forced-mate result.

## M86 packed KPKP runtime

Persisted asset:

```text
KP-KP-canonical-ep.ftb.gz
```

Recorded SHA-256:

```text
65e248648ada7b4372976a0b41ef7d0bc7a684ce51fa85be17c51453688e7cc9
```

Packed runtime state storage:

```text
33,669,120 bytes
32.11 MiB
```

Recorded isolated-child conditions/results:

```text
Maximum heap:          -Xmx128m
Heap before load:      2.41 MiB
Heap after load:       39.75 MiB
Final used heap:       ~38.96 MiB
Packed load time:      11.412 sec
Child exit:            0
```

Verification covered:

- persisted digest identity,
- persisted metadata identity,
- ordinary KPKP service routing,
- en-passant-aware KPKP service routing,
- `ExactEndgameTablebase` ordinary routing,
- `ExactEndgameTablebase` en-passant routing.

## Manual GUI sign-off

After the automated gate, the v1.0 GUI received a manual smoke pass covering the final release interface, including:

- Dovetail/Hybrid analysis layout,
- Stockfish candidate/continuation navigation,
- keyboard navigation,
- Setup mode history and Analyze Position,
- Endgame-to-Setup transition,
- endgame curriculum presentation,
- board preservation and resizing behavior.

Search and tablebase code remained frozen after the formal release gate; the final GUI fixes were presentation/workflow fixes and were manually rechecked before M88 release preparation.

## M88.3 packaged-release verification

The final distributable assets were tested from a separate release-test directory, independent of the source tree.

Validated release files:

```text
Chess-Engine-v1.0.0.jar
Chess-Engine-v1.0.0-tablebases.zip
SHA256SUMS.txt
```

The extracted tablebase package contained exactly:

```text
Three-piece .tb assets:          6
Canonical four-piece .ftb.gz:   30
```

Release-only verification passed:

- Maven `clean package` produced the v1.0.0 JAR successfully.
- The tablebase ZIP extracted to the expected external runtime layout.
- The three-piece resource harness passed for KQK/KRK from the release JAR.
- KPK Endgame mode was manually exercised from the packaged release layout.
- All 30 persisted four-piece assets passed catalog verification.
- Unified four-piece runtime routing passed.
- The application launched successfully from the standalone release JAR.
- The final startup screen-fit behavior passed manual verification.

Validated SHA-256:

```text
1d978c235880fd58d92acf5da675eff66130ddfd74d593feb7e9fd7c2834229f  Chess-Engine-v1.0.0.jar
46b64d8f7ff89055f1c8d21cada442515d63aff041b3bcc824f7cefbd70b4bca  Chess-Engine-v1.0.0-tablebases.zip
```


## Historical v1.0.2 JAR maintenance preparation (superseded)

This records the earlier JAR preparation, before the corrected branded Windows
app-image release. Its artifact hashes are historical, not the current download.
The official logo maintenance release was prepared as v1.0.2. The earlier unpublished local
logo preparation used v1.0.1 in error and is superseded by this fresh build.
The already-published v1.0.1 release (Setup/Endgame redesign), its tag at
`10a08c4b515af7d2a44f5d90dd9d0c3b55976379`, and its published assets remain unchanged.
At that preparation, the README badge targeted the v1.0.2 complete JAR ZIP.

Prepared from `78755799a7423fb4ed2c54194d38b7f8ec4a335c` plus the v1.0.2
version/documentation correction. No production Java, logo artwork, runtime
settings, engine/search, trainer, or tablebase implementation changed.

### Fresh artifacts

Output: `target/release-v1.0.2/`.

File | Bytes | SHA-256
--- | ---: | ---
Chess-Engine-v1.0.2.zip | 216647158 | 0bb9f3f4b123fb8c035ab434306c079160a4a64df3b495196cf4d43e6353a657
Chess-Engine-v1.0.2.jar | 2741539 | 375b423b9da2f4d69b7b940808a2a79cb433515907ff76ae38d861fe5a108b56
Chess-Engine-v1.0.2-tablebases.zip | 213870276 | 08567420ad5062bf5a9db22f31cf223b942ff74211e23f3bc4af07503ca9d54b
SHA256SUMS.txt | 284 | 2de4bce3c09577001073ac2d69964942abe1f1756f75cc4e1968833538044195

`mvn -B clean package` produced the new v1.0.2 JAR. Both ZIPs were newly
assembled from that JAR and the verified original v1.0.0 tablebase data. No
previously generated v1.0.1 application artifacts or logo-preview ZIP were
renamed or reused. The freshly compressed tablebase ZIP has a new archive hash;
all 36 extracted tablebases remain byte-identical to the original verified data.

The full ZIP contains exactly 41 files: `Chess-Engine-v1.0.2.jar`, six three-piece
`.tb` files, thirty canonical four-piece `.ftb.gz` files, the official ICO,
`LICENSE`, `README.txt`, and `stockfish/PUT-STOCKFISH-HERE.txt`. Both ZIP CRC checks,
strict file allowlists, and all artifact checksums passed. No source, IDE,
target directory, temporary, preview, or test-output files are distributed.
The JAR is identical to the standalone artifact and carries Maven version 1.0.2.

### Verification results

- Clean Maven package: PASS (Java 26.0.2.1).
- `main.java.chess.release.V1ReleaseRegressionMain`: all eight gates PASS:
  core chess/graph/FEN, frozen Dovetail search, frozen Hybrid search,
  three-piece resource loading, all thirty four-piece tablebases,
  endgame move control, Stockfish 18 forced-mate smoke, and isolated
  128 MiB KPKP packed-runtime verification.
- `main.java.chess.gui.ApplicationLogoVerificationMain`: PASS; the primary
  window receives the unchanged PNG from the extracted release JAR.
- `main.java.chess.endgame.FourPieceStudyIntegrationVerificationMain`: PASS,
  including unified runtime 2-v-2 routing.
- `main.java.chess.gui.BoardInteractionVerificationMain`: PASS, including
  Setup and Endgame interactions.
- Normal `javaw -jar Chess-Engine-v1.0.2.jar` startup and shutdown: PASS,
  with no heap overrides and no competing verification workload.
- README relative logo path: PASS; master PNG digest unchanged.
- ICO: PASS; seven valid 32-bit PNG frames at 16, 24, 32, 48, 64, 128, 256px.
- Packaged PNG and ICO identities, version metadata, ZIP contents, tablebase
  integrity, and final checksum manifest: PASS.

The full suite used only the final extracted JAR and tablebase data from a fresh
folder outside the checkout. The logo harness additionally used Maven test
classes and the source PNG as a reference. The optional Stockfish executable
was selected via a JVM-local `-Dstockfish.path` argument, without changing user
configuration. Verification logs are in `target/release-verification-v1.0.2/`.

### Verification environment and limitations

The first full-suite attempt, run alongside GUI checks, suffered a native JVM
allocation failure under low host memory during Hybrid verification. Its exit
was treated as a failure even though the Hybrid assertions completed. An early
standalone close check also exceeded its ten-second deadline. Startup/shutdown
then passed without concurrent tests, with initialization allowed to settle and
a thirty-second shutdown deadline. The complete eight-gate suite was rerun
sequentially and passed with process-local `JAVA_TOOL_OPTIONS=-Xmx768m`.
The KPKP child explicitly confirmed its own `-Xmx128m` limit. GUI verification
mains also used temporary 768 MiB heap caps. No such settings are packaged, and
normal application startup/shutdown was tested without them. Failed-run logs
are retained alongside the successful sequential run.

This is the existing runnable-JAR distribution: Java 26 is required separately;
Stockfish is optional and not bundled. There is no native EXE/installer icon to
verify. GUI checks were automated on the local Windows desktop; no fresh-VM or
comprehensive manual visual test is claimed. The published v1.0.1 release and
assets were not changed. No commit, tag, push, or publication was performed.


## v1.0.3 responsiveness release preparation

Prepared from responsiveness commit `f2faee8134eddbf1eb5c4e537e97f668446cc945`
plus version/documentation changes only. No additional production behavior changes
are part of this release preparation. Published v1.0.2 remains unchanged.

The Windows package uses `Chess Engine.exe`, the official blue/teal branding,
bundled Java 26, all 36 unchanged tablebases, and optional Stockfish support.
Users extract the complete `Chess-Engine-v1.0.3-windows.zip`; no separate Java
installation is required. The internal JAR is `app/chess-engine-1.0.3.jar`.

### Final Windows artifact

- File: `target/Chess-Engine-v1.0.3-windows.zip`
- Bytes: **249,459,119**
- SHA-256: `cff024b291135ae168c45d60450611406b905154bf8a9c79028d8f671acc7143`
- App-image: `target/windows-v1.0.3/Chess Engine/`
- Extracted file bytes: **288,769,419** across **186 files**.
- Launcher: `Chess Engine.exe`; internal JAR: `app/chess-engine-1.0.3.jar`.

`package-windows.ps1` performed a fresh `mvn -B clean package`, followed by
jdeps/jlink/jpackage and ZIP creation. Its existing version discovery reads the
POM; no script changes were needed. The final ZIP was extracted outside the
checkout and its bundled runtime and JAR were used for release verification.

### Verification results

| Verification | Result |
| --- | --- |
| Clean Maven build and Windows app-image creation | PASS |
| Core chess / persistent graph / FEN gate | PASS |
| Frozen Dovetail search gate | PASS |
| Frozen Hybrid search gate | PASS |
| Three-piece packaged tablebase gate | PASS |
| 30-family four-piece catalog gate | PASS |
| Endgame move-controller / practice-strength gate | PASS |
| Stockfish process / forced-mate gate | PASS |
| KPKP packed runtime with 128 MiB heap | PASS |
| BoardInteractionVerificationMain | PASS |
| Busy batch/manual-history graph handoff | PASS |
| SetupLayoutVerificationMain | PASS |
| FourPieceStudyIntegrationVerificationMain | PASS |
| Packaged PNG and primary window icon | PASS |
| Native EXE startup, early moves, and shutdown | PASS; automation caveat below |
| ZIP CRC and strict release-file allowlist | PASS |

All eight release gates passed sequentially in 796.294 seconds. GUI checks covered
startup moves while the engine lock was occupied, immediate history updates,
persistent graph/path restoration after analysis, deferred presentation during a
piece gesture, Dovetail/Hybrid/Stockfish, white/black palette drops, existing-piece
movement/removal, board flip, Cancel/re-entry, and endgame navigation. A separate
busy-batch check confirmed manual history and the selected graph endpoint after
the worker handed control back.

The pristine EXE was launched from the extracted ZIP with Java/Maven removed
from PATH and JAVA_HOME cleared. In a successful foreground-owned native mouse
run, the first press occurred 99 ms after the board rendered. Visible updates
were detected 123, 225, 168, and 187 ms after release for four successive white
and black moves. These are external capture/check measurements, not isolated
handler timings. No multi-second first-move stall occurred in that run, and
normal window shutdown completed. A separate module-path inspection confirmed the
loaded JVM was the extracted image's `runtime/bin/server/jvm.dll`.

The release contains exactly the expected launcher, internal application files,
bundled runtime, LICENSE, runtime README, Stockfish placeholder, and 36 tablebases.
Every tablebase matches the pinned original archive byte-for-byte. All 504 Java
class files match the approved responsiveness test build; only version metadata
and release documentation changed. The PNG matches the official blue/teal master,
and all seven embedded EXE icon frames match the ICO (16, 24, 32, 48, 64, 128,
256 pixels). EXE and Maven version metadata are 1.0.3. No source, IDE, temporary,
profiling, nested target directory, or absolute developer path leaked into the ZIP.

### Verification environment and limitations

The full gate used a temporary process-local 768 MiB heap cap; the KPKP child
explicitly used 128 MiB. GUI/check helpers used 512 MiB. The native EXE mouse run
used normal packaged settings without a heap override. No packaged runtime
settings changed. Stockfish was supplied explicitly for relevant checks; it
remains optional and is not bundled.

Native input automation was not uniformly reliable on the local desktop:
initial attempts clicked before the first board paint or lacked foreground
ownership; the optional Robot run passed its first moves but timed out on Back.
The foreground-owned native EXE run above passed. Repeat attempts confirmed
Windows denied foreground activation and therefore did not constitute repeat
interaction tests. The deterministic GUI suite passed in full. These results do
not claim a fresh-VM or comprehensive hands-on visual test, nor consumer
SmartScreen/code-signing validation.

Verification helpers/logs were kept outside release contents. The original
published v1.0.2 ZIP remains unchanged (SHA-256
`f80e0b3e7e19e1af01cecc329010c665aca211e1f67bac6cff2d14a473ba3205`).
No commit, tag, push, release modification, or upload was performed.
