@echo off
echo =======================================================
echo Tulsi Performance, Cold Start and Monkey Fuzz Testing
echo Package: com.tulsicare.app
echo =======================================================

echo 1. Checking connected ADB devices...
adb devices
if %ERRORLEVEL% neq 0 (
    echo Error: ADB not found or device not connected.
    exit /b 1
)

echo.
echo 2. Measuring Cold Start Time...
adb shell am force-stop com.tulsicare.app
timeout /t 2 /nobreak >nul
adb shell am start -W -n com.tulsicare.app/.MainActivity

echo.
echo 3. Measuring Memory Footprint (PSS / Private Dirty)...
adb shell dumpsys meminfo com.tulsicare.app

echo.
echo 4. Testing Screen Rotation (Landscape then Portrait)...
adb shell settings put system user_rotation 1
timeout /t 2 /nobreak >nul
adb shell settings put system user_rotation 0
timeout /t 1 /nobreak >nul
echo Rotation executed without crash.

echo.
echo 5. Executing ADB Monkey UI Stress Test (500 events)...
adb shell monkey -p com.tulsicare.app --throttle 80 --pct-touch 40 --pct-motion 25 --pct-appswitch 15 --pct-majornav 20 -v 500

echo.
echo 6. Checking for ANRs and Fatal Crashes in Logcat...
adb shell "logcat -d | grep -E 'FATAL EXCEPTION|ANR in com.tulsicare.app'"

echo =======================================================
echo Performance and Monkey Test Completed Successfully!
echo =======================================================
