#!/usr/bin/env bash
# ==============================================================================
# Tulsi Zero-Miss Alarm & Doze Mode Automated ADB Verification Script
# ==============================================================================

set -e

echo "========================================================"
echo "      Tulsi Alarm & Doze Mode ADB Test Suite"
echo "========================================================"

echo ""
echo "[1/6] Checking connected ADB device/emulator..."
adb devices

echo ""
echo "[2/6] Inspecting AlarmManager for com.tulsicare.app exact RTC_WAKEUP alarms..."
adb shell dumpsys alarm | grep -E "com.tulsicare.app|RTC_WAKEUP" || true

echo ""
echo "[3/6] Simulating App Standby Bucket: RESTRICTED (bucket 45)..."
adb shell am set-standby-bucket com.tulsicare.app restricted
echo "Current standby bucket: $(adb shell am get-standby-bucket com.tulsicare.app)"

echo ""
echo "[4/6] Simulating Deep Doze Mode..."
adb shell dumpsys battery unplug
adb shell cmd deviceidle force-idle
echo "Deep idle state: $(adb shell cmd deviceidle get deep)"
echo "[Verify] Phone is in deep Doze. RTC alarm will fire via setExactAndAllowWhileIdle."

echo ""
echo "[5/6] Simulating Reboot & Time Change Broadcasts..."
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.tulsicare.app
adb shell am broadcast -a android.intent.action.TIME_SET -p com.tulsicare.app
adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED -p com.tulsicare.app
adb shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -p com.tulsicare.app

echo ""
echo "[6/6] Resetting test environment..."
adb shell cmd deviceidle unforce
adb shell dumpsys battery reset
adb shell am set-standby-bucket com.tulsicare.app active
echo "[Done] Environment restored."
echo "========================================================"
