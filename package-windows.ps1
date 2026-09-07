[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$TablebaseArchive,
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'Build this Windows app image on Windows.' }
$root = $PSScriptRoot
$archive = (Resolve-Path -LiteralPath $TablebaseArchive).Path
$targetRoot = [IO.Path]::GetFullPath((Join-Path $root 'target')) + [IO.Path]::DirectorySeparatorChar
if ($archive.StartsWith($targetRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep the input tablebase archive outside target; Maven clean removes target.'
}
$expectedTablebases = '46b64d8f7ff89055f1c8d21cada442515d63aff041b3bcc824f7cefbd70b4bca'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedTablebases) {
    throw 'Use the verified original Chess-Engine-v1.0.0-tablebases.zip; see docs/RUNTIME-ASSETS.md.'
}
if (-not $JdkHome) { throw 'Supply -JdkHome or set JAVA_HOME to JDK 26.' }
$jdk = (Resolve-Path -LiteralPath $JdkHome).Path
foreach ($tool in @('java', 'jlink', 'jdeps', 'jpackage')) {
    if (-not (Test-Path -LiteralPath (Join-Path $jdk "bin\$tool.exe"))) { throw "Missing JDK tool: $tool" }
}
$release = Get-Content -LiteralPath (Join-Path $jdk 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="26[."]' -or $release -notmatch 'OS_ARCH="(amd64|x86_64)"') {
    throw 'A Windows x64 JDK 26 is required.'
}
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
Push-Location $root
try {
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $originalPath
    & mvn -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    [xml]$pom = Get-Content -LiteralPath (Join-Path $root 'pom.xml') -Raw
    $version = $pom.project.version
    $build = Join-Path $root 'target'
    $inputDir = Join-Path $build 'windows-input'
    $runtime = Join-Path $build 'windows-runtime'
    $destination = Join-Path $build "windows-v$version"
    $jarName = "chess-engine-$version.jar"
    New-Item -ItemType Directory -Path $inputDir | Out-Null
    Copy-Item -LiteralPath (Join-Path $build $jarName) -Destination $inputDir
    $moduleLines = @(& (Join-Path $jdk 'bin\jdeps.exe') -J-Xmx256m --print-module-deps (Join-Path $inputDir $jarName))
    if ($LASTEXITCODE -ne 0) { throw 'jdeps failed.' }
    $modules = ($moduleLines | Where-Object { $_ -match '^java\.base(,|$)' } | Select-Object -Last 1).Trim()
    if (-not $modules) { throw 'jdeps returned no module list.' }
    Write-Output "Runtime module roots from jdeps: $modules"
    # Retain native java.exe for standalone packaged-runtime verification.
    & (Join-Path $jdk 'bin\jlink.exe') -J-Xmx512m --module-path (Join-Path $jdk 'jmods') --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output $runtime
    if ($LASTEXITCODE -ne 0) { throw 'jlink failed.' }
    & (Join-Path $jdk 'bin\jpackage.exe') -J-Xmx256m --type app-image --name 'Chess Engine' --app-version $version --vendor 'Chess Engine' --description 'Chess Engine desktop application' --input $inputDir --main-jar $jarName --main-class main.java.chess.Main --runtime-image $runtime --icon (Join-Path $root 'assets\logo\chess-engine-logo.ico') --java-options '-Duser.dir=$APPDIR\..' --dest $destination
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
    $image = Join-Path $destination 'Chess Engine'
    Expand-Archive -LiteralPath $archive -DestinationPath $image
    $three = @(Get-ChildItem -LiteralPath (Join-Path $image 'tablebases') -File -Filter '*.tb')
    $four = @(Get-ChildItem -LiteralPath (Join-Path $image 'tablebases\four-piece') -File -Filter '*.ftb.gz')
    if ($three.Count -ne 6 -or $four.Count -ne 30) { throw 'Incomplete tablebase distribution.' }
    $stockfish = Join-Path $image 'stockfish'
    New-Item -ItemType Directory -Path $stockfish | Out-Null
    @('Stockfish 18 is optional and is not bundled.', 'Download it from https://stockfishchess.org/download/ and place its extracted', 'Windows executable in this folder. Common Stockfish executable names are recognized.', 'Launch Chess Engine.exe from the parent folder. No environment configuration is needed.') | Set-Content -LiteralPath (Join-Path $stockfish 'PUT-STOCKFISH-HERE.txt') -Encoding UTF8
    @("Chess Engine $version - Windows x64 app image", '', 'Extract the COMPLETE ZIP to a writable folder, then double-click Chess Engine.exe.', 'Java 26 is bundled. No separate Java or Maven installation is needed.', 'Keep app/, runtime/, and tablebases/ beside the EXE; do not move the EXE alone.', 'Stockfish 18 is optional; see stockfish/PUT-STOCKFISH-HERE.txt.', 'User progress remains in the existing Windows user-profile location.') | Set-Content -LiteralPath (Join-Path $image 'README.txt') -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination $image
    & (Join-Path $image 'runtime\bin\java.exe') --list-modules | Set-Content -LiteralPath (Join-Path $image 'app\RUNTIME_MODULES.txt') -Encoding UTF8
    if ($LASTEXITCODE -ne 0) { throw 'Bundled Java does not start.' }
    foreach ($relative in @('Chess Engine.exe', 'runtime\bin\java.exe', "app\$jarName", 'app\Chess Engine.cfg')) {
        if (-not (Test-Path -LiteralPath (Join-Path $image $relative))) { throw "Missing app image file: $relative" }
    }
    $cfg = Get-Content -LiteralPath (Join-Path $image 'app\Chess Engine.cfg') -Raw
    if ($cfg -notmatch '\$APPDIR' -or $cfg.Contains($root) -or $cfg.Contains($jdk)) { throw 'Launcher configuration is not portable.' }
    if (Test-Path -LiteralPath (Join-Path $image 'chess-engine-logo.ico')) { throw 'Unexpected loose root ICO.' }
    $zip = Join-Path $build "Chess-Engine-v$version-windows.zip"
    Compress-Archive -LiteralPath $image -DestinationPath $zip -CompressionLevel Optimal
    Get-Item -LiteralPath $zip | Select-Object FullName, Length
    Write-Output ("SHA-256: " + (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash)
    Write-Output "Launch: $(Join-Path $image 'Chess Engine.exe')"
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
    Pop-Location
}
