# Architecture

## Overview

The engine is organized around a canonical `PositionGraph` rather than a disposable search tree. A chess position maps to one shared node, and multiple move sequences that transpose into the same position reuse that node.

That design allows information to persist across exploration:

- generated continuations,
- edge and node visit counts,
- parent/child relationships,
- transpositions,
- solved-state information,
- selected-line analysis state,
- repeated exploration from different walkers.

## PositionGraph

`PositionGraph` is the shared state substrate for Dovetail, Hybrid, manual continuation inspection, and downstream analysis.

A persistent walker does not own a private tree. When it makes a move, the resulting position is committed into the shared graph. If the position already exists, the existing canonical node is reused.

This is what makes transpositions structural rather than merely a hash-table optimization layered onto a temporary search tree.

## Dovetail Search

The frozen v1.0 Dovetail build is:

```text
M76-STRENGTH-AWARE-DOVETAIL-WALKERS-V1
```

The scheduler follows a diagonal enumeration:

```text
A1
B1 A2
C1 B2 A3
D1 C2 B3 A4
...
```

Equivalently, diagonal `d` visits walker indices:

```text
d, d-1, ..., 0
```

Each walker has a maximum depth of 10,000 half-moves.

### Exploration hierarchy

For every legal move, the walker computes an exploration penalty using:

1. walker-specific edge visits,
2. total edge visits,
3. target-node visits,
4. a large current-path repetition penalty.

The v1.0 constants are:

```text
PATH_REPEAT_PENALTY = 1,000,000,000
WALKER_VISIT_PENALTY = 1,000,000
TOTAL_VISIT_PENALTY = 10,000
TARGET_VISIT_PENALTY = 10
```

A lower-penalty move cannot be skipped merely because evaluation prefers a more familiar move. Evaluation is applied only to the frontier tied at the best exploration penalty.

### Walker profiles

The profiles repeat every four walkers:

```text
EXPLORER   exponent 0
GUIDED     exponent 1
STRONG     exponent 3
PRINCIPAL  exponent 6
```

`EXPLORER` chooses uniformly from the tied frontier. The remaining profiles use increasingly strong rank-weighted random selection. Every candidate retains positive probability, so the process remains stochastic rather than becoming deterministic minimax.

## Hybrid Search

The frozen v1.0 Hybrid coverage build is:

```text
M77-STRENGTH-AWARE-HYBRID-COVERAGE-V1
```

Hybrid contains:

- the same persistent Dovetail walker lane, and
- a fair node/edge-coverage lane.

The two modes remain formally separated by:

```text
M71-SEPARATE-DOVETAIL-HYBRID-V1
```

### Fairness before evaluation

The coverage queues determine which node receives a coverage turn. Only after that fair node selection does evaluation choose the strongest currently unvisited legal move at that node.

This distinction is intentional: evaluation does not become a global best-first priority queue.

Selected-line focus is a Hybrid-only coverage bias. Pure Dovetail keeps its diagonal enumeration unchanged when a GUI line is selected.

## Native Evaluation

`PositionEvaluator` supplies the native evaluation used to bias tied walker choices and strength-aware Hybrid coverage. The engine's search architecture is intentionally separated from Stockfish.

Stockfish integration lives in its own UCI client and serves as an external reference.

## Stockfish Integration

`StockfishClient` is a dependency-free UCI process client.

The frozen v1.0 calibration/API identity is:

```text
M81-COMMON-DEPTH-MULTIPV-V1
```

It supports:

- process discovery and startup,
- explicit executable paths,
- UCI readiness,
- position analysis,
- MultiPV candidate analysis,
- common-depth calibration,
- PV/continuation formatting,
- forced-mate smoke verification.

Stockfish does not mutate or drive the native `PositionGraph`.

## Exact Endgame Stack

The endgame subsystem includes:

- material classification,
- primitive state representations,
- move/predecessor generators,
- retrograde builders,
- tablebase codecs,
- persisted runtime assets,
- tablebase routing services,
- exact best-move selection,
- curriculum/practice integration.

v1.0 completes the canonical four-piece catalog used by the engine's release gate.

### KPKP packed runtime

The KPKP runtime build is:

```text
M86-KPKP-PACKED-RUNTIME-V1
```

Because the maximum DTM fits in the selected packed representation, WDL and DTM are stored together in one byte per indexed state.

Core indexed-state storage:

```text
Before:
  outcome[]          33,669,120 bytes
  distance[]         67,338,240 bytes
  total raw state    ~96.3 MiB

v1.0 packed:
  packed[]           33,669,120 bytes
  total raw state    32.11 MiB
```

The release gate verifies loading and service routing in an isolated `-Xmx128m` JVM.

## GUI

The Swing GUI is a presentation and control layer over the same underlying position/search systems.

Major surfaces include:

- Dovetail/Hybrid analysis,
- candidate move panels,
- continuation previews,
- Stockfish mode,
- arbitrary position setup,
- FEN load/copy,
- board flipping,
- light/dark presentation,
- endgame curriculum,
- exact WDL/DTM practice.

Normal application startup is intentionally lightweight. Verification suites are kept behind dedicated verification/release entry points rather than running whenever the GUI starts.
