import React from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';
import { BookingDetailScreen } from '../BookingDetailScreen';
import { cancelBooking, getBooking, listTripBookings } from '../../api/bookings';
import type { BookingResponse } from '../../api/types';

jest.mock('../../api/bookings');
const mockedGet = getBooking as jest.MockedFunction<typeof getBooking>;
const mockedCancel = cancelBooking as jest.MockedFunction<typeof cancelBooking>;
const mockedListTrip = listTripBookings as jest.MockedFunction<typeof listTripBookings>;

function flightBooking(status: BookingResponse['status'], extras: Partial<BookingResponse> = {}): BookingResponse {
  return {
    id: 'b1',
    tripId: 'trip-1',
    employeeId: 'EMP9876',
    resourceType: 'FLIGHT',
    destination: 'NYC',
    departureDate: '2027-11-05T08:00:00Z',
    travelerCount: 1,
    costCenterRef: 'CC-456',
    status,
    createdAt: '2026-04-27T00:00:00Z',
    ...extras,
  };
}

function renderScreen() {
  const route = { params: { id: 'b1' } } as any;
  return render(
    <PaperProvider>
      <BookingDetailScreen route={route} />
    </PaperProvider>,
  );
}

beforeEach(() => {
  jest.useFakeTimers();
  jest.resetAllMocks();
  // Default: trip-of-one. Individual tests can override to exercise the cascade path.
  mockedListTrip.mockResolvedValue([]);
});

afterEach(() => {
  jest.useRealTimers();
});

describe('BookingDetailScreen', () => {
  it('advances the workflow stepper from PENDING to CONFIRMED as polling resolves', async () => {
    mockedGet
      .mockResolvedValueOnce(flightBooking('PENDING'))
      .mockResolvedValueOnce(flightBooking('CONFIRMED', { providerRef: 'DUFFEL-MOCK-A1B2C3D4' }));

    const { findByText, queryByText } = renderScreen();

    // Initial render — first fetch resolves to PENDING; "Reserving" step is active.
    await findByText(/Reserving with provider/i);
    expect(queryByText(/DUFFEL-MOCK-A1B2C3D4/)).toBeNull();

    // Trigger the 2 s poll; the second fetch resolves to CONFIRMED.
    await act(async () => {
      jest.advanceTimersByTime(2100);
    });

    await waitFor(() => {
      expect(queryByText(/DUFFEL-MOCK-A1B2C3D4/)).not.toBeNull();
    });
    expect(mockedGet).toHaveBeenCalledTimes(2);
  });

  it('renders the cancellation reason when the booking is CANCELLED', async () => {
    mockedGet.mockResolvedValueOnce(
      flightBooking('CANCELLED', { cancellationReason: 'Provider rejected: no availability' }),
    );

    const { findByText } = renderScreen();

    await findByText(/Provider rejected: no availability/i);
    expect(mockedGet).toHaveBeenCalledTimes(1);
  });

  it('flight detail shows trip siblings and a cascade cancel dialog (anchor of the trip)', async () => {
    const flight = flightBooking('CONFIRMED', { id: 'b1', tripId: 'trip-X', destination: 'NYC' });
    const hotel = flightBooking('CONFIRMED', { id: 'b2', tripId: 'trip-X', destination: 'NYC', resourceType: 'HOTEL' });

    mockedGet.mockResolvedValueOnce(flight);
    mockedListTrip.mockResolvedValue([flight, hotel]);

    const { findByText } = renderScreen();

    // Sibling row visible in the Trip card.
    await findByText(/HOTEL → NYC/i);
    // Cancel-button label reflects the trip-wide cascade count.
    await findByText('Cancel trip (2 bookings)');
  });

  it('hotel detail keeps a single-booking cancel even with sibling flight (no cascade)', async () => {
    // Route param is hardcoded to 'b1' in renderScreen — give the hotel that id so it
    // becomes the focused booking and the flight becomes the sibling.
    const hotel = flightBooking('CONFIRMED', { id: 'b1', tripId: 'trip-X', destination: 'NYC', resourceType: 'HOTEL' });
    const flight = flightBooking('CONFIRMED', { id: 'b2', tripId: 'trip-X', destination: 'NYC' });

    mockedGet.mockResolvedValueOnce(hotel);
    mockedListTrip.mockResolvedValue([hotel, flight]);

    const { findByText, queryByText } = renderScreen();

    // Sibling flight visible in the Trip card.
    await findByText(/FLIGHT → NYC/i);
    // Cancel button stays "Cancel booking" — hotel cancel doesn't cascade.
    await findByText('Cancel booking');
    expect(queryByText(/Cancel trip/i)).toBeNull();
  });

  it('cancels a CONFIRMED booking via the dialog and reflects the new state', async () => {
    mockedGet.mockResolvedValueOnce(
      flightBooking('CONFIRMED', { providerRef: 'DUFFEL-MOCK-A1B2C3D4' }),
    );
    mockedCancel.mockResolvedValueOnce(
      flightBooking('CANCELLED', { cancellationReason: 'Cancelled by user' }),
    );

    const { findByText, findAllByText, queryByText } = renderScreen();

    // Wait for the booking to load and the cancel button to appear.
    const cancelTrigger = await findByText('Cancel booking');
    fireEvent.press(cancelTrigger);

    // Dialog opens — confirm-action button shares the "Cancel booking" label,
    // so we tap the second occurrence (inside the dialog).
    await findByText(/Cancel this booking\?/i);
    const buttons = await findAllByText('Cancel booking');
    await act(async () => {
      fireEvent.press(buttons[buttons.length - 1]);
    });

    await waitFor(() => {
      expect(queryByText(/Cancelled by user/i)).not.toBeNull();
    });
    expect(mockedCancel).toHaveBeenCalledWith('b1');
  });
});
