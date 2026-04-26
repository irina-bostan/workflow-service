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
