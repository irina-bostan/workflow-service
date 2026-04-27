export type ResourceType = 'FLIGHT' | 'HOTEL';
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';
export type AppointmentType = 'CHECK_IN' | 'SPA' | 'RESTAURANT' | 'CONCIERGE';

// Wire shapes — match openapi.yaml exactly. Bare resources, no envelope.

export interface SearchResult {
  providerId: string;
  resourceType: ResourceType;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  availableSeats: number;
  pricePerPerson: number;
  currency: string;
  providerName: string;
}

export interface BookingResponse {
  id: string;
  tripId: string;
  employeeId: string;
  resourceType: ResourceType;
  destination: string;
  departureDate: string;
  returnDate?: string;
  travelerCount: number;
  costCenterRef: string;
  tripPurpose?: string;
  status: BookingStatus;
  providerRef?: string;
  cancellationReason?: string;
  createdAt: string;
}

export interface EmployeeResponse {
  id: string;
  employeeId: string;
  firstName: string;
  lastName: string;
  email: string;
  department: string;
  costCentreDefault?: string;
  createdAt: string;
}

// /bookings GET — matches PagedBookingResponse schema. The array field is `bookings`.
export interface PagedBookingResponse {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  bookings: BookingResponse[];
}

// Server error body — matches the `Error` schema in openapi.yaml.
export interface ApiError {
  source?: string;
  reasonCode?: string;
  description?: string;
  details?: string;
  isRecoverable?: boolean;
}

export interface RegisterEmployeeRequest {
  employeeId: string;
  firstName: string;
  lastName: string;
  email: string;
  department: string;
  costCentreDefault?: string;
}

export interface CreateBookingRequest {
  employeeId: string;
  resourceType: ResourceType;
  destination: string;
  departureDate: string;
  returnDate?: string;
  travelerCount: number;
  costCenterRef: string;
  tripPurpose?: string;
  /** Optional — supply a tripId from a prior booking response to link this booking
   *  into the same trip. Cancellation cascades across siblings sharing a tripId. */
  tripId?: string;
}

export interface SearchParams {
  resourceType: ResourceType;
  destination: string;
  departureDate?: string;
  returnDate?: string;
  travelerCount?: number;
}

export interface Appointment {
  id: string;
  bookingId: string;
  appointmentType: AppointmentType;
  scheduledAt: string;
  notes?: string;
  createdAt: string;
}

export interface CreateAppointmentRequest {
  appointmentType: AppointmentType;
  scheduledAt: string;
  notes?: string;
}
