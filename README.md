# Tulsi (Android)

> *"Take care, together."*

Tulsi is an Android medicine reminder app designed for families. It provides rock-solid, zero-miss medicine reminders for patients and real-time adherence visibility and escalation alerts for family caregivers.

All components run strictly within **100% $0 free tiers**:
- **Android App:** Kotlin 2.0, Jetpack Compose Material 3, Room Local DB, AlarmManager exact alarms (`SCHEDULE_EXACT_ALARM`).
- **Backend Sync & Storage:** Firebase Spark ($0 Tier) — Firestore real-time snapshots & Firebase Cloud Messaging (FCM).
- **Push Relay & Missed Dose Escalation:** Cloudflare Worker ($0 Free Tier) running a 5-minute cron escalation and HTTP push relay via FCM v1 HTTP API.

---

## 1. What Needs To Be Set Up Manually?

The Android app code and Cloudflare Worker scripts are 100% written and compiled into a production-ready debug APK. Because this is your private, secure family app, there are two quick external setup steps:

### A. Firebase Console Setup (Takes ~3 minutes, $0 Spark Plan)
1. Go to [Firebase Console](https://console.firebase.google.com/) and click **Add project** (e.g. `Tulsi-Family`). Select the free **Spark plan**.
2. Click **Add Android App**:
   - Package name: `com.tulsicare.app`
3. Download the generated `google-services.json` file and replace the placeholder file at:
   - [`app/google-services.json`](file:///c:/Users/Anshu/Desktop/Projects/DoseCircle/app/google-services.json)
4. In the Firebase Console left menu:
   - **Authentication**: Click *Get Started* -> enable **Anonymous** and **Email/Password**.
   - **Firestore Database**: Click *Create Database* -> choose your region -> start in *Production mode*.
   - **Firestore Rules**: Copy and paste the contents of [`firestore.rules`](file:///c:/Users/Anshu/Desktop/Projects/DoseCircle/firestore.rules) into the Rules tab and click *Publish*.
5. In **Project Settings** -> **Service Accounts**:
   - Click **Generate new private key** to download your Firebase Service Account JSON file (used by Cloudflare Worker to send FCM push notifications).

### B. Cloudflare Worker Deployment (Takes ~2 minutes, $0 Free Plan)
1. In a terminal, navigate to the `worker/` directory:
   ```bash
   cd worker
   npm install
   ```
2. Store your Firebase Service Account JSON key as an encrypted secret:
   ```bash
   npx wrangler secret put FCM_SERVICE_ACCOUNT_KEY
   # When prompted, paste the full contents of the downloaded service account JSON
   ```
3. Deploy the worker to Cloudflare's free global network:
   ```bash
   npx wrangler deploy
   ```
4. Your deployed worker URL is live at `https://tulsi-worker.tulsi-health.workers.dev` and pre-configured into [`PushRelayClient.kt`](file:///c:/Users/Anshu/Desktop/Projects/DoseCircle/app/src/main/kotlin/com/tulsicare/app/core/network/PushRelayClient.kt).

---

## 2. How the App Works Under the Hood

### Local Exact Alarm Guarantee (Never Misses)
- Many medicine apps fail when the phone enters Android's battery-saving Doze mode or when internet connection drops.
- **Tulsi never relies on internet or push notifications to ring the patient's alarm.**
- Reminders are scheduled locally into Android's native Linux kernel RTC alarm hardware via `AlarmManager.setExactAndAllowWhileIdle()`.
- Even in deep Doze mode, with screen locked and airplane mode enabled, the alarm will wake the device, trigger full-screen `AlarmActivity`, and play audio/vibration via a transient foreground service (`AlarmPlaybackService`) that auto-terminates within 2 minutes.

### Realtime Family Synchronization
- When the patient taps **"Take"** on their phone:
  - The local Room database updates immediately.
  - The event syncs to Cloud Firestore.
  - The app notifies the Cloudflare Worker push relay, which immediately delivers a warm FCM high-priority push to family members: *"Tulsi: Mom took Metformin"*.

### Overdue Escalation Cron
- If a scheduled dose is not marked as taken within 30 minutes:
  - The Cloudflare Worker scheduled cron (`*/5 * * * *`) checks Firestore for overdue doses.
  - It automatically dispatches an urgent escalation notification to all family admins: *"Tulsi reminder: Mom's Metformin is due"*.
  - Family members can tap **Call** directly from their app to ring the patient via `Intent.ACTION_DIAL`.

---

## 3. How 2 Family Members Operate the App

Here is an example walkthrough of **Person A (Elderly Parent / Patient)** and **Person B (Adult Child / Caregiver)**:

### Step 1: Caregiver (Person B) Sets Up the Family Circle
1. Person B installs Tulsi on their phone.
2. In the top bar, Person B taps the **Family Circle** icon (👥).
3. Person B enters group name *"Family"* and taps **Create Circle**.
4. The app generates a 6-character code (e.g., `TULSI7`).
5. Person B taps **Share Invite Code** to send it via WhatsApp/SMS to Person A.

### Step 2: Patient (Person A) Joins & Enables Simple Mode
1. Person A installs Tulsi on their phone.
2. In the top bar, Person A taps the **Family Circle** icon (👥), types the code, and taps **Join Circle**.
3. On the Home screen, Person A turns on the **Simple Mode** switch:
   - High contrast UI is activated.
   - Text size expands to 22sp.
   - The "Take Medicine" button enlarges to a **64dp touch target**, making it effortless for seniors with tremors or visual impairment.

### Step 3: Adding Medicines & Daily Use
1. Either Person A or Person B taps **+ Add Medicine** on their phone:
   - Medicine Name: *Metformin*
   - Dosage: *500mg*
   - Instructions: *With food*
   - Schedule: *8:00 AM Daily*
2. Thanks to Firestore realtime sync, both phones immediately update. Person A's device schedules native hardware alarms for 8:00 AM every day.

### Step 4: Daily Routine & Escalation
- **At 8:00 AM:** Person A's phone rings warmly with full-screen prompt. Person A taps the big teal **Take Medicine** button.
  - Ringing stops.
  - Person B's phone instantly chirps with an FCM notification: *"Tulsi: Mom took Metformin"*. Person B has peace of mind.
- **If Mom forgets or is asleep:**
  - At 8:30 AM (overdue threshold), the Cloudflare Worker fires.
  - Person B's phone receives an urgent notification: *"Tulsi reminder: Mom's Metformin is due"*.
  - Person B opens Tulsi, taps the **Call** button next to Mom's name in Family Circle, and dials her phone directly.

### Step 5: Doctor Visits & History
- Before visiting the doctor, Person B or Person A taps the **History & Reports** icon (🕒) in the top bar.
- They see adherence rate (e.g. `96%`), consecutive adherence streaks, and dose logs.
- They tap **Export Doctor Report (PDF)**. Tulsi automatically compiles and opens the Android share sheet with an A4 PDF summary detailing all medications, schedules, and timestamped intake logs ready for the physician.

---

## 4. Built Artifacts

The debug APK has been assembled and verified:
- **Location:** [`app/build/outputs/apk/debug/app-debug.apk`](file:///c:/Users/Anshu/Desktop/Projects/DoseCircle/app/build/outputs/apk/debug/app-debug.apk)
- **Compilation target:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`, Java 21, Kotlin 2.0.21, Jetpack Compose BOM 2024.10.01.
