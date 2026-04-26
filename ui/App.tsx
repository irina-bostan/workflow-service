import React from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { NavigationContainer } from '@react-navigation/native';
import { MD3LightTheme, PaperProvider } from 'react-native-paper';
import { StatusBar } from 'expo-status-bar';
import { AppNavigator } from './src/navigation/AppNavigator';

const theme = {
  ...MD3LightTheme,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#1565C0',
    secondary: '#0288D1',
  },
};

// GestureHandlerRootView at the top is required by React Navigation v6+ and is what
// makes scroll/touch gesture routing work consistently for FlatList, ScrollView, and
// the calendar Agenda. Without it, scroll behavior is broken app-wide on RN 0.76.
export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <PaperProvider theme={theme}>
        <NavigationContainer>
          <AppNavigator />
          <StatusBar style="auto" />
        </NavigationContainer>
      </PaperProvider>
    </GestureHandlerRootView>
  );
}
