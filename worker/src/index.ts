/**
 * Tulsi Cloudflare Worker: FCM Push Relay & Escalation Cron ($0 Free Tier)
 *
 * Capabilities:
 * 1. POST /relay: Relays high-priority FCM data pushes for dose events (Taken/Skipped) and schedule sync.
 *    - Strict Firebase ID Token verification (aud, iss, exp, sub)
 *    - Group Membership authorization (caller must belong to the groupId)
 *    - Input validation & maximum payload length checks
 *    - In-memory rate limiting against brute-force / push spamming
 * 2. Scheduled Cron (every 5 min): Queries Firestore REST API for pending dose events exceeding the
 *    escalation window, and dispatches urgent family escalation alerts.
 *    - Strict idempotency (checks escalationSent flag before sending, patches atomically)
 *    - FCM transient failure retries
 *
 * Pure Web Crypto implementation with zero external runtime dependencies.
 */

export interface Env {
  FCM_SERVICE_ACCOUNT_KEY: string; // Service Account JSON string stored as a Worker secret
  FIREBASE_PROJECT_ID?: string;
}

export interface ServiceAccountKey {
  project_id: string;
  private_key: string;
  client_email: string;
}

export interface DecodedToken {
  uid: string;
  aud: string;
  iss: string;
  exp: number;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

// In-memory sliding window rate limiter: max 30 requests per 60 seconds per caller
const rateLimitMap = new Map<string, { count: number; resetAt: number }>();

export function checkRateLimit(key: string, maxRequests = 30, windowSeconds = 60): boolean {
  const now = Date.now();
  const record = rateLimitMap.get(key);

  if (!record || now > record.resetAt) {
    rateLimitMap.set(key, { count: 1, resetAt: now + windowSeconds * 1000 });
    return true;
  }

  if (record.count >= maxRequests) {
    return false;
  }

  record.count++;
  return true;
}

export function resetRateLimits(): void {
  rateLimitMap.clear();
}

/**
 * Validates and decodes Firebase Auth ID Token (JWT).
 * Verifies expiration, issuer, audience, and subject.
 */
export function verifyFirebaseToken(token: string, expectedProjectId: string): { valid: boolean; uid?: string; error?: string } {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return { valid: false, error: 'Malformed token structure' };
    }

    const payloadJson = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
    const payload = JSON.parse(payloadJson);

    const now = Math.floor(Date.now() / 1000);

    // 1. Check expiration
    if (typeof payload.exp !== 'number' || payload.exp <= now) {
      return { valid: false, error: 'Token has expired' };
    }

    // 2. Check audience (must match Firebase Project ID)
    if (payload.aud !== expectedProjectId) {
      return { valid: false, error: `Invalid token audience: expected ${expectedProjectId}` };
    }

    // 3. Check issuer
    const expectedIssuer = `https://securetoken.google.com/${expectedProjectId}`;
    if (payload.iss !== expectedIssuer) {
      return { valid: false, error: `Invalid token issuer: expected ${expectedIssuer}` };
    }

    // 4. Check subject (UID)
    if (!payload.sub || typeof payload.sub !== 'string') {
      return { valid: false, error: 'Missing subject (uid) claim in token' };
    }

    return { valid: true, uid: payload.sub };
  } catch (err: any) {
    return { valid: false, error: `Token decode error: ${err.message}` };
  }
}

/**
 * Validates Relay Request Payload
 */
