# Third-Party Notices

## Stockfish

This project can optionally launch **Stockfish** as an external UCI chess engine for reference analysis and comparison.

Stockfish is **not included in this source repository**.

- Project: Stockfish
- Website: https://stockfishchess.org/
- Source: https://github.com/official-stockfish/Stockfish
- v1.0 integration target: Stockfish 18 (`sf_18`)
- License: GNU General Public License version 3 (GPLv3)

Stockfish's official project states that redistribution must satisfy the GPLv3 requirements, including access to the corresponding source code (or an appropriate pointer to the exact source used to build the distributed binary).

For that reason, this repository does not commit the local Stockfish executable. Users can download Stockfish directly from the official project and configure its path with `stockfish.path` or `STOCKFISH_PATH`.

If a future Chess Engine release directly redistributes a Stockfish binary, that release must include the required GPL notices and corresponding-source information for the exact binary being distributed.

The Chess Engine's own Java source is a separate codebase and communicates with Stockfish through the UCI process protocol.
