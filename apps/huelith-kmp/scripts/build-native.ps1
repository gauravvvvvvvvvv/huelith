# Run from a cloned LightCraft checkout in PowerShell. Uses Android NDK from Android Studio.
$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (git -C $scriptDir rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Run this from inside the LightCraft git clone.' }
Set-Location $root
rustup target add aarch64-linux-android
if ($LASTEXITCODE -ne 0) { throw 'Could not install Rust Android target.' }
$out = Join-Path $root 'apps/huelith-kmp/composeApp/src/androidMain/jniLibs'
cargo ndk -t arm64-v8a -o $out build --release -p huelith-mobile-ffi
if ($LASTEXITCODE -ne 0) { throw 'Native library build failed. Install cargo-ndk and Android NDK.' }
$so = Join-Path $out 'arm64-v8a/libhuelith_mobile_ffi.so'
if (-not (Test-Path $so)) { throw "Missing native library: $so" }
Write-Host "Native library ready: $so"
