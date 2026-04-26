import { apiClient } from './client';
import type { SearchParams, SearchResult } from './types';

export async function searchOptions(params: SearchParams): Promise<SearchResult[]> {
  const { data } = await apiClient.get<SearchResult[]>('/bookings/search', { params });
  return data;
}
