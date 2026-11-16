/**
 * Tulsi Test Data Reset Script
 * 
 * Clears all test data from the Firebase Local Emulator Suite:
 *  - Clears Firestore emulator documents
 *  - Clears Auth emulator user accounts
 * 
 * Safe: Strictly targets local emulator hosts (127.0.0.1:8080 and 127.0.0.1:9099).
 */

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'tulsi-family';
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';

async function resetFirestore() {
  const url = `http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
  console.log(`Clearing Firestore Emulator at ${url}...`);
  try {
    const res = await fetch(url, { method: 'DELETE' });
    if (res.ok) {
      console.log('✓ Firestore Emulator cleared successfully.');
    } else {
      console.warn(`Firestore clear returned status: ${res.status} ${await res.text()}`);
    }
  } catch (err) {
    console.log(`Firestore emulator is not running at ${FIRESTORE_HOST} (offline).`);
  }
}

async function resetAuth() {
  const url = `http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts`;
  console.log(`Clearing Auth Emulator at ${url}...`);
  try {
    const res = await fetch(url, { method: 'DELETE' });
    if (res.ok) {
      console.log('✓ Auth Emulator cleared successfully.');
    } else {
      console.warn(`Auth clear returned status: ${res.status} ${await res.text()}`);
    }
  } catch (err) {
    console.log(`Auth emulator is not running at ${AUTH_HOST} (offline).`);
  }
}

async function main() {
  console.log('--- Resetting Tulsi Test Environment ---');
  await resetFirestore();
  await resetAuth();
  console.log('--- Reset Complete ---');
}

main().catch(console.error);
