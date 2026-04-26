import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { Platform } from 'react-native';
import { clearToken, getIdToken } from './auth';

// Stage/prod set EXPO_PUBLIC_API_URL explicitly (e.g. https://api.stage.techquarter.com).
// For local dev the fallback handles the Android emulator's loopback quirk
// (10.0.2.2 instead of localhost). EXPO_PUBLIC_* is inlined at bundle time.
const BASE_URL =
  process.env.EXPO_PUBLIC_API_URL ||
  (Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080');

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
});

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await getIdToken();
  config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

// On 401, drop the cached token, re-authenticate, retry the original request once.
apiClient.interceptors.response.use(
  (resp) => resp,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    if (error.response?.status === 401 && original && !original._retry) {
      original._retry = true;
      clearToken();
      const token = await getIdToken(true);
      original.headers.set('Authorization', `Bearer ${token}`);
      return apiClient.request(original);
    }
    return Promise.reject(error);
  },
);
