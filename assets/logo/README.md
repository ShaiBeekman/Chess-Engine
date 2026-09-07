# Chess Engine logo

`chess-engine-logo.png` is the blue/teal application-theme master PNG (1254 x 1254), copied unchanged from Downloads.
SHA-256: `10B4CA0236EEE52414A799AF2D1C4E993F571C54CA2E0AA6E30122BF54F56A6E`.

The project README references this file directly. Maven includes this same file
as `/logo/chess-engine-logo.png` in the application JAR; there is no second
source PNG to maintain. Reimport the Maven project when using IntelliJ.

`chess-engine-logo.ico` contains 16, 24, 32, 48, 64, 128 and 256 pixel,
32-bit PNG frames, resized from the full master with high-quality bicubic
interpolation and preserved transparency. No cropping or design changes were made.
`package-windows.ps1` passes this ICO to JDK 26 jpackage, which embeds it in
`Chess Engine.exe`. It is not needed as a loose top-level runtime file.

The Swing window continues to use the packaged PNG through the existing
ApplicationLogo loader. Windows Explorer uses the EXE's embedded icon; Windows
may cache icons or apply taskbar grouping rules. The historical JAR distribution
uses Windows' Java file association. Published releases remain unchanged.
