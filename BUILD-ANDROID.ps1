# Build a real offline-capable Android debug APK from a pinned upstream LightCraft revision.
# Run from PowerShell on Windows with Android Studio/SDK 36, NDK, Java 17, Git, and Rust installed.
# First-time tool downloads are BUILD-TIME ONLY. The resulting app requests no network permission.
param(
    [string]$WorkDir = (Join-Path $env:USERPROFILE 'HuelithAndroidTest'),
    [switch]$SkipClone
)
$ErrorActionPreference = 'Stop'
$UPSTREAM_SHA = '0ba118de92a81f7463ca0e6d79e1323137b1adae'
$GRADLE_VERSION = '8.13'
$packageDir = $PSScriptRoot
$checkout = Join-Path $WorkDir 'huelith'

function Run-Checked([string]$name, [scriptblock]$command) {
    Write-Host "`n==> $name" -ForegroundColor Cyan
    & $command
    if ($LASTEXITCODE -ne 0) { throw "$name failed with exit code $LASTEXITCODE" }
}
function Require-Command([string]$name, [string]$why) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name is required. $why" }
}

Require-Command 'git' 'Install Git for Windows.'
Require-Command 'rustup' 'Install Rust using https://rustup.rs/ and reopen PowerShell.'
Require-Command 'cargo' 'Install Rust using https://rustup.rs/ and reopen PowerShell.'
Require-Command 'java' 'Install JDK 17 (Android Studio includes a JDK).'

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path (Join-Path $sdk 'platforms\android-36\android.jar'))) {
    throw "Android SDK 36 not found at '$sdk'. Install Android SDK Platform 36 using Android Studio > SDK Manager, or set ANDROID_HOME."
}
$ndkRoot = Join-Path $sdk 'ndk'
$ndks = @(Get-ChildItem -Path $ndkRoot -Directory -ErrorAction SilentlyContinue | Sort-Object { [version]($_.Name -replace '-.*$','') } -Descending)
if ($ndks.Count -eq 0) { throw "Android NDK not found at '$ndkRoot'. Install NDK (side by side) using Android Studio > SDK Manager > SDK Tools." }
$env:ANDROID_HOME = $sdk
$env:ANDROID_NDK_HOME = $ndks[0].FullName
$env:ANDROID_NDK_ROOT = $env:ANDROID_NDK_HOME
Write-Host "Using Android SDK: $sdk"
Write-Host "Using Android NDK: $env:ANDROID_NDK_HOME"

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
if (-not (Test-Path (Join-Path $checkout '.git'))) {
    if ($SkipClone) { throw "Source checkout missing: $checkout" }
    New-Item -ItemType Directory -Force -Path $checkout | Out-Null
    Run-Checked 'Initialize local source checkout' { git -C $checkout init }
    Run-Checked 'Pin verified upstream revision' { git -C $checkout fetch --depth 1 'https://github.com/storytold/lightcraft.git' $UPSTREAM_SHA }
    Run-Checked 'Checkout pinned source' { git -C $checkout checkout --detach FETCH_HEAD }
}
$sourceSha = (git -C $checkout rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -ne $UPSTREAM_SHA) {
    throw "Unexpected upstream checkout: $sourceSha; expected $UPSTREAM_SHA. Use a fresh WorkDir to avoid rewriting any existing work."
}
if (-not (Test-Path (Join-Path $checkout 'crates\mobile-ffi'))) {
    $python = Get-Command 'py' -ErrorAction SilentlyContinue
    if ($python) { Run-Checked 'Apply offline KMP source overlay' { py -3 (Join-Path $packageDir 'install_overlay.py') $checkout } }
    else {
        Require-Command 'python' 'Install Python 3.10+ to apply the overlay.'
        Run-Checked 'Apply offline KMP source overlay' { python (Join-Path $packageDir 'install_overlay.py') $checkout }
    }
}
if (-not (Test-Path (Join-Path $checkout 'apps\huelith-kmp'))) { throw 'KMP overlay was not installed' }
Run-Checked 'Install ARM64 Rust target' { rustup target add aarch64-linux-android }
if (-not (Get-Command 'cargo-ndk' -ErrorAction SilentlyContinue)) {
    Run-Checked 'Install Android native build helper' { cargo install cargo-ndk --locked }
}
# Cargo commands MUST execute inside the pinned source checkout, not the extracted ZIP.
Push-Location $checkout
try {
    Run-Checked 'Run Rust image quality and edit persistence tests' { cargo test -p huelith-mobile-ffi --lib }
    Run-Checked 'Check Rust formatting' { cargo fmt --all -- --check }
    Run-Checked 'Compile upstream Rust engine for Android ARM64' {
        & (Join-Path $checkout 'apps\huelith-kmp\scripts\build-native.ps1')
    }
} finally { Pop-Location }

