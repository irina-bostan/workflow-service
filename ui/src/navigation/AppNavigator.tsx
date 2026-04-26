import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import { SearchScreen } from '../screens/SearchScreen';
import { BookingFormScreen } from '../screens/BookingFormScreen';
import { BookingsScreen } from '../screens/BookingsScreen';
import { UpcomingBookingsScreen } from '../screens/UpcomingBookingsScreen';
import { RegisterScreen } from '../screens/RegisterScreen';
import type { RootTabParamList, SearchStackParamList } from './types';

const Tab = createBottomTabNavigator<RootTabParamList>();
const SearchStack = createNativeStackNavigator<SearchStackParamList>();

function SearchNavigator() {
  return (
    <SearchStack.Navigator>
      <SearchStack.Screen name="Search" component={SearchScreen} options={{ title: 'Search' }} />
      <SearchStack.Screen
        name="BookingForm"
        component={BookingFormScreen}
        options={{ title: 'Book Trip' }}
      />
    </SearchStack.Navigator>
  );
}

const TAB_ICONS: Record<keyof RootTabParamList, keyof typeof Ionicons.glyphMap> = {
  SearchTab: 'search',
  BookingsTab: 'list',
  UpcomingTab: 'calendar',
  RegisterTab: 'person-add',
};

export function AppNavigator() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ color, size }) => (
          <Ionicons name={TAB_ICONS[route.name]} size={size} color={color} />
        ),
        tabBarActiveTintColor: '#1565C0',
        tabBarInactiveTintColor: '#9E9E9E',
        headerShown: false,
      })}
    >
      <Tab.Screen name="SearchTab" component={SearchNavigator} options={{ title: 'Search' }} />
      <Tab.Screen
        name="BookingsTab"
        component={BookingsScreen}
        options={{ title: 'My Bookings', headerShown: true }}
      />
      <Tab.Screen
        name="UpcomingTab"
        component={UpcomingBookingsScreen}
        options={{ title: 'Upcoming', headerShown: true }}
      />
      <Tab.Screen
        name="RegisterTab"
        component={RegisterScreen}
        options={{ title: 'Register', headerShown: true }}
      />
    </Tab.Navigator>
  );
}
