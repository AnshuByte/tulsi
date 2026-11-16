# Tulsi Manual Test Checklist & Real-Device Validation Guide

This document covers critical manual verification workflows that cannot be 100% automated via headless CI, including aggressive OEM battery savers (Xiaomi, OPPO, Vivo, Realme, Samsung), physical lock screen wakeups in deep Doze mode, real FCM pushes with apps swiped away/killed, and accessibility TalkBack navigation.

---

## 1. OEM Battery Optimization & Background Execution Checklist

Aggressive Chinese and Korean OEM ROMs frequently kill background processes, delay AlarmManager alarms, and block background activity starts unless explicitly configured.

### A. Xiaomi / Redmi / POCO (MIUI / HyperOS)
- [ ] **Autostart Permission:**  
  Go to `Settings` -> `Apps` -> `Manage Apps` -> `Tulsi` -> Turn **ON** `Autostart`.
- [ ] **Battery Saver:**  
  In the same app info screen, tap `Battery Saver` -> Select **No restrictions** (do not use "MIUI Battery Saver").
- [ ] **Lock Screen Display & Popups:**  
  Go to `Other Permissions` -> Grant **Show on Lock Screen** and **Display pop-up windows while running in the background**.
- [ ] **Lock App in Recent Apps:**  
  Open App Switcher (Recents), long-press the `Tulsi` app card, and tap the **Lock Icon** (🔒) to prevent the system cleaner from swiping it away.

### B. OPPO & Realme (ColorOS / Realme UI)
- [ ] **Allow Background Activity:**  
  Go to `Settings` -> `Battery` -> `More battery settings` -> `App battery management` -> `Tulsi` -> Enable **Allow background activity** and **Allow foreground activity**.
- [ ] **Auto-Launch:**  
  Go to `Settings` -> `Apps` -> `Auto-launch` -> Toggle `Tulsi` **ON**.
- [ ] **Recent Apps Lock:**  
  In Recent Apps screen, tap the three dots (⋮) on the `Tulsi` card -> select **Lock**.

### C. Vivo & iQOO (Funtouch OS / OriginOS)
- [ ] **High Background Power Consumption:**  
  Go to `Settings` -> `Battery` -> `Background power consumption management` -> Select `Tulsi` -> Choose **High background power consumption** (or "Don't restrict background power usage").
- [ ] **Autostart:**  
  Go to `Settings` -> `Applications and Permissions` -> `Permission Management` -> `Autostart` -> Enable `Tulsi`.

### D. Samsung Galaxy (One UI)
- [ ] **Never Sleeping Apps:**  
  Go to `Settings` -> `Battery` -> `Background usage limits` -> `Never sleeping apps` -> Tap `+` and add `Tulsi`.
- [ ] **Battery Unrestricted:**  
  Long-press `Tulsi` icon -> Tap `(i)` App Info -> `Battery` -> Select **Unrestricted**.
- [ ] **Remove Unused Apps Permission:**  
  In App Info, ensure **Pause app activity if unused** is turned **OFF**.

---

## 2. Zero-Miss Exact Alarm & Doze Mode Real-Device Tests

Execute these steps with physical device:

| Step | Action | Expected Behavior | Pass/Fail |
|---|---|---|---|
| **ALM-PHY-01** | App Open (Foreground) | Set dose 2 minutes ahead; keep app open. | Full-screen prompt appears immediately at target second; audio & vibration play. | [ ] |
| **ALM-PHY-02** | App in Background | Set dose 2 minutes ahead; press HOME. | Full-screen prompt launches over home screen; audio rings. | [ ] |
| **ALM-PHY-03** | App Swiped Away (Killed) | Set dose 3 minutes ahead; swipe app away from Recents. | Exact alarm wakes Linux kernel RTC; `AlarmActivity` launches with sound. | [ ] |
| **ALM-PHY-04** | Screen Locked & Sleeping | Set dose 3 minutes ahead; press Power to turn screen off and lock phone. | Screen lights up (`turnScreenOn`), shows over keyguard (`showWhenLocked`), alarm plays. | [ ] |
| **ALM-PHY-05** | Overnight Deep Doze Mode | Leave phone stationary on desk unplugged for >2 hours with dose scheduled. | Alarm rings exactly on schedule without drift or missing. | [ ] |
| **ALM-PHY-06** | Airplane Mode / Offline | Turn ON Airplane mode (WiFi & Cellular OFF); wait for alarm. | Rings locally on time. (Tulsi never requires internet to ring local alarms). | [ ] |
| **ALM-PHY-07** | Silent Mode / DND | Set phone to Silent / Do Not Disturb (DND); wait for alarm. | Full-screen prompt lights up screen; audio routes through `USAGE_ALARM` audio stream. | [ ] |

---

## 3. Real Push Notification & Escalation Tests

Requires two connected physical devices or two Android Virtual Devices (AVD 1: Ravi / Patient, AVD 2: Asha / Caregiver):

| Test | Flow | Expected Result | Pass/Fail |
|---|---|---|---|
| **PUSH-PHY-01** | Member Takes Dose | Member (Ravi) taps big **Take Medicine** button on phone. | Caregiver (Asha) receives warm push: *"Tulsi: Ravi took TestPill A"*. | [ ] |
| **PUSH-PHY-02** | Caregiver App Swiped Away | Swipe caregiver app away from Recents; Member marks dose taken. | Caregiver device still receives FCM push notification banner immediately. | [ ] |
| **PUSH-PHY-03** | Member Ignores Dose (Escalation) | Member alarm rings; member does NOT act. Wait 30 minutes. | Caregiver receives urgent escalation push: *"Tulsi reminder: Ravi's TestPill A is due"*. | [ ] |
| **PUSH-PHY-04** | One-Tap Dial Caregiver | Caregiver taps **Call** next to Ravi's profile in Family Circle. | Phone dialer (`Intent.ACTION_DIAL`) opens with Ravi's phone number pre-filled. | [ ] |

---

## 4. Accessibility & Senior Usability (TalkBack & Simple Mode)

Tested on physical device or AVD with Simple Mode enabled:

| Item | Requirement | Verification Method | Pass/Fail |
|---|---|---|---|
| **A11Y-01** | TalkBack Screen Reader | Turn ON TalkBack in `Settings -> Accessibility`. Navigate Home, Alarm, and Family screens. | All interactive buttons have descriptive `contentDescription` labels (e.g., "Take Medicine", "Snooze 10 minutes", "Close"). No unlabeled icon buttons. | [ ] |
| **A11Y-02** | 48dp+ Minimum Touch Targets | Inspect buttons in Simple Mode. | "Take Medicine" button is expanded to 64dp high, full width, easy for tremors. | [ ] |
| **A11Y-03** | Largest System Font Scaling | Set Android display settings font size to **Largest** (130%+). | Text scales cleanly; cards expand vertically without truncating or overlapping text. | [ ] |
| **A11Y-04** | High Contrast & Dark Theme | Toggle between Dark Theme and Light Theme in system settings. | Teal `#00897B` and Deep Navy `#0A192F` maintain WCAG AAA contrast ratio (>7:1) for readability. | [ ] |
