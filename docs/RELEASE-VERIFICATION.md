# v1.0 Release Verification

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


## v1.0.1 maintenance release preparation

Prepared locally from `fe8a13a995ca9c001fc6428e7b2b0312197c789d` plus the
v1.0.1 Maven/version documentation changes. No engine or GUI behavior changes
were made during release preparation. Historical verification above is preserved.

Artifacts are in `target/release-v1.0.1/`. The full ZIP was assembled from the
clean v1.0.1 JAR and the verified v1.0.0 tablebase archive, never the logo-preview
ZIP. The separately versioned tablebase ZIP is byte-identical to v1.0.0.

The Windows layout remains a runnable JAR beside `tablebases/` and the optional
`stockfish/` folder. The complete ZIP additionally includes the official ICO,
license, and concise launch instructions. The Stockfish note now refers to the
actual JAR launch command instead of a nonexistent batch launcher.

### Artifact integrity

File | Bytes | SHA-256
--- | ---: | ---
Chess-Engine-v1.0.1.zip | 216647138 | a35a4f07ca01098ba5067458df91f369bf7a7d18388b56c75e741f9ebf6d09ac
Chess-Engine-v1.0.1.jar | 2741539 | 369e5e1f678514ace8d793465858d6d2ff53e1da2bf0c32501ef71cc6a429cbb
Chess-Engine-v1.0.1-tablebases.zip | 213899435 | 46b64d8f7ff89055f1c8d21cada442515d63aff041b3bcc824f7cefbd70b4bca
SHA256SUMS.txt | 284 | 1d8c18d7cb0cd508ef255037ab05e45f2ff0796306a4f3cddf37dcddb2cf4635

The full ZIP has exactly 41 files: the v1.0.1 JAR, six three-piece tablebases,
thirty four-piece tablebases, ICO, license, launch instructions, and Stockfish
note. A strict file allowlist and ZIP CRC check passed. Every tablebase matches
the verified v1.0.0 data byte-for-byte. No preview, source, IDE, build directory,
temporary file, or test log is included. The embedded JAR matches the standalone
artifact; its Maven metadata is 1.0.1 and its PNG matches the repository master.

### Verification results

- `mvn -B clean package`: PASS on Java 26.0.2.1.
- Full `main.java.chess.release.V1ReleaseRegressionMain`: all 8 gates PASS,
  including core chess/graph/FEN, frozen Dovetail and Hybrid search, three-piece
  resource loading, all 30 four-piece assets, endgame move control, Stockfish 18
  forced-mate smoke, and isolated 128 MiB KPKP runtime verification.
- `main.java.chess.gui.ApplicationLogoVerificationMain`: PASS; the primary window
  opens with the unchanged PNG loaded from the extracted release JAR.
- `main.java.chess.endgame.FourPieceStudyIntegrationVerificationMain`: PASS;
  includes the unified runtime 2-v-2 probe.
- `main.java.chess.gui.BoardInteractionVerificationMain`: PASS from the extracted
  final JAR, including Setup and Endgame interactions.
- Normal `javaw -jar Chess-Engine-v1.0.1.jar` startup and window closure: PASS.

Tests ran from an extraction of the final ZIP in a fresh directory outside the
checkout. The application and tablebase gates used the extracted JAR/data.
The logo check additionally used Maven's test classes and original PNG solely
as its verification harness/reference. Stockfish was supplied via a JVM-local
`-Dstockfish.path` argument; no user configuration was changed.

The first full-suite attempt exhausted the host Windows paging-file commitment
in the catalog gate (native JVM allocation failure, not a checksum failure).
The complete suite then passed with process-local `JAVA_TOOL_OPTIONS=-Xmx768m`.
The KPKP child still explicitly used and confirmed `-Xmx128m`. No runtime flag
or application change was added to the release. Logs, including the failed
attempt, are retained in `target/release-verification-v1.0.1/`.

### Limits

This retains the v1.0.0 distribution model: Java 26 is required separately and
Stockfish is optional and not bundled. There is no native EXE/installer whose
icon can be verified. GUI startup/icon and interaction checks were automated on
the local Windows desktop; no new clean-VM or comprehensive manual visual test
is claimed. No commit, tag, push, or GitHub release was created during preparation.
