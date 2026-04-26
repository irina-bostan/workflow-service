import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';
import { SearchScreen } from '../SearchScreen';
import { searchOptions } from '../../api/search';
import type { SearchResult } from '../../api/types';

jest.mock('../../api/search');
const mockedSearch = searchOptions as jest.MockedFunction<typeof searchOptions>;

function renderSearch() {
  const navigation = { navigate: jest.fn() } as any;
  const route = { params: undefined } as any;
  const utils = render(
    <PaperProvider>
      <SearchScreen navigation={navigation} route={route} />
    </PaperProvider>,
  );
  return { ...utils, navigation };
}

const flightResult: SearchResult = {
  providerId: 'BA-001',
  resourceType: 'FLIGHT',
  origin: 'LHR',
  destination: 'NYC',
  departureTime: '2027-11-05T08:00:00Z',
  arrivalTime: '2027-11-05T11:00:00Z',
  availableSeats: 12,
  pricePerPerson: 450,
  currency: 'GBP',
  providerName: 'British Airways',
};

beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Alert, 'alert').mockImplementation(() => {});
});

describe('SearchScreen', () => {
  it('blocks the search and alerts when destination is empty', () => {
    const { getByText } = renderSearch();
    fireEvent.press(getByText('Search'));

    expect(Alert.alert).toHaveBeenCalledWith('Validation', expect.stringContaining('Destination'));
    expect(mockedSearch).not.toHaveBeenCalled();
  });

  it('renders a result card after a successful search', async () => {
    mockedSearch.mockResolvedValueOnce([flightResult]);

    const { getByPlaceholderText, getByText, findByText } = renderSearch();
    fireEvent.changeText(getByPlaceholderText('e.g. NYC'), 'NYC');
    fireEvent.press(getByText('Search'));

    await findByText('British Airways — NYC');
    expect(mockedSearch).toHaveBeenCalledWith(
      expect.objectContaining({ resourceType: 'FLIGHT', destination: 'NYC' }),
    );
  });

  it('shows the empty-state message after a search returning no results', async () => {
    mockedSearch.mockResolvedValueOnce([]);

    const { getByPlaceholderText, getByText, findByText } = renderSearch();
    fireEvent.changeText(getByPlaceholderText('e.g. NYC'), 'NOWHERE');
    fireEvent.press(getByText('Search'));

    await findByText(/No results found for "NOWHERE"/);
  });
});
