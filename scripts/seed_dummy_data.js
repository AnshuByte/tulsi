/**
 * Tulsi Test Data Seed Script
 * 
 * Creates repeatable dummy data for the Tulsi application test suite.
 * Targets Firebase Local Emulator Suite:
 *  - Firestore Emulator: http://127.0.0.1:8080
 *  - Auth Emulator:      http://127.0.0.1:9099
 * 
 * Also writes `scripts/dummy_seed_data.json` for offline/mock test usage.
 * 
 * Strict Ground Rules:
 *  - 100% dummy data only.
 *  - Emails: test.*@example.com
 *  - Phone numbers: +1555000000X (fake reserved 555 range)
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'tulsi-family';
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';

const FIRESTORE_BASE = `http://${FIRESTORE_HOST}/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const AUTH_EMULATOR_BASE = `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1`;

// --- Helper Functions to Convert JS Values to Firestore REST Format ---
function toFirestoreValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === 'boolean') return { booleanValue: val };
  if (typeof val === 'number') {
    if (Number.isInteger(val)) return { integerValue: val.toString() };
    return { doubleValue: val };
  }
  if (typeof val === 'string') return { stringValue: val };
  if (Array.isArray(val)) {
    return { arrayValue: { values: val.map(toFirestoreValue) } };
  }
  if (typeof val === 'object') {
    const fields = {};
    for (const [k, v] of Object.entries(val)) {
      if (v !== undefined) fields[k] = toFirestoreValue(v);
    }
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

function toFirestoreFields(obj) {
  const fields = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v !== undefined) fields[k] = toFirestoreValue(v);
  }
  return { fields };
}

async function writeFirestoreDoc(docPath, data) {
  const url = `${FIRESTORE_BASE}/${docPath}`;
  const body = JSON.stringify(toFirestoreFields(data));
  try {
    const res = await fetch(url, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body,
    });
    if (!res.ok) {
      const errText = await res.text();
      console.warn(`[Firestore Emulator Warning] Failed to write ${docPath}: ${res.status} ${errText}`);
      return false;
    }
    return true;
  } catch (err) {
    console.warn(`[Firestore Emulator Offline] Skipping network write for ${docPath}: ${err.message}`);
    return false;
  }
}

async function createAuthUser(user) {
  const url = `${AUTH_EMULATOR_BASE}/accounts:signUp?key=fake-emulator-key`;
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: user.email,
        password: user.password || 'TestPass123!',
        displayName: user.displayName,
        returnSecureToken: true,
      }),
    });
    if (!res.ok) {
      const err = await res.text();
      console.warn(`[Auth Emulator Warning] Could not create user ${user.email}: ${err}`);
      return null;
    }
    const data = await res.json();
    return data.localId;
  } catch (err) {
    console.warn(`[Auth Emulator Offline] Skipping auth network create for ${user.email}: ${err.message}`);
    return null;
  }
}

// --- Data Generation ---
async function generateData() {
  const now = Date.now();
  const ONE_DAY_MS = 24 * 60 * 60 * 1000;

  // 1. Users
  const users = [
    {
      id: 'user_asha_admin',
      displayName: 'Asha (Admin)',
      email: 'test.asha@example.com',
      phoneNumber: '+15550000001',
      currentGroupId: 'group_test_family',
      createdAt: now - 90 * ONE_DAY_MS,
      emailVerified: true,
      role: 'ADMIN',
    },
    {
      id: 'user_ravi_elderly',
      displayName: 'Ravi (Elderly Parent)',
      email: 'test.ravi@example.com',
      phoneNumber: '+15550000002',
      currentGroupId: 'group_test_family',
      createdAt: now - 80 * ONE_DAY_MS,
      emailVerified: true,
      simpleMode: true,
      role: 'MEMBER',
      isPatient: true,
    },
    {
      id: 'user_meera_member',
      displayName: 'Meera (Caregiver Member)',
      email: 'test.meera@example.com',
      phoneNumber: '+15550000003',
      currentGroupId: 'group_test_family',
      createdAt: now - 70 * ONE_DAY_MS,
      emailVerified: true,
      role: 'MEMBER',
      isPatient: false,
    },
    {
      id: 'user_zed_outsider',
      displayName: 'Zed (Outsider)',
      email: 'test.zed@example.com',
      phoneNumber: '+15550000009',
      currentGroupId: 'group_test_other',
      createdAt: now - 60 * ONE_DAY_MS,
      emailVerified: true,
      role: 'ADMIN',
    },
    {
      id: 'user_dave_unverified',
      displayName: 'Dave (Unverified)',
      email: 'test.dave_unverified@example.com',
      phoneNumber: '+15550000010',
      currentGroupId: null,
      createdAt: now - 10 * ONE_DAY_MS,
      emailVerified: false,
    },
    {
      id: 'user_solo_nogroup',
      displayName: 'Solo (No Group)',
      email: 'test.solo@example.com',
      phoneNumber: '+15550000011',
      currentGroupId: null,
      createdAt: now - 5 * ONE_DAY_MS,
      emailVerified: true,
    },
  ];

  // 2. Groups
  const groups = [
    {
      id: 'group_test_family',
      name: 'TEST Family',
      inviteCode: 'TULSI1',
      createdBy: 'user_asha_admin',
      createdAt: now - 90 * ONE_DAY_MS,
      members: [
        {
          userId: 'user_asha_admin',
          displayName: 'Asha (Admin)',
          role: 'ADMIN',
          isPatient: false,
          phoneNumber: '+15550000001',
          joinedAt: now - 90 * ONE_DAY_MS,
        },
        {
          userId: 'user_ravi_elderly',
          displayName: 'Ravi (Elderly Parent)',
          role: 'MEMBER',
          isPatient: true,
          phoneNumber: '+15550000002',
          joinedAt: now - 80 * ONE_DAY_MS,
        },
        {
          userId: 'user_meera_member',
          displayName: 'Meera (Caregiver Member)',
          role: 'MEMBER',
          isPatient: false,
          phoneNumber: '+15550000003',
          joinedAt: now - 70 * ONE_DAY_MS,
        },
      ],
    },
    {
      id: 'group_test_other',
      name: 'TEST Other',
      inviteCode: 'ZED999',
      createdBy: 'user_zed_outsider',
      createdAt: now - 60 * ONE_DAY_MS,
      members: [
        {
          userId: 'user_zed_outsider',
          displayName: 'Zed (Outsider)',
          role: 'ADMIN',
          isPatient: true,
          phoneNumber: '+15550000009',
          joinedAt: now - 60 * ONE_DAY_MS,
        },
      ],
    },
  ];

  // 3. Medicines and Schedules (TEST Family - Ravi)
  const medicines = [
    {
      id: 'med_test_pill_a',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestPill A (Metformin)',
      dosage: '500mg',
      form: 'PILL',
      instructions: 'Take with food and full glass of water',
      colorHex: '#2E7D32',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 60 * ONE_DAY_MS,
      updatedAt: now - 60 * ONE_DAY_MS,
      schedule: {
        id: 'sched_test_pill_a',
        groupId: 'group_test_family',
        medicineId: 'med_test_pill_a',
        type: 'DAILY',
        daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
        timesOfDay: ['08:00', '20:00'],
        startDate: now - 60 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_test_capsule_b',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestCapsule B (Vitamin D3)',
      dosage: '60,000 IU',
      form: 'CAPSULE',
      instructions: 'Take after breakfast on specified weekdays',
      colorHex: '#1565C0',
      escalationWindowMinutes: 45,
      isActive: true,
      createdAt: now - 60 * ONE_DAY_MS,
      updatedAt: now - 60 * ONE_DAY_MS,
      schedule: {
        id: 'sched_test_capsule_b',
        groupId: 'group_test_family',
        medicineId: 'med_test_capsule_b',
        type: 'SPECIFIC_DAYS',
        daysOfWeek: [1, 3, 5], // Monday, Wednesday, Friday
        timesOfDay: ['09:00'],
        startDate: now - 60 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_test_syrup_c',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestSyrup C (Cough Elixir)',
      dosage: '10ml',
      form: 'LIQUID',
      instructions: 'Shake bottle thoroughly; take every 6 hours',
      colorHex: '#F57F17',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 20 * ONE_DAY_MS,
      updatedAt: now - 20 * ONE_DAY_MS,
      schedule: {
        id: 'sched_test_syrup_c',
        groupId: 'group_test_family',
        medicineId: 'med_test_syrup_c',
        type: 'INTERVAL',
        intervalHours: 6,
        timesOfDay: ['06:00', '12:00', '18:00', '00:00'],
        startDate: now - 20 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_test_drops_d',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestDrops D (Eye Lubricant)',
      dosage: '2 drops',
      form: 'DROPS',
      instructions: 'Instill into both eyes as needed for dryness',
      colorHex: '#6A1B9A',
      escalationWindowMinutes: 60,
      isActive: true,
      createdAt: now - 40 * ONE_DAY_MS,
      updatedAt: now - 40 * ONE_DAY_MS,
      schedule: {
        id: 'sched_test_drops_d',
        groupId: 'group_test_family',
        medicineId: 'med_test_drops_d',
        type: 'AS_NEEDED',
        timesOfDay: [],
        startDate: now - 40 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_test_antibiotic_f',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestAntibiotic F (Amoxicillin Course)',
      dosage: '500mg',
      form: 'PILL',
      instructions: 'Take midday; must finish complete 7-day course',
      colorHex: '#C62828',
      escalationWindowMinutes: 20,
      isActive: true,
      createdAt: now - 3 * ONE_DAY_MS,
      updatedAt: now - 3 * ONE_DAY_MS,
      schedule: {
        id: 'sched_test_antibiotic_f',
        groupId: 'group_test_family',
        medicineId: 'med_test_antibiotic_f',
        type: 'DAILY',
        timesOfDay: ['13:00'],
        startDate: now - 3 * ONE_DAY_MS,
        endDate: now + 4 * ONE_DAY_MS,
        isActive: true,
      },
    },
    {
      id: 'med_test_expired_g',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestPill G Expired (Old Antihistamine)',
      dosage: '10mg',
      form: 'PILL',
      instructions: 'Completed course from previous month',
      colorHex: '#757575',
      escalationWindowMinutes: 30,
      isActive: false,
      createdAt: now - 45 * ONE_DAY_MS,
      updatedAt: now - 15 * ONE_DAY_MS,
      schedule: {
        id: 'sched_test_expired_g',
        groupId: 'group_test_family',
        medicineId: 'med_test_expired_g',
        type: 'DAILY',
        timesOfDay: ['10:00'],
        startDate: now - 45 * ONE_DAY_MS,
        endDate: now - 15 * ONE_DAY_MS,
        isActive: false,
      },
    },
    // Edge Data Medicines:
    {
      id: 'med_edge_long_name',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestPill Hydrochlorothiazide Extra Extended-Release Microencapsulated Formulation Long Name Testing Component 500mg Tablets BP',
      dosage: '500mg extended-release sustained bilayer osmotic pump tablet',
      form: 'PILL',
      instructions: 'Swallow whole with 250ml water, do not chew, crush, break or dissolve',
      colorHex: '#00838F',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 10 * ONE_DAY_MS,
      updatedAt: now - 10 * ONE_DAY_MS,
      schedule: {
        id: 'sched_edge_long_name',
        groupId: 'group_test_family',
        medicineId: 'med_edge_long_name',
        type: 'DAILY',
        timesOfDay: ['11:00'],
        startDate: now - 10 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_edge_emoji',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: '💊 CardioPill 🩺 HeartCare ❤️ (Multivitamin ⭐)',
      dosage: '1 Tablet 🌈',
      form: 'PILL',
      instructions: 'Drink with warm water ☕ & smile 😊',
      colorHex: '#E91E63',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 10 * ONE_DAY_MS,
      updatedAt: now - 10 * ONE_DAY_MS,
      schedule: {
        id: 'sched_edge_emoji',
        groupId: 'group_test_family',
        medicineId: 'med_edge_emoji',
        type: 'DAILY',
        timesOfDay: ['12:00'],
        startDate: now - 10 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_edge_hindi',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'तुलसी गोली ५०० मि.ग्रा.',
      dosage: '१ गोली',
      form: 'PILL',
      instructions: 'भोजन के बाद गुनगुने पानी के साथ लें',
      colorHex: '#2E7D32',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 10 * ONE_DAY_MS,
      updatedAt: now - 10 * ONE_DAY_MS,
      schedule: {
        id: 'sched_edge_hindi',
        groupId: 'group_test_family',
        medicineId: 'med_edge_hindi',
        type: 'DAILY',
        timesOfDay: ['07:00'],
        startDate: now - 10 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_edge_kannada',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'ತುಳಸಿ ಮಾತ್ರೆ ೫೦೦ ಮಿ.ಗ್ರಾಂ.',
      dosage: '೧ ಮಾತ್ರೆ',
      form: 'PILL',
      instructions: 'ಊಟದ ನಂತರ ಬಿಸಿ ನೀರಿನೊಂದಿಗೆ ಸೇವಿಸಿ',
      colorHex: '#388E3C',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 10 * ONE_DAY_MS,
      updatedAt: now - 10 * ONE_DAY_MS,
      schedule: {
        id: 'sched_edge_kannada',
        groupId: 'group_test_family',
        medicineId: 'med_edge_kannada',
        type: 'DAILY',
        timesOfDay: ['19:00'],
        startDate: now - 10 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_edge_zero_doses',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestZeroDoses H (Unused Inhaler)',
      dosage: '1 puff',
      form: 'INHALER',
      instructions: 'Prescribed but never taken; zero doses test',
      colorHex: '#607D8B',
      escalationWindowMinutes: 30,
      isActive: true,
      createdAt: now - 5 * ONE_DAY_MS,
      updatedAt: now - 5 * ONE_DAY_MS,
      schedule: {
        id: 'sched_edge_zero_doses',
        groupId: 'group_test_family',
        medicineId: 'med_edge_zero_doses',
        type: 'DAILY',
        timesOfDay: ['16:00'],
        startDate: now - 5 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
    {
      id: 'med_edge_midnight',
      groupId: 'group_test_family',
      assignedMemberId: 'user_ravi_elderly',
      name: 'TestMidnight Rollover (Night Cap)',
      dosage: '5mg',
      form: 'PILL',
      instructions: 'Testing exact 23:59 and 00:00 midnight alarm transitions',
      colorHex: '#311B92',
      escalationWindowMinutes: 15,
      isActive: true,
      createdAt: now - 10 * ONE_DAY_MS,
      updatedAt: now - 10 * ONE_DAY_MS,
      schedule: {
        id: 'sched_edge_midnight',
        groupId: 'group_test_family',
        medicineId: 'med_edge_midnight',
        type: 'DAILY',
        timesOfDay: ['00:00', '23:59'],
        startDate: now - 10 * ONE_DAY_MS,
        endDate: null,
        isActive: true,
      },
    },
  ];

  // 4. Dose History for 60 Days (Generating realistic Taken, Skipped, Missed, Snoozed)
  const doseEvents = [];
  let eventCounter = 1;

  for (let dayOffset = 59; dayOffset >= 0; dayOffset--) {
    const dayStart = new Date(now - dayOffset * ONE_DAY_MS);
    dayStart.setHours(0, 0, 0, 0);
    const dayOfWeek = dayStart.getDay() === 0 ? 7 : dayStart.getDay(); // 1=Mon .. 7=Sun

    // 4a. TestPill A: 08:00 and 20:00 Daily
    const timesA = [
      { hour: 8, minute: 0 },
      { hour: 20, minute: 0 },
    ];
    for (const t of timesA) {
      const scheduledTime = new Date(dayStart);
      scheduledTime.setHours(t.hour, t.minute, 0, 0);
      const scheduledAt = scheduledTime.getTime();

      // Determine outcome with realistic adherence (~80% taken, 10% skipped, 7% missed, 3% snoozed)
      const roll = (eventCounter * 17 + dayOffset * 31) % 100;
      let status = 'TAKEN';
      let actedAt = scheduledAt + ((eventCounter % 15) * 60 * 1000); // 0-15 mins late
      let actedBy = 'user_ravi_elderly';
      let snoozeUntil = null;
      let notes = null;

      if (dayOffset === 0 && scheduledAt > now) {
        status = 'PENDING';
        actedAt = null;
        actedBy = null;
      } else if (roll < 75) {
        status = 'TAKEN';
      } else if (roll < 87) {
        status = 'SKIPPED';
        notes = 'Stomach upset';
      } else if (roll < 95) {
        status = 'MISSED';
        actedAt = null;
        actedBy = null;
      } else {
        status = 'SNOOZED';
        snoozeUntil = scheduledAt + 15 * 60 * 1000;
        notes = 'In a phone call';
      }

      doseEvents.push({
        id: `dose_pill_a_${eventCounter++}`,
        groupId: 'group_test_family',
        medicineId: 'med_test_pill_a',
        medicineName: 'TestPill A (Metformin)',
        dosage: '500mg',
        assignedMemberId: 'user_ravi_elderly',
        scheduleId: 'sched_test_pill_a',
        scheduledAt,
        status,
        actedAt,
        actedBy,
        snoozeUntil,
        notes,
        escalationSent: status === 'MISSED',
        createdAt: scheduledAt,
      });
    }

    // 4b. TestCapsule B: Mon, Wed, Fri at 09:00
    if ([1, 3, 5].includes(dayOfWeek)) {
      const scheduledTime = new Date(dayStart);
      scheduledTime.setHours(9, 0, 0, 0);
      const scheduledAt = scheduledTime.getTime();

      const roll = (eventCounter * 23 + dayOffset * 19) % 100;
      let status = roll < 85 ? 'TAKEN' : (roll < 95 ? 'SKIPPED' : 'MISSED');
      let actedAt = status === 'MISSED' ? null : scheduledAt + 5 * 60 * 1000;

      if (dayOffset === 0 && scheduledAt > now) {
        status = 'PENDING';
        actedAt = null;
      }

      doseEvents.push({
        id: `dose_capsule_b_${eventCounter++}`,
        groupId: 'group_test_family',
        medicineId: 'med_test_capsule_b',
        medicineName: 'TestCapsule B (Vitamin D3)',
        dosage: '60,000 IU',
        assignedMemberId: 'user_ravi_elderly',
        scheduleId: 'sched_test_capsule_b',
        scheduledAt,
        status,
        actedAt,
        actedBy: status === 'MISSED' ? null : 'user_ravi_elderly',
        snoozeUntil: null,
        notes: null,
        escalationSent: status === 'MISSED',
        createdAt: scheduledAt,
      });
    }

    // 4c. TestMidnight: 00:00 and 23:59 (for the last 10 days)
    if (dayOffset < 10) {
      for (const t of [{ hour: 0, minute: 0 }, { hour: 23, minute: 59 }]) {
        const scheduledTime = new Date(dayStart);
        scheduledTime.setHours(t.hour, t.minute, 0, 0);
        const scheduledAt = scheduledTime.getTime();

        const status = scheduledAt > now ? 'PENDING' : 'TAKEN';
        doseEvents.push({
          id: `dose_midnight_${eventCounter++}`,
          groupId: 'group_test_family',
          medicineId: 'med_edge_midnight',
          medicineName: 'TestMidnight Rollover (Night Cap)',
          dosage: '5mg',
          assignedMemberId: 'user_ravi_elderly',
          scheduleId: 'sched_edge_midnight',
          scheduledAt,
          status,
          actedAt: status === 'TAKEN' ? scheduledAt + 60000 : null,
          actedBy: status === 'TAKEN' ? 'user_ravi_elderly' : null,
          snoozeUntil: null,
          notes: null,
          escalationSent: false,
          createdAt: scheduledAt,
        });
      }
    }
  }

  // 5. Stress Test Volume: 1,000 Doses for stress testing
  const stressEvents = [];
  for (let i = 1; i <= 1000; i++) {
    const fakeTime = now - (1000 - i) * (30 * 60 * 1000); // every 30 mins back
    stressEvents.push({
      id: `stress_dose_${i}`,
      groupId: 'group_test_family',
      medicineId: 'med_test_pill_a',
      medicineName: 'TestPill A (Stress Test)',
      dosage: '500mg',
      assignedMemberId: 'user_ravi_elderly',
      scheduleId: 'sched_test_pill_a',
      scheduledAt: fakeTime,
      status: i % 10 === 0 ? 'SKIPPED' : (i % 25 === 0 ? 'MISSED' : 'TAKEN'),
      actedAt: fakeTime + 30000,
      actedBy: 'user_ravi_elderly',
      snoozeUntil: null,
      notes: null,
      escalationSent: false,
      createdAt: fakeTime,
    });
  }

  return {
    users,
    groups,
    medicines,
    doseEvents,
    stressEventsCount: stressEvents.length,
    stressEventsPreview: stressEvents.slice(0, 5),
  };
}

// --- Main Execution ---
async function main() {
  console.log('--- Generating Tulsi Dummy Seed Data ---');
  const seedData = await generateData();

  console.log(`Generated:
  - Users: ${seedData.users.length}
  - Groups: ${seedData.groups.length}
  - Medicines: ${seedData.medicines.length}
  - 60-day Dose Events: ${seedData.doseEvents.length}
  - Stress Test Doses: ${seedData.stressEventsCount}`);

  // Write snapshot to local JSON file
  const outPath = path.join(__dirname, 'dummy_seed_data.json');
  fs.writeFileSync(outPath, JSON.stringify(seedData, null, 2), 'utf-8');
  console.log(`[Success] Saved offline seed data snapshot: ${outPath}`);

  // Attempt to write to live Firebase Emulator if running
  console.log('\n--- Attempting to Seed Firebase Emulator Suite ---');
  let emulatorConnected = false;

  for (const user of seedData.users) {
    const authSuccess = await createAuthUser(user);
    const firestoreSuccess = await writeFirestoreDoc(`users/${user.id}`, {
      id: user.id,
      displayName: user.displayName,
      email: user.email,
      phoneNumber: user.phoneNumber,
      currentGroupId: user.currentGroupId,
      createdAt: user.createdAt,
    });
    if (firestoreSuccess) emulatorConnected = true;
  }

  for (const group of seedData.groups) {
    await writeFirestoreDoc(`groups/${group.id}`, {
      id: group.id,
      name: group.name,
      inviteCode: group.inviteCode,
      createdBy: group.createdBy,
      createdAt: group.createdAt,
    });

    for (const member of group.members) {
      await writeFirestoreDoc(`groups/${group.id}/members/${member.userId}`, member);
    }
  }

  for (const med of seedData.medicines) {
    const { schedule, ...medDoc } = med;
    await writeFirestoreDoc(`groups/${med.groupId}/medicines/${med.id}`, medDoc);
    if (schedule) {
      await writeFirestoreDoc(`groups/${med.groupId}/schedules/${schedule.id}`, schedule);
    }
  }

  console.log(`Seeding 60-day dose history into Firestore emulator...`);
  let seededDoseCount = 0;
  for (const dose of seedData.doseEvents) {
    const ok = await writeFirestoreDoc(`groups/${dose.groupId}/doseEvents/${dose.id}`, dose);
    if (ok) seededDoseCount++;
  }

  if (emulatorConnected) {
    console.log(`[Success] Seeded ${seededDoseCount} dose events into live Firestore Emulator!`);
  } else {
    console.log(`[Info] Firebase emulator was not reachable at ${FIRESTORE_HOST}. Local seed JSON is saved.`);
  }

  console.log('\n--- Seed Data Generation Complete ---');
}

main().catch(console.error);
