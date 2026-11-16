# Project: DoseCircle (Android) – V1

## Product summary
DoseCircle is an Android medicine reminder app for families. A user can add
medicines for themselves or for family members. The patient gets a loud,
alarm-style reminder; the family admin is notified in realtime, and gets an
escalation alert if the dose isn't marked as taken in time.

Tagline: "Take care, together."

## HARD CONSTRAINT: $0 only
Use only free services that need no billing account or credit card:
Firebase Spark plan (Auth, Firestore, FCM) and Cloudflare Workers free tier.
No Cloud Functions, no SMS/phone auth, no paid APIs. If something would need
payment, stop and propose a free alternative.

## Brand
- Personality: warm, calm, reassuring, never clinical or scary
- Voice: short, friendly, plain language. Example: "Time for Mom's BP tablet",
  not "Medication adherence event triggered"
- Colors:
  - Primary teal #0F9D8A
  - Deep navy (text, headers) #12304A
  - Soft background #F6FAF9
  - Success green (Taken) #2E9E5B
  - Amber (Snooze) #F2A93B
  - Coral (Missed / urgent) #F26B5B
  - Grey (Skipped) #8A97A3
- Typography: Poppins for headings, Inter for body. Min 16sp body text.
- Shapes: rounded corners (16dp), soft shadows, friendly icons
  (pill, heart, circle of people)
- Logo concept: a ring of small connected dots around a pill
- Light and dark themes

## Tech stack (all free)
- Kotlin + Jetpack Compose, Material 3 themed with the brand above
- MVVM + Hilt, Room (local cache), Coroutines/Flow
- Firebase Spark: Auth (email/password + Google Sign-In), Firestore, FCM
- AlarmManager exact alarms + foreground service + WorkManager
- Cloudflare Workers (free tier, with cron trigger) as a small push relay
- Min SDK 26, target latest stable SDK
- Distribute via APK first; Play Store is optional later

## V1 features (in scope)
1. Onboarding and login
   - Email/password and Google Sign-In
   - Permission flow with a friendly explanation for each: notifications,
     exact alarms, full-screen intent, battery optimization exemption
2. Family group
   - Create a group, invite members by link or invite code
   - Roles: Admin (manages all medicines) and Member
3. Medicines and schedules
   - Add/edit/delete: name, dosage, form (tablet/syrup/injection), notes,
     assigned family member
   - Flexible schedules: daily, specific weekdays, every X hours, as-needed,
     with start/end dates; multiple times per day
4. Reminders
   - Loud full-screen alarm-style reminder, rings even in silent/DND
   - Buttons: Taken, Snooze (5/10/15 min), Skip
   - Works offline using local alarms; actions sync when back online
5. Realtime family sync and escalation
   - Firestore snapshot listeners give live updates across family devices
     (dose status, schedule changes)
   - Firestore offline persistence; writes queue offline and sync later
   - When a dose is due, the member gets the alarm and the admin gets a
     gentle heads-up
   - When the member taps Taken/Skipped, the admin is notified instantly
   - If no response within the escalation window (default 15 min,
     configurable per medicine), the admin gets: "[Name] hasn't taken
     [Medicine]" with a one-tap Call button
6. History and adherence
   - Dose log: Taken / Skipped / Missed with timestamps
   - Calendar view, streaks, adherence % per person
   - Export a PDF report to share with a doctor
7. Simple Mode toggle: larger text and buttons, fewer screens, for elderly users

## Realtime and push architecture (serverless, free)
- Firestore is the source of truth.
- FCM high-priority data messages wake the app when it is closed.
- A Cloudflare Worker with a cron trigger sends FCM pushes for:
  1. Dose status change (Taken/Skipped) -> notify admin instantly
  2. Escalation timeout (no response in X min) -> notify admin
- Keep the Worker small and stateless. Store FCM service account credentials
  in Worker secrets, never in the app. Verify the caller (Firebase ID token)
  before sending any push.
- Local alarms remain the backup layer: patient alarms and admin escalation
  checks are also scheduled on-device, so reminders still fire without
  internet. If the admin's check can't reach Firestore, alert anyway
  (a false alarm is safer than a missed one).
- Reschedule all alarms on reboot (BOOT_COMPLETED) and whenever schedules change.
- Stay within free-tier limits and avoid unnecessary reads/writes
  (use listeners scoped to the family group, not whole collections).

## Out of scope for V1 (V2)
Refill/stock alerts, barcode and pill photo scan, doctor appointment
reminders, drug interaction warnings, extra languages, home-screen widget.
(Design the data model so these can be added later.)

## Key screens
Splash/Onboarding -> Login -> Permissions setup -> Home (today's doses,
grouped by person) -> Add/Edit Medicine -> Family (members, invites) ->
Alarm screen -> History/Reports -> Settings (escalation, notifications,
Simple Mode, theme)

## Data model (Firestore)
users, groups, members (role), medicines, schedules, doseEvents
(status: pending/taken/skipped/snoozed/missed, scheduledAt, actedAt, actedBy)
Write least-privilege Firestore security rules: members can only access
their own group's data; only admins can edit other members' medicines.

## Non-functional requirements
- Reminders must fire on time in Doze mode and after reboot
- Handle aggressive OEM battery savers (Xiaomi, Oppo, Vivo, Realme) with a
  guided setup screen
- Encrypt sensitive data in transit and at rest; keep health data private
- Include a privacy policy screen and a note that the app does not give
  medical advice
- Accessibility: TalkBack support, high contrast, 48dp minimum tap targets

## Work plan
Work in small steps, explain decisions briefly, and ask me questions if
anything is unclear before coding. Order:
1. Propose project structure and Gradle setup
2. Build the brand theme (colors, typography, shapes) in Compose
3. Set up Firebase (Auth, Firestore + rules) and the data model
4. Medicine add/edit with schedules and local exact alarms + alarm screen
5. Family groups with Firestore realtime sync
6. Cloudflare Worker push relay + escalation
7. History, adherence report, PDF export, Simple Mode
Pause for my review after steps 4 and 6.