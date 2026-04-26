import { apiClient } from './client';
import type { EmployeeResponse, RegisterEmployeeRequest } from './types';

export async function registerEmployee(
  request: RegisterEmployeeRequest,
): Promise<EmployeeResponse> {
  const { data } = await apiClient.post<EmployeeResponse>('/employees', request);
  return data;
}
