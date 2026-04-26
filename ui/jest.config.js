// babel-preset-expo inlines process.env.EXPO_PUBLIC_* at transform time, so the
// value has to be present BEFORE Jest spawns workers. Setting it here (which Node
// loads before any test code) makes the inlined value 'test-client-id' across the run.
process.env.EXPO_PUBLIC_COGNITO_CLIENT_ID ||= 'test-client-id';

module.exports = {
  preset: 'jest-expo',
  setupFiles: ['<rootDir>/jest.setup.ts'],
  testPathIgnorePatterns: ['/node_modules/', '/.expo/'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json'],
};
