# Chess Engine logo

`chess-engine-logo.png` is the original, unchanged master PNG (1254 ? 1254).
SHA-256: `D62870817102E7FB01B400D8AEFE02D44AF25C3398C4395CFB6C8B04B3AB1955`.

The project README references this file directly. Maven includes this same file
as `/logo/chess-engine-logo.png` in the application JAR; there is no second
source PNG to maintain. Reimport the Maven project when using IntelliJ.

`chess-engine-logo.ico` contains 16, 24, 32, 48, 64, 128 and 256 pixel,
32-bit PNG frames, resized from the full master with high-quality bicubic
interpolation and preserved transparency. No cropping or design changes were made.
It is available for Windows shortcuts or a future native launcher.

The current Windows distribution uses a runnable JAR, not a native EXE or
installer. Windows controls the JAR file-association icon; the application sets
its own Swing window/taskbar icon from the packaged PNG. No native launcher
configuration exists to receive an ICO. Keep the existing external tablebases
and optional Stockfish layout when assembling the release ZIP; the runtime logo
travels inside the JAR and requires no external image file.
