import type { ApiError } from './types';

/**
 * Extract a human-readable message from anything thrown out of an API call.
 *
 * Order of preference:
 *  1. The server's `description` field (the user-facing reason from `ApiError`).
 *  2. The server's `details` field (technical detail when `description` is generic).
 *  3. The error's own `.message` (network errors, timeouts, etc.).
 *  4. The supplied fallback.
 *
 * Doesn't depend on `axios.isAxiosError` so it works equally well in tests where
 * the rejection is a plain object shaped like an Axios error.
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: ApiError } } | null)?.response?.data;
  if (data?.description) return data.description;
  if (data?.details) return data.details;
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}
