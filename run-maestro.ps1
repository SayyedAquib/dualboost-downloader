# ==============================================================================
# DualBoost Downloader — Playwright-Style UI Automation Runner (Maestro)
# ==============================================================================

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  DualBoost Downloader - Maestro E2E UI Automation Runner" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Check for connected device
$devices = adb devices | Where-Object { $_ -match "\tdevice$" }
if (-not $devices) {
    Write-Host "[!] No Android device found via ADB." -ForegroundColor Red
    Write-Host "    Please connect your Google Pixel 9 and ensure USB/Wireless debugging is active." -ForegroundColor Yellow
    exit 1
}
$rawDevice = ($devices | Select-Object -First 1).ToString()
$deviceId = ($rawDevice -split "\s+")[0]
Write-Host "[+] Connected Android Device: $deviceId" -ForegroundColor Green

# 2. Check if Maestro is installed (PATH or default install locations)
$maestroCmd = Get-Command maestro -ErrorAction SilentlyContinue
if (-not $maestroCmd) {
    $commonPaths = @(
        "$HOME\.maestro\maestro\bin",
        "$HOME\.maestro\bin",
        "C:\maestro\bin"
    )
    foreach ($path in $commonPaths) {
        if (Test-Path "$path\maestro.bat") {
            $env:PATH += ";$path"
            $maestroCmd = Get-Command maestro -ErrorAction SilentlyContinue
            break
        }
    }
}

if (-not $maestroCmd) {
    Write-Host "[!] Maestro CLI is not currently installed on your computer." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To install Maestro on Windows (Native):" -ForegroundColor Cyan
    Write-Host "  1. Download the zip directly from GitHub releases:" -ForegroundColor White
    Write-Host "     https://github.com/mobile-dev-inc/Maestro/releases/latest/download/maestro.zip" -ForegroundColor Gray
    Write-Host "  2. Extract it to C:\maestro (so you have C:\maestro\bin\maestro.bat)" -ForegroundColor White
    Write-Host "  3. Add C:\maestro\bin to your Windows PATH, or extract to $HOME\.maestro" -ForegroundColor White
    Write-Host ""
    Write-Host "Or install via one PowerShell command:" -ForegroundColor Cyan
    Write-Host '  curl.exe -L "https://github.com/mobile-dev-inc/Maestro/releases/latest/download/maestro.zip" -o "$HOME\maestro.zip"; Expand-Archive "$HOME\maestro.zip" -DestinationPath "$HOME\.maestro" -Force; Remove-Item "$HOME\maestro.zip"' -ForegroundColor Gray
    Write-Host ""
    Write-Host "After extracting, re-run: .\run-maestro.ps1" -ForegroundColor Green
    exit 0
}

# 3. Wake screen
adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Start-Sleep -Milliseconds 300

# 4. Execute Maestro Master Suite
Write-Host "[*] Executing Master End-to-End Suite (.maestro/master_suite.yaml)..." -ForegroundColor Cyan
maestro test .maestro/master_suite.yaml

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] All Maestro UI flows passed successfully!" -ForegroundColor Green
} else {
    Write-Host "[!] Maestro flow encountered an issue. See details above." -ForegroundColor Red
}
exit $LASTEXITCODE
