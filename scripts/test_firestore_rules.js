const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');
const test = require('node:test');
const assert = require('node:assert');

const PROJECT_ID = 'tulsi-family-rules-test';
const RULES_PATH = path.resolve(__dirname, '../firestore.rules');

test('Firestore Security Rules Comprehensive Test Suite', async (t) => {
  let testEnv;

  // Initialize test environment with loaded rules
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: fs.readFileSync(RULES_PATH, 'utf8'),
    },
  });

  // Seed baseline data using bypass security context
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    // 1. Asha User (Admin)
    await db.doc('users/user_asha_admin').set({
      id: 'user_asha_admin',
      displayName: 'Asha',
      email: 'test.asha@example.com',
      currentGroupId: 'group_test_family',
    });

    // 2. Ravi User (Member)
    await db.doc('users/user_ravi_elderly').set({
      id: 'user_ravi_elderly',
      displayName: 'Ravi',
      email: 'test.ravi@example.com',
      currentGroupId: 'group_test_family',
    });

    // 3. Meera User (Member)
    await db.doc('users/user_meera_member').set({
      id: 'user_meera_member',
      displayName: 'Meera',
      email: 'test.meera@example.com',
      currentGroupId: 'group_test_family',
    });

    // 4. Zed User (Outsider in group_test_other)
    await db.doc('users/user_zed_outsider').set({
      id: 'user_zed_outsider',
      displayName: 'Zed',
      email: 'test.zed@example.com',
      currentGroupId: 'group_test_other',
    });

    // 5. TEST Family Group
    await db.doc('groups/group_test_family').set({
      id: 'group_test_family',
      name: 'TEST Family',
      inviteCode: 'TULSI1',
      createdBy: 'user_asha_admin',
    });

    // TEST Family Members
    await db.doc('groups/group_test_family/members/user_asha_admin').set({
      userId: 'user_asha_admin',
      displayName: 'Asha',
      role: 'ADMIN',
    });
    await db.doc('groups/group_test_family/members/user_ravi_elderly').set({
      userId: 'user_ravi_elderly',
      displayName: 'Ravi',
      role: 'MEMBER',
    });

    // Existing Medicine
    await db.doc('groups/group_test_family/medicines/med_paracetamol').set({
      id: 'med_paracetamol',
      name: 'Test Paracetamol',
      groupId: 'group_test_family',
    });

    // Existing Ravi Dose Event
    await db.doc('groups/group_test_family/doseEvents/event_ravi_morning').set({
      id: 'event_ravi_morning',
      groupId: 'group_test_family',
      medicineId: 'med_paracetamol',
      assignedMemberId: 'user_ravi_elderly',
      scheduledAt: 1700000000000,
      status: 'PENDING',
    });

    // TEST Other Group (for Zed)
    await db.doc('groups/group_test_other').set({
      id: 'group_test_other',
      name: 'TEST Other',
      inviteCode: 'ZED999',
      createdBy: 'user_zed_outsider',
    });
    await db.doc('groups/group_test_other/members/user_zed_outsider').set({
      userId: 'user_zed_outsider',
      displayName: 'Zed',
      role: 'ADMIN',
    });

    // Top level invite code
    await db.doc('inviteCodes/TULSI1').set({
      groupId: 'group_test_family',
      code: 'TULSI1',
    });
  });

  // Client contexts
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  const ashaDb = testEnv.authenticatedContext('user_asha_admin').firestore();
  const raviDb = testEnv.authenticatedContext('user_ravi_elderly').firestore();
  const meeraDb = testEnv.authenticatedContext('user_meera_member').firestore();
  const zedDb = testEnv.authenticatedContext('user_zed_outsider').firestore();

  // Test 1: Unauthenticated Access Denials
  await t.test('1. Unauthenticated users cannot read or write to any collection', async () => {
    await assertFails(unauthedDb.doc('users/user_asha_admin').get());
    await assertFails(unauthedDb.doc('groups/group_test_family').get());
    await assertFails(unauthedDb.doc('groups/group_test_family/medicines/med_paracetamol').get());
    await assertFails(unauthedDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').get());
    await assertFails(unauthedDb.doc('inviteCodes/TULSI1').get());
  });

  // Test 2: User Profiles & Data Enumeration
  await t.test('2. User Profiles: Isolation and Enumeration Protection', async () => {
    // Self-access allowed
    await assertSucceeds(ashaDb.doc('users/user_asha_admin').get());
    await assertSucceeds(raviDb.doc('users/user_ravi_elderly').get());

    // Group members sharing a group can read each other's profile (needed for family list)
    await assertSucceeds(ashaDb.doc('users/user_ravi_elderly').get());

    // Outsider Zed CANNOT read Asha or Ravi's profile
    await assertFails(zedDb.doc('users/user_asha_admin').get());
    await assertFails(zedDb.doc('users/user_ravi_elderly').get());

    // Cross-user write denied
    await assertFails(raviDb.doc('users/user_asha_admin').update({ displayName: 'Hacked' }));

    // FCM tokens private to user
    await assertSucceeds(raviDb.doc('users/user_ravi_elderly/fcmTokens/tok1').set({ token: 'abc' }));
    await assertFails(meeraDb.doc('users/user_ravi_elderly/fcmTokens/tok1').get());
    await assertFails(meeraDb.doc('users/user_ravi_elderly/fcmTokens/tok2').set({ token: 'xyz' }));
  });

  // Test 3: Member Joining & Privilege Escalation Protection
  await t.test('3. Member Roles: Prevent Privilege Escalation to ADMIN', async () => {
    // Prospective member joins with valid MEMBER role -> SUCCEEDS
    await assertSucceeds(
      meeraDb.doc('groups/group_test_family/members/user_meera_member').set({
        userId: 'user_meera_member',
        displayName: 'Meera',
        role: 'MEMBER',
      })
    );

    // PRIVILEGE ESCALATION 1: User tries to join directly as ADMIN -> DENIED
    await assertFails(
      testEnv.authenticatedContext('user_attacker').firestore()
        .doc('groups/group_test_family/members/user_attacker').set({
          userId: 'user_attacker',
          displayName: 'Attacker',
          role: 'ADMIN',
        })
    );

    // PRIVILEGE ESCALATION 2: Existing MEMBER Meera tries to promote herself to ADMIN -> DENIED
    await assertFails(
      meeraDb.doc('groups/group_test_family/members/user_meera_member').update({
        role: 'ADMIN',
      })
    );

    // Legitimate ADMIN Asha can promote Meera -> SUCCEEDS
    await assertSucceeds(
      ashaDb.doc('groups/group_test_family/members/user_meera_member').update({
        role: 'ADMIN',
      })
    );

    // Demote Meera back to MEMBER for subsequent member-scoping tests -> SUCCEEDS
    await assertSucceeds(
      ashaDb.doc('groups/group_test_family/members/user_meera_member').update({
        role: 'MEMBER',
      })
    );
  });

  // Test 4: Medicine Catalog & Schedule Administration
  await t.test('4. Medicines & Schedules: Admin-Only Management', async () => {
    // Admin Asha can add and update medicines
    await assertSucceeds(
      ashaDb.doc('groups/group_test_family/medicines/med_new').set({
        id: 'med_new',
        name: 'Test Amoxicillin',
        groupId: 'group_test_family',
      })
    );

    // Admin Asha can manage schedules
    await assertSucceeds(
      ashaDb.doc('groups/group_test_family/schedules/sched_1').set({
        id: 'sched_1',
        medicineId: 'med_new',
        type: 'DAILY',
        times: ['08:00', '20:00'],
      })
    );

    // Regular member Ravi can READ medicines & schedules
    await assertSucceeds(ashaDb.doc('groups/group_test_family/medicines/med_new').get());
    await assertSucceeds(ashaDb.doc('groups/group_test_family/schedules/sched_1').get());

    // Regular member Ravi CANNOT create, edit, or delete medicines
    await assertFails(
      raviDb.doc('groups/group_test_family/medicines/med_tamper').set({
        id: 'med_tamper',
        name: 'Unauthorized Med',
      })
    );
    await assertFails(
      raviDb.doc('groups/group_test_family/medicines/med_new').update({
        name: 'Tampered Med Name',
      })
    );
    await assertFails(raviDb.doc('groups/group_test_family/medicines/med_new').delete());

    // Regular member Ravi CANNOT modify schedules
    await assertFails(
      raviDb.doc('groups/group_test_family/schedules/sched_1').update({
        times: ['12:00'],
      })
    );
  });

  // Test 5: Dose Events Tampering & Forgery Protection
  await t.test('5. Dose Events: Prevent Cross-Member Tampering & Forgery', async () => {
    // Ravi can create his own dose event
    await assertSucceeds(
      raviDb.doc('groups/group_test_family/doseEvents/event_ravi_evening').set({
        id: 'event_ravi_evening',
        groupId: 'group_test_family',
        medicineId: 'med_paracetamol',
        assignedMemberId: 'user_ravi_elderly',
        scheduledAt: 1700036000000,
        status: 'PENDING',
      })
    );

    // Ravi can update his own dose event (e.g. mark TAKEN)
    await assertSucceeds(
      raviDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').update({
        status: 'TAKEN',
        takenAt: Date.now(),
      })
    );

    // FORGERY: Meera attempts to log a dose claiming to be Ravi -> DENIED
    await assertFails(
      meeraDb.doc('groups/group_test_family/doseEvents/event_forged').set({
        id: 'event_forged',
        groupId: 'group_test_family',
        medicineId: 'med_paracetamol',
        assignedMemberId: 'user_ravi_elderly', // trying to log for Ravi
        scheduledAt: 1700040000000,
        status: 'TAKEN',
      })
    );

    // TAMPERING: Meera attempts to update Ravi's existing morning dose event -> DENIED
    await assertFails(
      meeraDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').update({
        status: 'SKIPPED',
      })
    );

    // REASSIGNMENT: Ravi cannot alter assignedMemberId on existing event -> DENIED
    await assertFails(
      raviDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').update({
        assignedMemberId: 'user_meera_member',
      })
    );

    // Regular member cannot delete dose history records
    await assertFails(
      raviDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').delete()
    );

    // Admin Asha CAN manage and audit dose events
    await assertSucceeds(
      ashaDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').update({
        notes: 'Verified by Asha caregiver',
      })
    );
  });

  // Test 6: Cross-Group Isolation (Outsider Zed)
  await t.test('6. Outsider Isolation: Zed Cannot Access TEST Family', async () => {
    // Zed cannot read TEST Family group document
    await assertFails(zedDb.doc('groups/group_test_family').get());

    // Zed cannot read TEST Family medicines
    await assertFails(zedDb.doc('groups/group_test_family/medicines/med_paracetamol').get());

    // Zed cannot read TEST Family members
    await assertFails(zedDb.doc('groups/group_test_family/members/user_asha_admin').get());

    // Zed cannot read TEST Family dose events
    await assertFails(zedDb.doc('groups/group_test_family/doseEvents/event_ravi_morning').get());

    // Zed cannot write or inject into TEST Family
    await assertFails(
      zedDb.doc('groups/group_test_family/doseEvents/event_zed_inject').set({
        id: 'event_zed_inject',
        status: 'TAKEN',
      })
    );
  });

  // Test 7: Invite Codes & Discovery
  await t.test('7. Invite Codes: Safe Resolution Without Group Enumeration', async () => {
    // Authenticated user can read invite code doc to look up groupId
    await assertSucceeds(zedDb.doc('inviteCodes/TULSI1').get());

    // Regular member cannot create an invite code for a group they do not admin
    await assertFails(
      raviDb.doc('inviteCodes/HACK99').set({
        code: 'HACK99',
        groupId: 'group_test_family',
      })
    );

    // Admin Asha can create an invite code
    await assertSucceeds(
      ashaDb.doc('inviteCodes/ASHA88').set({
        code: 'ASHA88',
        groupId: 'group_test_family',
      })
    );
  });

  // Cleanup test environment
  await testEnv.cleanup();
});
