import type { SearchResult } from '../api/types';

export type SearchStackParamList = {
  Search: undefined;
  BookingForm: { result: SearchResult };
};

export type RootTabParamList = {
  SearchTab: undefined;
  BookingsTab: undefined;
  UpcomingTab: undefined;
  RegisterTab: undefined;
};
