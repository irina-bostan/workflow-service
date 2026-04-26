import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';
import { UpcomingBookingsScreen } from '../UpcomingBookingsScreen';
import { listBookings } from '../../api/bookings';
import type { BookingResponse, PagedBookingResponse } from '../../api/types';

jest.mock('../../api/bookings');
const mockedList = listBookings as jest.MockedFunction<typeof listBookings>;

function renderScreen() {
  return render(
    <PaperProvider>
      <UpcomingBookingsScreen />
    </PaperProvider>,
  );
}

function booking(
  id: string,
  destination: string,
  departureDate: string,
  status: BookingResponse['status'] = 'PENDING',
): BookingResponse {
  return {
    id,
    employeeId: 'EMP9876',
    resourceType: 'FLIGHT',
    destination,
    departureDate,
    travelerCount: 1,
    costCenterRef: 'CC-456',
    status,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function paged(items: BookingResponse[]): PagedBookingResponse {
  return { page: 0, size: 100, totalElements: items.length, totalPages: 1, bookings: items };
}

beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Alert, 'alert').mockImplementation(() => {});
});

describe('UpcomingBookingsScreen', () => {
  it('blocks the load and alerts when employee id is empty', () => {
    const { getByText } = renderScreen();
    fireEvent.press(getByText('Load'));

    expect(Alert.alert).toHaveBeenCalledWith('Validation', expect.stringContaining('employee'));
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('renders upcoming bookings on the agenda, keyed by departureDate', async () => {
    const future = new Date(Date.now() + 7 * 86400_000).toISOString();
    mockedList.mockResolvedValueOnce(paged([booking('b1', 'NYC', future, 'PENDING')]));

    const { getByPlaceholderText, getByText, findByText, findByTestId } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByTestId('Agenda');
    await findByText('FLIGHT → NYC');
    // Status chip survives the agenda wrapping
    await findByText('PENDING');
  });

  it('filters out CANCELLED and past bookings', async () => {
    const future = new Date(Date.now() + 86400_000).toISOString();
    const past = new Date(Date.now() - 86400_000).toISOString();
    mockedList.mockResolvedValueOnce(
      paged([
        booking('b-past', 'LON', past, 'PENDING'),
        booking('b-cancel', 'PAR', future, 'CANCELLED'),
        booking('b-good', 'NYC', future, 'PENDING'),
      ]),
    );

    const { getByPlaceholderText, getByText, findByText, queryByText } = renderScreen();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.press(getByText('Load'));

    await findByText('FLIGHT → NYC');
    await waitFor(() => {
      expect(queryByText('FLIGHT → LON')).toBeNull(); // past
      expect(queryByText('FLIGHT → PAR')).toBeNull(); // cancelled
    });
  });
});
