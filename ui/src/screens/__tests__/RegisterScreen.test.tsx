import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { PaperProvider } from 'react-native-paper';
import { RegisterScreen } from '../RegisterScreen';
import { registerEmployee } from '../../api/employees';

jest.mock('../../api/employees');
const mockedRegister = registerEmployee as jest.MockedFunction<typeof registerEmployee>;

function renderRegister() {
  return render(
    <PaperProvider>
      <RegisterScreen />
    </PaperProvider>,
  );
}

beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(Alert, 'alert').mockImplementation(() => {});
});

describe('RegisterScreen', () => {
  it('shows a validation alert when required fields are missing', () => {
    const { getByText } = renderRegister();
    fireEvent.press(getByText('Register Employee'));

    expect(Alert.alert).toHaveBeenCalledWith(
      'Validation',
      expect.stringContaining('required'),
    );
    expect(mockedRegister).not.toHaveBeenCalled();
  });

  it('submits a trimmed, lowercased payload on success', async () => {
    mockedRegister.mockResolvedValueOnce({
      id: '1',
      employeeId: 'EMP9876',
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@techquarter.com',
      department: 'Engineering',
      createdAt: '2026-01-01T00:00:00Z',
    });

    const { getByPlaceholderText, getByTestId, getByText } = renderRegister();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), '  EMP9876  ');
    fireEvent.changeText(getByTestId('firstName'), 'Alice');
    fireEvent.changeText(getByTestId('lastName'), 'Smith');
    fireEvent.changeText(getByTestId('email'), 'Alice@TechQuarter.com');
    fireEvent.changeText(getByTestId('department'), 'Engineering');
    fireEvent.press(getByText('Register Employee'));

    await waitFor(() => expect(mockedRegister).toHaveBeenCalledTimes(1));
    expect(mockedRegister).toHaveBeenCalledWith({
      employeeId: 'EMP9876',
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@techquarter.com',
      department: 'Engineering',
      costCentreDefault: undefined,
    });
  });

  it("surfaces the server's `description` field in the error alert (regression: violations[0].message was wrong)", async () => {
    mockedRegister.mockRejectedValueOnce({
      response: { data: { reasonCode: 'DUPLICATE_EMPLOYEE', description: 'employeeId already exists' } },
    });

    const { getByPlaceholderText, getByTestId, getByText } = renderRegister();
    fireEvent.changeText(getByPlaceholderText('EMP1234'), 'EMP9876');
    fireEvent.changeText(getByTestId('firstName'), 'Alice');
    fireEvent.changeText(getByTestId('lastName'), 'Smith');
    fireEvent.changeText(getByTestId('email'), 'alice@techquarter.com');
    fireEvent.changeText(getByTestId('department'), 'Engineering');
    fireEvent.press(getByText('Register Employee'));

    await waitFor(() =>
      expect(Alert.alert).toHaveBeenCalledWith('Error', 'employeeId already exists'),
    );
  });
});