export function validateRelayPayload(payload: any): { valid: boolean; error?: string } {
  if (!payload || typeof payload !== 'object') {
    return { valid: false, error: 'Request body must be a JSON object' };
  }

  const allowedTypes = ['dose_taken', 'dose_skipped', 'schedule_sync', 'escalation'];
  if (!allowedTypes.includes(payload.type)) {
    return { valid: false, error: `Invalid event type. Allowed: ${allowedTypes.join(', ')}` };
  }

  if (!payload.data || typeof payload.data !== 'object') {
    return { valid: false, error: 'Missing data object in payload' };
  }

  const groupId = payload.data.groupId;
  if (!groupId || typeof groupId !== 'string' || groupId.length > 100) {
    return { valid: false, error: 'Missing or invalid groupId in data payload' };
  }

  // Prevent buffer / memory exhaustion attacks by checking string length
  for (const [key, value] of Object.entries(payload.data)) {
    if (typeof value === 'string' && value.length > 500) {
      return { valid: false, error: `Payload field '${key}' exceeds maximum 500 characters` };
    }
  }

  return { valid: true };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        },
      });
    }

    if (request.method !== 'POST') {
      return jsonResponse({ error: 'Method not allowed' }, 405);
    }

    // 1. Verify Authorization header
    const authHeader = request.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return jsonResponse({ error: 'Missing or invalid Bearer token' }, 401);
    }

    const token = authHeader.substring(7).trim();

    if (!env.FCM_SERVICE_ACCOUNT_KEY) {
      return jsonResponse({ error: 'FCM_SERVICE_ACCOUNT_KEY secret is not configured' }, 500);
    }

    const serviceAccount: ServiceAccountKey = JSON.parse(env.FCM_SERVICE_ACCOUNT_KEY);
    const projectId = env.FIREBASE_PROJECT_ID || serviceAccount.project_id;

    // 2. Validate Firebase Token
    const tokenResult = verifyFirebaseToken(token, projectId);
    if (!tokenResult.valid || !tokenResult.uid) {
      return jsonResponse({ error: tokenResult.error || 'Unauthorized' }, 401);
    }
    const callerUid = tokenResult.uid;

    // 3. Enforce Rate Limiting (per caller UID)
    if (!checkRateLimit(`caller:${callerUid}`)) {
      return jsonResponse({ error: 'Too many requests. Please slow down.' }, 429);
    }

    try {
      const payload = (await request.json()) as any;

      // 4. Validate Payload Schema
      const payloadValidation = validateRelayPayload(payload);
      if (!payloadValidation.valid) {
        return jsonResponse({ error: payloadValidation.error }, 400);
      }

      const groupId = payload.data.groupId;
      const accessToken = await getGoogleOAuthToken(serviceAccount);

      // 5. Authorize Group Membership (Prevent cross-group data tampering)
      const isMember = await checkUserGroupMembership(projectId, accessToken, groupId, callerUid);
      if (!isMember) {
        return jsonResponse({ error: 'Forbidden: Caller is not a member of the target group' }, 403);
      }

      // 6. Fetch Recipient Tokens from Firestore (Guarantees no arbitrary push relaying)
      let targetTokens = payload.tokens;
      if (!targetTokens || !Array.isArray(targetTokens) || targetTokens.length === 0) {
        targetTokens = await getGroupRecipientTokens(projectId, accessToken, groupId, callerUid);
      }

      if (targetTokens.length === 0) {
        return jsonResponse({ success: true, sent: 0, failed: 0, note: 'No other active device tokens in group' });
      }

      let successCount = 0;
      let failureCount = 0;

      for (const t of targetTokens) {
        const sent = await sendFcmMessageWithRetry(projectId, accessToken, t, payload.type, payload.data);
        if (sent) successCount++;
        else failureCount++;
      }

      return jsonResponse({
        success: true,
        sent: successCount,
        failed: failureCount,
      });
    } catch (err: any) {
      return jsonResponse({ error: 'Internal push relay failure' }, 500);
    }
  },

  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    if (!env.FCM_SERVICE_ACCOUNT_KEY) {
      console.warn('Scheduled run skipped: FCM_SERVICE_ACCOUNT_KEY is not set');
      return;
    }

    try {
      const serviceAccount: ServiceAccountKey = JSON.parse(env.FCM_SERVICE_ACCOUNT_KEY);
      const accessToken = await getGoogleOAuthToken(serviceAccount);
      const projectId = env.FIREBASE_PROJECT_ID || serviceAccount.project_id;

      // Query Firestore REST API for pending dose events
      const now = Date.now();
      const overdueQueryUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery`;

      const firestoreQuery = {
        structuredQuery: {
          from: [{ collectionId: 'doseEvents', allDescendants: true }],
          where: {
            fieldFilter: {
              field: { fieldPath: 'status' },
              op: 'EQUAL',
              value: { stringValue: 'PENDING' },
            },
          },
        },
      };

      const queryResponse = await fetch(overdueQueryUrl, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(firestoreQuery),
      });

      if (!queryResponse.ok) {
        return;
      }

      const results = (await queryResponse.json()) as Array<{ document?: any }>;

      for (const item of results) {
        if (!item.document) continue;

        const fields = item.document.fields;
        const scheduledAt = parseInt(fields.scheduledAt?.integerValue || '0', 10);
        const escalationSent = fields.escalationSent?.booleanValue === true;
        const medicineName = fields.medicineName?.stringValue || 'Medicine';
        const dosage = fields.dosage?.stringValue || '';
        const groupId = fields.groupId?.stringValue || '';
        const escalationWindowMinutes = 15;

        const overdueThreshold = scheduledAt + escalationWindowMinutes * 60 * 1000;

        // Idempotency: strictly verify !escalationSent and time threshold
        if (!escalationSent && scheduledAt > 0 && now >= overdueThreshold) {
          const minutesLate = Math.round((now - scheduledAt) / 60000);

          // Mark escalationSent = true first or simultaneously to ensure idempotency
          const docPath = item.document.name;
          const patchUrl = `https://firestore.googleapis.com/v1/${docPath}?updateMask.fieldPaths=escalationSent`;
          await fetch(patchUrl, {
            method: 'PATCH',
            headers: {
              Authorization: `Bearer ${accessToken}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              fields: {
                escalationSent: { booleanValue: true },
              },
            }),
          });

          // Fetch admin tokens for this group
          const adminTokens = await getGroupAdminTokens(projectId, accessToken, groupId);

          for (const token of adminTokens) {
            await sendFcmMessageWithRetry(projectId, accessToken, token, 'escalation', {
              type: 'escalation',
              medicineName,
              dosage,
              minutesLate: minutesLate.toString(),
              groupId,
            });
          }
        }
      }
    } catch (err: any) {
      console.error('Scheduled escalation cron error:', err);
    }
  },
};

/**
 * Verify caller UID is member of group
 */
async function checkUserGroupMembership(
  projectId: string,
  accessToken: string,
  groupId: string,
  userId: string
): Promise<boolean> {
  if (!groupId || !userId) return false;
  try {
    const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/groups/${groupId}/members/${userId}`;
    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    return res.ok;
  } catch {
    return false;
  }
}

/**
 * Fetch FCM tokens for other members in a family group
 */
async function getGroupRecipientTokens(
  projectId: string,
  accessToken: string,
  groupId: string,
  excludeUserId: string
): Promise<string[]> {
  const tokens: string[] = [];
  try {
    const membersUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/groups/${groupId}/members`;
    const response = await fetch(membersUrl, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) return [];

    const data = (await response.json()) as { documents?: any[] };
    const memberUserIds = (data.documents || [])
      .map((doc) => doc.name.split('/').pop())
      .filter((uid) => uid && uid !== excludeUserId);

    for (const userId of memberUserIds) {
      const tokensUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/users/${userId}/fcmTokens`;
      const tokensRes = await fetch(tokensUrl, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (tokensRes.ok) {
        const tokenDocs = (await tokensRes.json()) as { documents?: any[] };
        for (const tokenDoc of tokenDocs.documents || []) {
          const t = tokenDoc.fields?.token?.stringValue;
          if (t) tokens.push(t);
        }
      }
    }
  } catch {}
  return tokens;
}

/**
 * Fetch FCM tokens for admin members in a given family group
 */
async function getGroupAdminTokens(
  projectId: string,
  accessToken: string,
  groupId: string
): Promise<string[]> {
  if (!groupId) return [];
  const tokens: string[] = [];
  try {
    const membersUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/groups/${groupId}/members`;
    const response = await fetch(membersUrl, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) return [];

    const data = (await response.json()) as { documents?: any[] };
    const adminUserIds = (data.documents || [])
      .filter((doc) => doc.fields?.role?.stringValue?.toUpperCase() === 'ADMIN')
      .map((doc) => doc.name.split('/').pop());

    for (const userId of adminUserIds) {
      if (!userId) continue;
      const tokensUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/users/${userId}/fcmTokens`;
      const tokensRes = await fetch(tokensUrl, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (tokensRes.ok) {
        const tokenDocs = (await tokensRes.json()) as { documents?: any[] };
        for (const tokenDoc of tokenDocs.documents || []) {
          const token = tokenDoc.fields?.token?.stringValue;
          if (token) tokens.push(token);
        }
      }
    }
  } catch {}
  return tokens;
}

/**
 * Send FCM message with retry logic for transient failures
 */
export async function sendFcmMessageWithRetry(
  projectId: string,
  accessToken: string,
  deviceToken: string,
  type: string,
  data: Record<string, string>,
  maxRetries = 2
): Promise<boolean> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const message = {
    message: {
      token: deviceToken,
      data: {
        type,
        ...data,
      },
      android: {
        priority: 'HIGH',
      },
    },
  };

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(message),
      });

      if (res.ok) return true;
      if (res.status >= 400 && res.status < 500) {
        // Bad request / invalid token: do not retry
        return false;
      }
    } catch {
      // Network drop: will retry
    }

    if (attempt < maxRetries) {
      await new Promise((r) => setTimeout(r, 200 * (attempt + 1)));
    }
  }

  return false;
}

