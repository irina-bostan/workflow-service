import { apiErrorMessage } from '../errors';

describe('apiErrorMessage', () => {
  it('returns the server description when present', () => {
    const err = { response: { data: { description: 'employeeId already exists' } } };
    expect(apiErrorMessage(err, 'fallback')).toBe('employeeId already exists');
  });

  it('falls back to details when description is missing', () => {
    const err = { response: { data: { details: 'constraint violation: employees_email_key' } } };
    expect(apiErrorMessage(err, 'fallback')).toBe('constraint violation: employees_email_key');
  });

  it('prefers description over details when both are present', () => {
    const err = {
      response: { data: { description: 'user-facing reason', details: 'tech detail' } },
    };
    expect(apiErrorMessage(err, 'fallback')).toBe('user-facing reason');
  });

  it("uses the Error's own message when there's no API response body (network error, timeout)", () => {
    const err = new Error('Network Error');
    expect(apiErrorMessage(err, 'fallback')).toBe('Network Error');
  });

  it('uses fallback for an empty response body', () => {
    const err = { response: { data: {} } };
    expect(apiErrorMessage(err, 'Default failure')).toBe('Default failure');
  });

  it('uses fallback for unknown / non-error inputs', () => {
    expect(apiErrorMessage(null, 'fallback')).toBe('fallback');
    expect(apiErrorMessage(undefined, 'fallback')).toBe('fallback');
    expect(apiErrorMessage('a string', 'fallback')).toBe('fallback');
  });
});
