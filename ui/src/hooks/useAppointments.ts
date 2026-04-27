import { useCallback, useEffect, useState } from 'react';
import { createAppointment, listAppointments } from '../api/appointments';
import type { Appointment, CreateAppointmentRequest } from '../api/types';

export interface AppointmentsState {
  appointments: Appointment[];
  loading: boolean;
  error: unknown;
  add: (request: CreateAppointmentRequest) => Promise<Appointment>;
}

export function useAppointments(bookingId: string, enabled: boolean): AppointmentsState {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState<boolean>(enabled);
  const [error, setError] = useState<unknown>(null);

  const refresh = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    try {
      const items = await listAppointments(bookingId);
      setAppointments(items);
      setError(null);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [bookingId, enabled]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const add = useCallback(
    async (request: CreateAppointmentRequest) => {
      const created = await createAppointment(bookingId, request);
      setAppointments((prev) => [...prev, created].sort(byScheduledAt));
      return created;
    },
    [bookingId],
  );

  return { appointments, loading, error, add };
}

const byScheduledAt = (a: Appointment, b: Appointment) =>
  new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime();
