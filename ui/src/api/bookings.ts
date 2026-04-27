import * as Crypto from 'expo-crypto';
import { apiClient } from './client';
import type {
  BookingResponse,
  CreateBookingRequest,
  PagedBookingResponse,
} from './types';

export async function createBooking(request: CreateBookingRequest): Promise<BookingResponse> {
  const { data } = await apiClient.post<BookingResponse>('/bookings', request, {
    headers: { 'Idempotency-Key': Crypto.randomUUID() },
  });
  return data;
}

export async function listBookings(
  employeeId: string,
  page = 0,
  size = 20,
): Promise<PagedBookingResponse> {
  const { data } = await apiClient.get<PagedBookingResponse>('/bookings', {
    params: { employeeId, page, size },
  });
  return data;
}

export async function getBooking(id: string): Promise<BookingResponse> {
  const { data } = await apiClient.get<BookingResponse>(`/bookings/${id}`);
  return data;
}

export async function cancelBooking(id: string): Promise<BookingResponse> {
  const { data } = await apiClient.post<BookingResponse>(`/bookings/${id}/cancel`);
  return data;
}

export async function listTripBookings(tripId: string): Promise<BookingResponse[]> {
  const { data } = await apiClient.get<BookingResponse[]>(`/trips/${tripId}/bookings`);
  return data;
}