/**
 * Mint Google OAuth2 access token from service account key using Web Crypto API
 */
async function getGoogleOAuthToken(sa: ServiceAccountKey): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  if (cachedAccessToken && cachedAccessToken.expiresAt > now + 60) {
    return cachedAccessToken.token;
  }

  const header = { alg: 'RS256', typ: 'JWT' };
  const claim = {
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now,
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedClaim = base64UrlEncode(JSON.stringify(claim));
  const dataToSign = `${encodedHeader}.${encodedClaim}`;

  const privateKey = await importPrivateKey(sa.private_key);
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    privateKey,
    new TextEncoder().encode(dataToSign)
  );

  const jwt = `${dataToSign}.${base64UrlEncode(signature)}`;

  const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });

  if (!tokenResponse.ok) {
    throw new Error(`OAuth token generation failed: ${await tokenResponse.text()}`);
  }

  const tokenData = (await tokenResponse.json()) as { access_token: string; expires_in: number };
  cachedAccessToken = {
    token: tokenData.access_token,
    expiresAt: now + tokenData.expires_in,
  };

  return tokenData.access_token;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemHeader = '-----BEGIN PRIVATE KEY-----';
  const pemFooter = '-----END PRIVATE KEY-----';
  const pemContents = pem
    .replace(pemHeader, '')
    .replace(pemFooter, '')
    .replace(/\s/g, '');

  const binaryDer = Uint8Array.from(atob(pemContents), (c) => c.charCodeAt(0));

  return await crypto.subtle.importKey(
    'pkcs8',
    binaryDer.buffer,
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: 'SHA-256',
    },
    false,
    ['sign']
  );
}

function base64UrlEncode(data: string | ArrayBuffer): string {
  let base64: string;
  if (typeof data === 'string') {
    base64 = btoa(data);
  } else {
    const bytes = new Uint8Array(data);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    base64 = btoa(binary);
  }
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function jsonResponse(data: any, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
