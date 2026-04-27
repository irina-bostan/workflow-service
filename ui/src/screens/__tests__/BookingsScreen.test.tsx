import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';
import { BookingsScreen, classifyTrips } from '../BookingsScreen';
import { listBookings } from '../../api/bookings';
import type { BookingResponse, PagedBookingResponse } from '../../api/types';

jest.mock('../../api/bookings');
const mockedList = listBookings as jest.MockedFunction<typeof listBookings>;

function renderScreen() {
  // addListener stub returns a no-op unsubscribe — matches React Navigation's contract
  // for `navigation.addListener('focus', cb)` used by BookingsScreen to refetch on focus.
  const navigation = { navigate: jest.fn(), addListener: jest.fn(() => () => {}) } as any;
  const route = { params: undefined } as any;
  const utils = render(
    <PaperProvider>
      <BookingsScreen navigation={navigation} route={route} />
    </PaperProvider>,
  );
  return { ...utils, navigation };
}

function booking(
  id: string,
  destination: string,
  status: BookingResponse['status'] = 'PENDING',
): BookingResponse {
  return {
    id,
    tripId: 'trip-' + id,
    employeeId: 'EMP9876',
    resourceType: 'FLIGHT',
    destination,
    departureDate: '2027-11-05T08:00:00Z',
    travelerCount: 1,
    costCenterRef: 'CC-456',
    status,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function page(p: number, totalPages: number, items: BookingResponse[]): PagedBookingResponse {
  return { page: p, size: 20, totalElements: items.length * totalPages, totalPages, bookings: items };
}

beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Alert, 'alert').mockImplementation(() => {});
});

