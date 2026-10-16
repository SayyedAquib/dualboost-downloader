# Automated Test Runner for DualBoost Downloader
# Executes local MockWebServer test suite and optional connected ADB tests without manual intervention.

$ErrorActionPreference = "Stop"

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  DualBoost Downloader - Automated Test Runner       " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# Step 1: Run Local JVM Unit & Integration Tests (MockWebServer + SHA-256 validation)
Write-Host "`n[1/3] Running local MockWebServer unit & integration tests..." -ForegroundColor Yellow
& .\gradlew.bat test
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Unit and Integration tests failed!" -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] All unit and integration tests passed cleanly (0 failures)!" -ForegroundColor Green

# Step 2: Check for connected Android device via ADB
Write-Host "`n[2/3] Checking for connected Android devices via ADB..." -ForegroundColor Yellow
$devicesOutput = & adb devices
$deviceConnected = $false

foreach ($line in $devicesOutput) {
    if ($line -match "^\S+\s+device$") {
        Write-Host "[INFO] Found connected device: $line" -ForegroundColor Green
        $deviceConnected = $true
        break
    }
}

if (-not $deviceConnected) {
    Write-Host "[INFO] No Android device attached. Skipping connected on-device smoke test." -ForegroundColor DarkGray
    Write-Host "       (Plug in your Pixel 9 with USB Debugging enabled to run live hardware tests)" -ForegroundColor DarkGray
    Write-Host "`n=====================================================" -ForegroundColor Cyan
    Write-Host "[PASS] 100% AUTOMATED TESTS PASSED (MockWebServer + Integrity)!" -ForegroundColor Green
    Write-Host "=====================================================" -ForegroundColor Cyan
    exit 0
}

# Step 3: Run On-Device Automated ADB Smoke Test
Write-Host "`n[3/3] Running automated on-device deployment test via ADB..." -ForegroundColor Yellow

# Build APK
Write-Host "   Compiling debug APK..." -ForegroundColor DarkGray
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] assembleDebug failed!" -ForegroundColor Red
    exit 1
}

# Install APK
Write-Host "   Installing app onto device via ADB..." -ForegroundColor DarkGray
$installResult = & adb install -r app/build/outputs/apk/debug/app-debug.apk | Out-String
if ($installResult -notmatch "Success") {
    Write-Host "[FAIL] ADB installation failed: $installResult" -ForegroundColor Red
    exit 1
}
Write-Host "   Installed successfully." -ForegroundColor Green

# Grant notification permission
& adb shell pm grant com.dualboost.downloader android.permission.POST_NOTIFICATIONS

# Clear logcat buffer
& adb logcat -c

# Launch app with intent
Write-Host "   Launching MainActivity via ADB..." -ForegroundColor DarkGray
& adb shell am start -n com.dualboost.downloader/.ui.MainActivity

# Wait 3 seconds for initial initialization
Start-Sleep -Seconds 3

# Verify app process is running
$pidCheck = & adb shell pidof com.dualboost.downloader | Out-String
$trimmedPid = $pidCheck.Trim()
if ([string]::IsNullOrWhiteSpace($trimmedPid)) {
    Write-Host "[FAIL] Application failed to launch or crashed on startup!" -ForegroundColor Red
    & adb logcat -d -s AndroidRuntime
    exit 1
}
Write-Host "   App is running smoothly (PID: $trimmedPid)." -ForegroundColor Green

# Check logcat for any unhandled crashes
$crashLogs = & adb logcat -d -s AndroidRuntime:E | Out-String
if ($crashLogs -match "FATAL EXCEPTION") {
    Write-Host "[FAIL] Crash detected in logcat!" -ForegroundColor Red
    Write-Host $crashLogs -ForegroundColor Red
    exit 1
}

Write-Host "   Zero crashes or fatal exceptions detected in logcat." -ForegroundColor Green
Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "[PASS] ALL AUTOMATED TESTS & ON-DEVICE SMOKE TESTS PASSED!" -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Cyan
exit 0