$gradleHome = Join-Path $WorkDir "tools\gradle-$GRADLE_VERSION"
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'
if (-not (Test-Path $gradleExe)) {
    $tools = Join-Path $WorkDir 'tools'
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $url = "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    $zip = Join-Path $tools "gradle-$GRADLE_VERSION-bin.zip"
    Write-Host 'Downloading Gradle for the first build...' -ForegroundColor Cyan
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    $shaPage = (Invoke-WebRequest -Uri "${url}.sha256" -UseBasicParsing).Content
    $expected = (($shaPage -split '\s+')[0]).ToLowerInvariant()
    $actual = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { Remove-Item $zip -Force; throw 'Gradle SHA-256 verification failed' }
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip -Force
}
if (-not (Test-Path $gradleExe)) { throw 'Gradle extraction failed' }
$gradleProject = Join-Path $checkout 'apps\huelith-kmp'
# Preserve source copyright and license notices inside distributed APKs.
$licenses = Join-Path $gradleProject 'composeApp\src\androidMain\assets\licenses'
New-Item -ItemType Directory -Force -Path $licenses | Out-Null
foreach ($filename in @('LICENSE-MIT', 'LICENSE-APACHE', 'NOTICE')) {
    $source = Join-Path $checkout $filename
    if (-not (Test-Path $source)) { throw "Upstream license notice missing: $source" }
    Copy-Item $source (Join-Path $licenses $filename) -Force
}
$attributions = Join-Path $checkout 'assets\ATTRIBUTION.md'
if (Test-Path $attributions) { Copy-Item $attributions (Join-Path $licenses 'ASSETS-ATTRIBUTION.md') -Force }
Push-Location $gradleProject
try {
    Run-Checked 'Assemble ARM64 Android debug APK' { & $gradleExe :composeApp:assembleDebug --no-daemon --stacktrace }
} finally { Pop-Location }
$built = Join-Path $gradleProject 'composeApp\build\outputs\apk\debug\composeApp-debug.apk'
if (-not (Test-Path $built)) { throw "Gradle reported success but APK is missing: $built" }
$copy = Join-Path $WorkDir 'Huelith-offline-arm64-debug.apk'
Copy-Item $built $copy -Force
# Validate the APK container and bundled Rust ABI before handing the artifact to QA.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($copy)
try {
    $entries = @($archive.Entries | ForEach-Object FullName)
    if ($entries -notcontains 'classes.dex') { throw 'Invalid APK: no classes.dex.' }
    foreach ($requiredNotice in @('assets/licenses/NOTICE', 'assets/licenses/LICENSE-MIT', 'assets/licenses/LICENSE-APACHE')) {
        if ($entries -notcontains $requiredNotice) { throw "License notice not bundled in APK: $requiredNotice" }
    }
    if ($entries -notcontains 'lib/arm64-v8a/libhuelith_mobile_ffi.so') {
        throw 'Invalid APK: native Huelith ARM64 engine is missing.'
    }
} finally { $archive.Dispose() }
# Validate the FINAL merged binary manifest, not just our source manifest. A transitive
# Android library must never introduce INTERNET, telemetry, or location permissions.
$buildTools = Join-Path $sdk 'build-tools'
$aapt = @(Get-ChildItem -Path $buildTools -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Name -replace '-.*$','') } -Descending |
    ForEach-Object { Join-Path $_.FullName 'aapt.exe' } |
    Where-Object { Test-Path $_ } | Select-Object -First 1)
if ($aapt.Count -eq 0) { throw 'Android SDK Build Tools with aapt.exe are required for final APK permission verification.' }
$permissions = (& $aapt[0] dump permissions $copy | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect APK permissions with aapt.' }
if ($permissions -match '(?m)^uses-permission:') {
    throw "Offline policy violation: an Android permission was merged into the APK: $permissions"
}
$badging = (& $aapt[0] dump badging $copy | Out-String)
if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='cc.gvrv.huelith'") {
    throw 'APK package identity could not be verified.'
}
Write-Host 'APK audit: package identity and zero permissions VERIFIED.' -ForegroundColor Green
Write-Host "APK SHA256: $((Get-FileHash -Algorithm SHA256 $copy).Hash)"
Write-Host "`nAPK READY: $copy" -ForegroundColor Green
Write-Host "Install: adb install -r `"$copy`""
Write-Host 'First test: turn on airplane mode, import a JPEG, adjust Exposure, export JPEG, reopen the app.'
