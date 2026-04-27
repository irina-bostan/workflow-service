import { useEffect, useRef, useState } from 'react';
import { getBooking } from '../api/bookings';
import type { BookingResponse } from '../api/types';

const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 30000;

export interface BookingDetailState {
  booking: BookingResponse | null;
  loading: boolean;
  error: unknown;
  /** True while a status === PENDING booking is actively being polled. */
  polling: boolean;
  /** Externally apply a server response (e.g. after a cancel POST returns the
   *  updated booking) so the UI doesn't wait for the next poll tick. */
  setBooking: (b: BookingResponse) => void;
}

/**
 * Fetches a booking by id and, while its status is PENDING, polls every 2 s
 * until the status flips (CONFIRMED / CANCELLED) or the 30 s safety timeout
 * elapses. Cleans up its interval on unmount or status change.
 */
export function useBookingDetail(id: string): BookingDetailState {
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<unknown>(null);
  const [polling, setPolling] = useState<boolean>(false);

  // Refs so the interval callback can read the latest values without re-binding.
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const stopAtRef = useRef<number>(0);
  const cancelledRef = useRef<boolean>(false);

  useEffect(() => {
    cancelledRef.current = false;
    stopAtRef.current = Date.now() + POLL_TIMEOUT_MS;

    const fetchOnce = async () => {
      try {
        const next = await getBooking(id);
        if (cancelledRef.current) return;
        setBooking(next);
        setError(null);
        if (next.status !== 'PENDING' || Date.now() >= stopAtRef.current) {
          stopPolling();
        }
      } catch (e) {
        if (cancelledRef.current) return;
        setError(e);
        stopPolling();
      } finally {
        if (!cancelledRef.current) setLoading(false);
      }
    };

    const stopPolling = () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      setPolling(false);
    };

    // Initial fetch — once we have the first response, start polling only if PENDING.
    void fetchOnce().then(() => {
      if (cancelledRef.current) return;
      setBooking((current) => {
        if (current && current.status === 'PENDING' && !intervalRef.current) {
          setPolling(true);
          intervalRef.current = setInterval(fetchOnce, POLL_INTERVAL_MS);
        }
        return current;
      });
    });

    return () => {
      cancelledRef.current = true;
      stopPolling();
    };
  }, [id]);

  return { booking, loading, error, polling, setBooking };
}
