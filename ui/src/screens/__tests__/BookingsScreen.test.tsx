import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';
import { BookingsScreen } from '../BookingsScreen';
import { listBookings } from '../../api/bookings';
import type { BookingResponse, PagedBookingResponse } from '../../api/types';

jest.mock('../../api/bookings');
const mockedList = listBookings as jest.MockedFunction<typeof listBookings>;

function renderScreen() {
  return render(
    <PaperProvider>
      <BookingsScreen />
    </PaperProvider>,
  );
}

function booking(
  id: string,
  destination: string,
  status: BookingResponse['status'] = 'PENDING',
): BookingResponse {
  return {
    id,
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
