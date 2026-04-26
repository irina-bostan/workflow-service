import { apiClient } from '../client';
import { createBooking, listBookings } from '../bookings';
import type { CreateBookingRequest } from '../types';

jest.mock('../client', () => ({
  apiClient: {
    post: jest.fn(),
    get: jest.fn(),
  },
}));

const mockedClient = apiClient as jest.Mocked<typeof apiClient>;

beforeEach(() => {
  jest.resetAllMocks();
});

describe('createBooking', () => {
  const request: CreateBookingRequest = {
    employeeId: 'EMP9876',
    resourceType: 'FLIGHT',
    destination: 'NYC',
    departureDate: '2027-11-05T08:00:00Z',
    travelerCount: 1,
    costCenterRef: 'CC-456',
  };

  it('POSTs to /bookings with a UUID-v4 Idempotency-Key header', async () => {
    mockedClient.post.mockResolvedValueOnce({ data: { id: 'b-1' } });

    await createBooking(request);

    expect(mockedClient.post).toHaveBeenCalledTimes(1);
    const [path, body, config] = mockedClient.post.mock.calls[0];
    expect(path).toBe('/bookings');
    expect(body).toEqual(request);

    const key = (config as { headers: Record<string, string> }).headers['Idempotency-Key'];
    // RFC 4122 v4: 8-4-4-4-12 hex with version nibble = 4 and variant bits 10xx in pos 19.
    expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it('uses a fresh idempotency key on every call', async () => {
    mockedClient.post.mockResolvedValue({ data: { id: 'b' } });

    await createBooking(request);
    await createBooking(request);

    const keyA = (mockedClient.post.mock.calls[0][2] as { headers: Record<string, string> })
      .headers['Idempotency-Key'];
    const keyB = (mockedClient.post.mock.calls[1][2] as { headers: Record<string, string> })
      .headers['Idempotency-Key'];
    expect(keyA).not.toBe(keyB);
  });
});

describe('listBookings', () => {
  it('passes employeeId, page, size as query params with sane defaults', async () => {
    mockedClient.get.mockResolvedValueOnce({
      data: { page: 0, size: 20, totalElements: 0, totalPages: 0, bookings: [] },
    });

    await listBookings('EMP9876');

    expect(mockedClient.get).toHaveBeenCalledWith('/bookings', {
      params: { employeeId: 'EMP9876', page: 0, size: 20 },
    });
  });

  it('honors caller-supplied page and size', async () => {
    mockedClient.get.mockResolvedValueOnce({
      data: { page: 2, size: 50, totalElements: 0, totalPages: 0, bookings: [] },
    });

    await listBookings('EMP9876', 2, 50);

    expect(mockedClient.get).toHaveBeenCalledWith('/bookings', {
      params: { employeeId: 'EMP9876', page: 2, size: 50 },
    });
  });
});
