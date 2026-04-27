import { useEffect, useRef } from 'react';
import type { BookingResponse } from '../api/types';

const DEFAULT_INTERVAL_MS = 3000;

/**
 * While any booking in the list is PENDING, runs {@code refresh} on the given interval.
 * Stops automatically when no PENDING rows remain or the component unmounts.
 *
 * The refresh callback is held in a ref so changing it doesn't tear down the interval.
 */
export function usePollWhilePending(
  bookings: BookingResponse[],
  refresh: () => void,
  intervalMs: number = DEFAULT_INTERVAL_MS,
) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;

  const hasPending = bookings.some((b) => b.status === 'PENDING');

  useEffect(() => {
    if (!hasPending) return;
    const id = setInterval(() => refreshRef.current(), intervalMs);
    return () => clearInterval(id);
  }, [hasPending, intervalMs]);
}
