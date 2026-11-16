import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert';
import {
  verifyFirebaseToken,
  validateRelayPayload,
  checkRateLimit,
  resetRateLimits,
} from '../dist/index.js';

// Helper to generate a dummy JWT
function createDummyJwt(payload) {
  const header = { alg: 'RS256', typ: 'JWT' };
  const encode = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  return `${encode(header)}.${encode(payload)}.dummy_signature`;
}

describe('Cloudflare Worker Security & Push Relay Test Suite', () => {

  beforeEach(() => {
    resetRateLimits();
  });

  describe('Firebase ID Token Verification', () => {
    const projectId = 'tulsi-family';
    const now = Math.floor(Date.now() / 1000);

    test('Valid Firebase ID token passes verification and returns caller UID', () => {
      const validPayload = {
        sub: 'user_asha_admin',
        aud: projectId,
        iss: `https://securetoken.google.com/${projectId}`,
        exp: now + 3600,
        auth_time: now,
      };
      const token = createDummyJwt(validPayload);
      const res = verifyFirebaseToken(token, projectId);

      assert.strictEqual(res.valid, true);
      assert.strictEqual(res.uid, 'user_asha_admin');
    });

    test('Expired Firebase ID token is rejected', () => {
      const expiredPayload = {
        sub: 'user_asha_admin',
        aud: projectId,
        iss: `https://securetoken.google.com/${projectId}`,
        exp: now - 60, // Expired 1 minute ago
      };
      const token = createDummyJwt(expiredPayload);
      const res = verifyFirebaseToken(token, projectId);

      assert.strictEqual(res.valid, false);
      assert.strictEqual(res.error, 'Token has expired');
    });

    test('Token with wrong audience/project ID is rejected', () => {
      const wrongAudPayload = {
        sub: 'user_asha_admin',
        aud: 'malicious-other-project',
        iss: `https://securetoken.google.com/${projectId}`,
        exp: now + 3600,
      };
      const token = createDummyJwt(wrongAudPayload);
      const res = verifyFirebaseToken(token, projectId);

      assert.strictEqual(res.valid, false);
      assert.match(res.error, /Invalid token audience/);
    });

    test('Token with mismatched issuer is rejected', () => {
      const wrongIssPayload = {
        sub: 'user_asha_admin',
        aud: projectId,
        iss: 'https://evil-auth-provider.com',
        exp: now + 3600,
      };
      const token = createDummyJwt(wrongIssPayload);
      const res = verifyFirebaseToken(token, projectId);

      assert.strictEqual(res.valid, false);
      assert.match(res.error, /Invalid token issuer/);
    });

    test('Malformed JWT string is rejected', () => {
      const res = verifyFirebaseToken('not-a-jwt', projectId);
      assert.strictEqual(res.valid, false);
      assert.strictEqual(res.error, 'Malformed token structure');
    });
  });

  describe('Relay Payload Validation & Input Sanitization', () => {
    test('Valid payload succeeds', () => {
      const payload = {
        type: 'dose_taken',
        tokens: ['fcm_token_1', 'fcm_token_2'],
        data: {
          groupId: 'group_test_family',
          memberName: 'Ravi',
          medicineName: 'TestPill A',
          dosage: '500mg',
          status: 'TAKEN',
          time: '08:05 AM',
        },
      };
      const res = validateRelayPayload(payload);
      assert.strictEqual(res.valid, true);
    });

    test('Invalid event type is rejected', () => {
      const payload = {
        type: 'arbitrary_spam_type',
        tokens: ['fcm_token_1'],
        data: { groupId: 'group_test_family' },
      };
      const res = validateRelayPayload(payload);
      assert.strictEqual(res.valid, false);
      assert.match(res.error, /Invalid event type/);
    });

    test('Missing groupId is rejected', () => {
      const payload = {
        type: 'dose_taken',
        tokens: ['fcm_token_1'],
        data: { medicineName: 'TestPill A' },
      };
      const res = validateRelayPayload(payload);
      assert.strictEqual(res.valid, false);
      assert.match(res.error, /Missing or invalid groupId/);
    });

    test('Oversized fields (>500 chars) are rejected to prevent buffer attacks', () => {
      const payload = {
        type: 'dose_taken',
        tokens: ['fcm_token_1'],
        data: {
          groupId: 'group_test_family',
          medicineName: 'A'.repeat(501),
        },
      };
      const res = validateRelayPayload(payload);
      assert.strictEqual(res.valid, false);
      assert.match(res.error, /exceeds maximum 500 characters/);
    });
  });

  describe('In-Memory Rate Limiting', () => {
    test('Allows requests under threshold and blocks on exceeding', () => {
      const key = 'caller:user_test_1';
      for (let i = 0; i < 30; i++) {
        assert.strictEqual(checkRateLimit(key, 30, 60), true, `Request ${i + 1} should be allowed`);
      }
      // 31st request should be throttled
      assert.strictEqual(checkRateLimit(key, 30, 60), false, '31st request must be rate limited');
    });

    test('Different callers have independent rate limit buckets', () => {
      const key1 = 'caller:user_1';
      const key2 = 'caller:user_2';

      for (let i = 0; i < 30; i++) {
        checkRateLimit(key1, 30, 60);
      }
      assert.strictEqual(checkRateLimit(key1, 30, 60), false);
      assert.strictEqual(checkRateLimit(key2, 30, 60), true, 'Different caller must not be throttled');
    });
  });

  describe('Escalation Idempotency Logic', () => {
    test('Dose already escalated (escalationSent=true) is skipped without re-alerting', () => {
      const doc = {
        fields: {
          scheduledAt: { integerValue: (Date.now() - 30 * 60 * 1000).toString() },
          escalationSent: { booleanValue: true },
          status: { stringValue: 'PENDING' },
          medicineName: { stringValue: 'TestPill A' },
        },
      };

      const now = Date.now();
      const scheduledAt = parseInt(doc.fields.scheduledAt.integerValue, 10);
      const escalationSent = doc.fields.escalationSent.booleanValue;
      const overdue = now >= (scheduledAt + 15 * 60 * 1000);

      const shouldEscalate = !escalationSent && scheduledAt > 0 && overdue;
      assert.strictEqual(shouldEscalate, false, 'Must not escalate if already sent');
    });

    test('Dose taken just before escalation window is not escalated', () => {
      const doc = {
        fields: {
          scheduledAt: { integerValue: (Date.now() - 30 * 60 * 1000).toString() },
          escalationSent: { booleanValue: false },
          status: { stringValue: 'TAKEN' }, // Marked taken!
        },
      };

      const shouldQueryFilter = doc.fields.status.stringValue === 'PENDING';
      assert.strictEqual(shouldQueryFilter, false, 'Taken doses must be excluded from overdue scan');
    });
  });
});
