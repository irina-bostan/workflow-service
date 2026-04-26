import axios from 'axios';
import { Platform } from 'react-native';

// Release-build safety: this file implements USER_PASSWORD_AUTH against a local
// Cognito stub with credentials bundled into the JS via EXPO_PUBLIC_*. Anything
// EXPO_PUBLIC_* is inlined at bundle time, so a release build with .env present
// would ship the password. Release builds must replace this flow with the
// hosted Cognito sign-in (OAuth/PKCE) before being usable.
if (!__DEV__) {
  throw new Error(
    'auth.ts implements USER_PASSWORD_AUTH with bundled credentials — dev-only. ' +
      'Release builds must replace this module with the hosted Cognito sign-in flow.',
  );
}

// In stage/prod, EXPO_PUBLIC_COGNITO_URL is the real Cognito issuer (or its
// regional endpoint). Locally, cognito-local listens on 9229; the Android
// emulator routes loopback through 10.0.2.2.
const COGNITO_HOST =
  process.env.EXPO_PUBLIC_COGNITO_URL ||
  (Platform.OS === 'android' ? 'http://10.0.2.2:9229' : 'http://localhost:9229');

const CLIENT_ID = process.env.EXPO_PUBLIC_COGNITO_CLIENT_ID ?? '';
const USERNAME =
  process.env.EXPO_PUBLIC_COGNITO_USERNAME ?? 'tester@techquarter.local';
const PASSWORD = process.env.EXPO_PUBLIC_COGNITO_PASSWORD ?? 'Test1234!';

let cachedToken: string | null = null;
let inFlight: Promise<string> | null = null;

export async function getIdToken(forceRefresh = false): Promise<string> {
  if (!forceRefresh && cachedToken) return cachedToken;
  if (inFlight) return inFlight;

  if (!CLIENT_ID) {
    throw new Error(
      'EXPO_PUBLIC_COGNITO_CLIENT_ID is not set. Copy ui/.env.example to ui/.env ' +
        'and fill in the Client ID printed by local/setup-cognito.sh.',
    );
  }

  inFlight = (async () => {
    const { data } = await axios.post(
      `${COGNITO_HOST}/`,
      {
        AuthFlow: 'USER_PASSWORD_AUTH',
        ClientId: CLIENT_ID,
        AuthParameters: { USERNAME, PASSWORD },
      },
      {
        headers: {
          'Content-Type': 'application/x-amz-json-1.1',
          'X-Amz-Target': 'AWSCognitoIdentityProviderService.InitiateAuth',
        },
      },
    );
    const token = data?.AuthenticationResult?.IdToken;
    if (!token) throw new Error('No IdToken in cognito-local InitiateAuth response');
    cachedToken = token;
    return token;
  })();

  try {
    return await inFlight;
  } finally {
    inFlight = null;
  }
}

export function clearToken(): void {
  cachedToken = null;
}
