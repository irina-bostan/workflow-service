import type { SearchResult } from '../api/types';

export type SearchStackParamList = {
  Search: undefined;
  BookingForm: { result: SearchResult };
  BookingDetail: { id: string };
};

export type BookingsStackParamList = {
  Bookings: undefined;
  BookingDetail: { id: string };
};

export type UpcomingStackParamList = {
  Upcoming: undefined;
  BookingDetail: { id: string };
};

export type RootTabParamList = {
  SearchTab: undefined;
  BookingsTab: undefined;
  UpcomingTab: undefined;
  RegisterTab: undefined;
};
