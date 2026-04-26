import axios from 'axios';
import { clearToken, getIdToken } from '../auth';

jest.mock('axios');

const mockedAxios = axios as jest.Mocked<typeof axios>;

beforeEach(() => {
  jest.resetAllMocks();
  clearToken();
});

describe('getIdToken', () => {
  it('caches the token across calls — only one InitiateAuth round-trip per session', async () => {
    mockedAxios.post.mockResolvedValueOnce({
      data: { AuthenticationResult: { IdToken: 'tok-A' } },
    });

    const a = await getIdToken();
    const b = await getIdToken();

    expect(a).toBe('tok-A');
    expect(b).toBe('tok-A');
    expect(mockedAxios.post).toHaveBeenCalledTimes(1);
  });

  it('forceRefresh=true bypasses the cache and re-fetches', async () => {
    mockedAxios.post
      .mockResolvedValueOnce({ data: { AuthenticationResult: { IdToken: 'tok-1' } } })
      .mockResolvedValueOnce({ data: { AuthenticationResult: { IdToken: 'tok-2' } } });

    const first = await getIdToken();
    const refreshed = await getIdToken(true);

    expect(first).toBe('tok-1');
    expect(refreshed).toBe('tok-2');
    expect(mockedAxios.post).toHaveBeenCalledTimes(2);
  });

  it('dedupes concurrent calls — single InitiateAuth even when fired simultaneously', async () => {
    let resolveAuth: (v: unknown) => void = () => {};
    const authPromise = new Promise((resolve) => {
      resolveAuth = resolve;
    });
    mockedAxios.post.mockReturnValueOnce(authPromise as Promise<{ data: unknown }>);

    const calls = Promise.all([getIdToken(), getIdToken(), getIdToken()]);
    resolveAuth({ data: { AuthenticationResult: { IdToken: 'tok-shared' } } });
    const tokens = await calls;

    expect(tokens).toEqual(['tok-shared', 'tok-shared', 'tok-shared']);
    expect(mockedAxios.post).toHaveBeenCalledTimes(1);
  });

  // Note: the "throws when EXPO_PUBLIC_COGNITO_CLIENT_ID is unset" guard is deployment-time
  // misconfiguration insurance. We don't test it here because babel-preset-expo *inlines* the
  // EXPO_PUBLIC_* value at transform time, so resetting the env var at runtime has no effect.
  // The guard is verified manually by booting the app without the env file.
});

describe('release-build safety guard', () => {
  it('throws at module load when __DEV__ is false (would ship credentials in a prod bundle)', () => {
    const original = (globalThis as { __DEV__?: boolean }).__DEV__;
    (globalThis as { __DEV__?: boolean }).__DEV__ = false;
    try {
      expect(() => {
        jest.isolateModules(() => {
          require('../auth');
        });
      }).toThrow(/dev-only|release build/i);
    } finally {
      (globalThis as { __DEV__?: boolean }).__DEV__ = original;
    }
  });
});
