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

# 2. Check if Maestro is installed
$maestroCmd = Get-Command maestro -ErrorAction SilentlyContinue
if (-not $maestroCmd) {
    Write-Host "[!] Maestro CLI is not currently installed on your computer." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To install Maestro on Windows, run this single command:" -ForegroundColor Cyan
    Write-Host "    powershell -c `"irm https://get.maestro.mobile.dev | iex`"" -ForegroundColor White
    Write-Host ""
    Write-Host "After installation finishes, simply re-run this script:" -ForegroundColor Green
    Write-Host "    .\run-maestro.ps1" -ForegroundColor White
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