describe('BookingsScreen', () => {
  it('blocks the load and alerts when employee id is empty', () => {
    const { getByText } = renderScreen();
    fireEvent.press(getByText('Load'));

    expect(Alert.alert).toHaveBeenCalledWith('Validation', expect.stringContaining('employee'));
    expect(mockedList).not.toHaveBeenCalled();
  });

  // Pure-function tests for classifyTrips. Rendering the accordions in jest needs
  // MaterialCommunityIcons mocked at a level that's too invasive for the test infra,
  // so we exercise the bucketing + sorting logic directly here.
  it('classifyTrips: separates all-CANCELLED trips from upcoming + sorts upcoming asc', () => {
    const upcomingNear = booking('a', 'NYC', 'CONFIRMED');
    upcomingNear.tripId = 't-near';
    upcomingNear.departureDate = '2027-06-01T08:00:00Z';
    const upcomingFar = booking('b', 'TYO', 'CONFIRMED');
    upcomingFar.tripId = 't-far';
    upcomingFar.departureDate = '2028-03-15T08:00:00Z';
    const cancelled = booking('c', 'PAR', 'CANCELLED');
    cancelled.tripId = 't-cx';
    cancelled.departureDate = '2027-12-15T08:00:00Z';

    const result = classifyTrips([upcomingFar, cancelled, upcomingNear]);

    expect(result.upcoming.map((t) => t.tripId)).toEqual(['t-near', 't-far']);
    expect(result.cancelled.map((t) => t.tripId)).toEqual(['t-cx']);
    expect(result.past).toEqual([]);
  });

  it('classifyTrips: trip with one PENDING + one CANCELLED stays in upcoming, not cancelled', () => {
    // Cascade rule from BookingService.cancelByUser: a HOTEL cancel doesn't cascade to
    // its sibling FLIGHT. The mixed-status trip should stay visible at the top.
    const flight = booking('a', 'NYC', 'PENDING');
    flight.tripId = 't-mixed';
    flight.departureDate = '2027-06-01T08:00:00Z';
    const hotel = booking('b', 'NYC', 'CANCELLED');
    hotel.tripId = 't-mixed';
    hotel.departureDate = '2027-06-01T08:00:00Z';

    const result = classifyTrips([flight, hotel]);

    expect(result.upcoming.map((t) => t.tripId)).toEqual(['t-mixed']);
    expect(result.cancelled).toEqual([]);
  });

  it('refetches the bookings list when the screen regains focus', async () => {
    mockedList.mockResolvedValue(page(0, 1, [booking('b1', 'NYC', 'CONFIRMED')]));

    const { getByPlaceholderText, getByText, findByText, navigation } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('FLIGHT → NYC');
    expect(mockedList).toHaveBeenCalledTimes(1);

    // Simulate the user navigating back from BookingDetail — invoke the most-recently-
    // registered focus listener (useEffect re-binds on each state change, the older ones
    // are unsubscribed by the cleanup, only the latest is "live" in the real navigator).
    const focusCalls = (navigation.addListener as jest.Mock).mock.calls.filter(
      ([event]) => event === 'focus',
    );
    expect(focusCalls.length).toBeGreaterThanOrEqual(1);
    const focusCallback = focusCalls[focusCalls.length - 1][1] as () => void;
    focusCallback();

    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
  });

  it('groups multiple bookings sharing a tripId under one Trip card', async () => {
    // Two bookings in the same trip — should render under a wrapping "Trip to NYC" card,
    // counted as 2 bookings.
    const flight = booking('b1', 'NYC');
    flight.tripId = 'trip-shared';
    flight.resourceType = 'FLIGHT';
    const hotel = booking('b2', 'NYC');
    hotel.tripId = 'trip-shared';
    hotel.resourceType = 'HOTEL';
    mockedList.mockResolvedValueOnce(page(0, 1, [flight, hotel]));

    const { getByPlaceholderText, getByText, findByText, queryAllByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('Trip to NYC');
    await findByText(/2 bookings · trip trip-sha…/i);
    // Both bookings still rendered as inner cards.
    expect(queryAllByText(/FLIGHT → NYC/i)).toHaveLength(1);
    expect(queryAllByText(/HOTEL → NYC/i)).toHaveLength(1);
  });

  it('renders the first page of bookings', async () => {
    mockedList.mockResolvedValueOnce(page(0, 1, [booking('b1', 'NYC')]));

    const { getByPlaceholderText, getByText, findByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('FLIGHT → NYC');
  });

  it('renders the booking status as a labelled chip (regression: empty square when status was undefined)', async () => {
    mockedList.mockResolvedValueOnce(page(0, 1, [booking('b1', 'NYC', 'PENDING')]));

    const { getByPlaceholderText, getByText, findByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('FLIGHT → NYC');
    await findByText('PENDING');
  });

  it('renders CONFIRMED status correctly (regression: stale backend returned no status → grey square)', async () => {
    mockedList.mockResolvedValueOnce(page(0, 1, [booking('b1', 'NYC', 'CONFIRMED')]));

    const { getByPlaceholderText, getByText, findByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('FLIGHT → NYC');
    await findByText('CONFIRMED');
  });

  it('hides the status chip entirely when the field is missing (rather than rendering an empty grey square)', async () => {
    const noStatus = { ...booking('b1', 'NYC') } as { status?: 'PENDING' | 'CONFIRMED' | 'CANCELLED' };
    delete noStatus.status;
    mockedList.mockResolvedValueOnce(page(0, 1, [noStatus as any]));

    const { getByPlaceholderText, getByText, findByText, queryByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('FLIGHT → NYC');
    expect(queryByText('PENDING')).toBeNull();
    expect(queryByText('CONFIRMED')).toBeNull();
    expect(queryByText('CANCELLED')).toBeNull();
  });

  it('appends — does not replace — when "Load more" fires (regression: stale-closure pagination)', async () => {
    mockedList
      .mockResolvedValueOnce(page(0, 2, [booking('b1', 'NYC'), booking('b2', 'PAR')]))
      .mockResolvedValueOnce(page(1, 2, [booking('b3', 'TYO')]));

    const { getByPlaceholderText, getByText, findByText, queryByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    // First page rendered.
    await findByText('FLIGHT → NYC');
    await findByText('FLIGHT → PAR');

    // Trigger "Load more" — page 1 should APPEND, not replace.
    fireEvent.press(getByText('Load more'));
    await findByText('FLIGHT → TYO');

    // Critical regression: the first page must still be visible. The pre-fix code
    // captured `bookings` in a closure and could replace with [] under timing edge cases.
    expect(queryByText('FLIGHT → NYC')).not.toBeNull();
    expect(queryByText('FLIGHT → PAR')).not.toBeNull();
    expect(mockedList).toHaveBeenNthCalledWith(2, 'EMP9876', 1);
  });
});
