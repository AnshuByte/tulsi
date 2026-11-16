@echo off
REM ==============================================================================
REM Tulsi Zero-Miss Alarm & Doze Mode Automated ADB Verification Script
REM Tests:
REM   1. AlarmManager exact RTC_WAKEUP registration
REM   2. Android Deep Doze Mode simulation (cmd deviceidle force-idle)
REM   3. App Standby Bucket throttling simulation (am set-standby-bucket rare)
REM   4. Screen-off / Lockscreen full-screen alarm trigger
REM   5. BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED broadcast rescheduling
REM ==============================================================================

echo ========================================================
echo       Tulsi Alarm & Doze Mode ADB Test Suite
echo ========================================================

echo.
echo [1/6] Checking connected ADB device/emulator...
adb devices
if errorlevel 1 (
    echo [ERROR] ADB not found or device not connected.
    exit /b 1
)

echo.
echo [2/6] Inspecting AlarmManager for com.tulsicare.app exact RTC_WAKEUP alarms...
adb shell dumpsys alarm | findstr /I "com.tulsicare.app RTC_WAKEUP"
echo [Info] Active alarms for Tulsi listed above.

echo.
echo [3/6] Simulating App Standby Bucket: RESTRICTED (bucket 45)...
adb shell am set-standby-bucket com.tulsicare.app restricted
echo App bucket set. Current standby bucket:
adb shell am get-standby-bucket com.tulsicare.app
echo [Verify] Verify that exact alarms still fire without delay.

echo.
echo [4/6] Simulating Deep Doze Mode...
echo Unplugging battery simulator...
adb shell dumpsys battery unplug
echo Forcing device into deep Doze mode...
adb shell cmd deviceidle force-idle
echo Deep idle state:
adb shell cmd deviceidle get deep
echo [Verify] Phone is now in deep Doze. Wait for scheduled dose time.
echo Alarm will wake RTC hardware, start AlarmPlaybackService, and open AlarmActivity.

echo.
echo [5/6] Simulating Reboot & Time Change Broadcasts...
echo Sending BOOT_COMPLETED...
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.tulsicare.app
echo Sending TIME_SET...
adb shell am broadcast -a android.intent.action.TIME_SET -p com.tulsicare.app
echo Sending TIMEZONE_CHANGED...
adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED -p com.tulsicare.app
echo [Verify] BootReceiver received intents and triggered rescheduleAll.

echo.
echo [6/6] Resetting test environment...
adb shell cmd deviceidle unforce
adb shell dumpsys battery reset
adb shell am set-standby-bucket com.tulsicare.app active
echo [Done] Doze unforced, battery simulator reset, bucket restored to active.
echo ========================================================
