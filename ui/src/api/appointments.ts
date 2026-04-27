import { apiClient } from './client';
import type { Appointment, CreateAppointmentRequest } from './types';

export async function listAppointments(bookingId: string): Promise<Appointment[]> {
  const { data } = await apiClient.get<Appointment[]>(`/bookings/${bookingId}/appointments`);
  return data;
}

export async function createAppointment(
  bookingId: string,
  request: CreateAppointmentRequest,
): Promise<Appointment> {
  const { data } = await apiClient.post<Appointment>(`/bookings/${bookingId}/appointments`, request);
  return data;
}
