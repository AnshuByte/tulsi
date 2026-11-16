/**
 * Tulsi Cloudflare Worker: FCM Push Relay & Escalation Cron ($0 Free Tier)
 *
 * Capabilities:
 * 1. POST /relay: Relays high-priority FCM data pushes for dose events (Taken/Skipped) and schedule sync.
 * 2. Scheduled Cron (every 5 min): Queries Firestore REST API for pending dose events exceeding the
 *    escalation window, and dispatches urgent family escalation alerts.
 *
 * Pure Web Crypto implementation with zero external runtime dependencies.
 */

export interface Env {
  FCM_SERVICE_ACCOUNT_KEY: string; // Service Account JSON string stored as a Worker secret
  FIREBASE_PROJECT_ID?: string;
}

interface ServiceAccountKey {
  project_id: string;
  private_key: string;
  client_email: string;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

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

    // Verify Authorization header
    const authHeader = request.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return jsonResponse({ error: 'Missing or invalid Bearer token' }, 401);
    }

    try {
      const payload = (await request.json()) as {
        type: string; // 'dose_taken' | 'dose_skipped' | 'schedule_sync' | 'escalation'
        tokens: string[];
        data: Record<string, string>;
      };

      if (!payload.tokens || payload.tokens.length === 0) {
        return jsonResponse({ error: 'No recipient tokens provided' }, 400);
      }

      if (!env.FCM_SERVICE_ACCOUNT_KEY) {
        return jsonResponse({ error: 'FCM_SERVICE_ACCOUNT_KEY secret is not configured' }, 500);
      }

      const serviceAccount: ServiceAccountKey = JSON.parse(env.FCM_SERVICE_ACCOUNT_KEY);
      const accessToken = await getGoogleOAuthToken(serviceAccount);

      let successCount = 0;
      let failureCount = 0;

      for (const token of payload.tokens) {
        const sent = await sendFcmMessage(
          serviceAccount.project_id,
          accessToken,
          token,
          payload.type,
          payload.data
        );
        if (sent) successCount++;
        else failureCount++;
      }

      return jsonResponse({
        success: true,
        sent: successCount,
        failed: failureCount,
      });
    } catch (err: any) {
      return jsonResponse({ error: err.message || 'Push relay failure' }, 500);
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
        console.error('Firestore overdue query failed:', await queryResponse.text());
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
        const escalationWindowMinutes = 15; // default 15m timeout

        const overdueThreshold = scheduledAt + escalationWindowMinutes * 60 * 1000;

        if (!escalationSent && scheduledAt > 0 && now >= overdueThreshold) {
          const minutesLate = Math.round((now - scheduledAt) / 60000);

          // Fetch admin tokens for this group
          const adminTokens = await getGroupAdminTokens(projectId, accessToken, groupId);

          for (const token of adminTokens) {
            await sendFcmMessage(projectId, accessToken, token, 'escalation', {
              type: 'escalation',
              medicineName,
              dosage,
              minutesLate: minutesLate.toString(),
              groupId,
            });
          }

          // Mark escalationSent = true to prevent re-alerting
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
        }
      }
    } catch (err: any) {
      console.error('Scheduled escalation cron error:', err);
    }
  },
};

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
      .filter((doc) => doc.fields?.role?.stringValue === 'ADMIN')
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
  } catch (err) {
    console.error('Failed to get group admin tokens:', err);
  }

  return tokens;
}

/**
 * Sends an FCM v1 HTTP message to a specific device token
 */
async function sendFcmMessage(
  projectId: string,
  accessToken: string,
  deviceToken: string,
  type: string,
  data: Record<string, string>
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

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(message),
    });

    return res.ok;
  } catch (_e) {
    return false;
  }
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

/**
 * Import PEM RSA private key into CryptoKey
 */
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
