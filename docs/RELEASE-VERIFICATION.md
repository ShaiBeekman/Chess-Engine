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
