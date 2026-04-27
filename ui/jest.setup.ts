/* Test environment setup — runs once per test file before the framework loads.
 * Use this for: native-module mocks, env vars used at module-load time.
 */

jest.mock('@react-native-community/datetimepicker', () => 'DateTimePicker');
// MaterialCommunityIcons is required by react-native-paper for the chevron in
// List.Accordion (and other glyphs). Without it, any test that renders an
// accordion / chip with an icon throws "node on an unmounted component".
jest.mock('@expo/vector-icons', () => ({
  Ionicons: 'Ionicons',
  MaterialCommunityIcons: 'MaterialCommunityIcons',
}));

// react-native-calendars relies on native-ish layout APIs; in tests we replace Agenda
// with a thin React component that renders every item inline so RNTL can find them by text.
jest.mock('react-native-calendars', () => {
  const React = require('react');
  const { View } = require('react-native');
  return {
    Agenda: ({ items, renderItem, renderEmptyData }: any) => {
      const days = Object.keys(items ?? {});
      if (days.length === 0) {
        return React.createElement(View, { testID: 'Agenda' }, renderEmptyData?.() ?? null);
      }
      const children = days.flatMap((day) =>
        (items[day] ?? []).map((item: any, i: number) =>
          React.createElement(
            View,
            { key: `${day}-${i}`, testID: `agenda-item-${day}` },
            renderItem(item, i === 0),
          ),
        ),
      );
      return React.createElement(View, { testID: 'Agenda' }, children);
    },
    Calendar: 'Calendar',
    CalendarList: 'CalendarList',
  };
});

// jest-expo auto-mocks expo-crypto and returns undefined for randomUUID; replace with
// a deterministic-but-unique v4-format generator so tests can assert key uniqueness.
jest.mock('expo-crypto', () => {
  let counter = 0;
  return {
    randomUUID: () => {
      counter += 1;
      const hex = counter.toString(16).padStart(12, '0');
      return `00000000-0000-4000-8000-${hex}`;
    },
  };
});

// React Native Testing Library 12.x auto-detects host component names by rendering a
// probe that includes Switch — under RN 0.76 + jest-expo this probe crashes. Pre-set
// the names to skip the probe entirely.
const rntlConfig = require('@testing-library/react-native/build/config');
if (typeof rntlConfig.configureInternal === 'function') {
  rntlConfig.configureInternal({
    hostComponentNames: {
      text: 'Text',
      textInput: 'TextInput',
      image: 'Image',
      switch: 'RCTSwitch',
      scrollView: 'RCTScrollView',
      modal: 'Modal',
    },
  });
}
