import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import { SearchScreen } from '../screens/SearchScreen';
import { BookingFormScreen } from '../screens/BookingFormScreen';
import { BookingsScreen } from '../screens/BookingsScreen';
import { UpcomingBookingsScreen } from '../screens/UpcomingBookingsScreen';
import { BookingDetailScreen } from '../screens/BookingDetailScreen';
import { RegisterScreen } from '../screens/RegisterScreen';
import type {
  BookingsStackParamList,
  RootTabParamList,
  SearchStackParamList,
  UpcomingStackParamList,
} from './types';

const Tab = createBottomTabNavigator<RootTabParamList>();
const SearchStack = createNativeStackNavigator<SearchStackParamList>();
const BookingsStack = createNativeStackNavigator<BookingsStackParamList>();
const UpcomingStack = createNativeStackNavigator<UpcomingStackParamList>();

function SearchNavigator() {
  return (
    <SearchStack.Navigator>
      <SearchStack.Screen name="Search" component={SearchScreen} options={{ title: 'Search' }} />
      <SearchStack.Screen
        name="BookingForm"
        component={BookingFormScreen}
        options={{ title: 'Book Trip' }}
      />
      <SearchStack.Screen
        name="BookingDetail"
        component={BookingDetailScreen}
        options={{ title: 'Booking' }}
      />
    </SearchStack.Navigator>
  );
}

function BookingsNavigator() {
  return (
    <BookingsStack.Navigator>
      <BookingsStack.Screen
        name="Bookings"
        component={BookingsScreen}
        options={{ title: 'My Bookings' }}
      />
      <BookingsStack.Screen
        name="BookingDetail"
        component={BookingDetailScreen}
        options={{ title: 'Booking' }}
      />
    </BookingsStack.Navigator>
  );
}

function UpcomingNavigator() {
  return (
    <UpcomingStack.Navigator>
      <UpcomingStack.Screen
        name="Upcoming"
        component={UpcomingBookingsScreen}
        options={{ title: 'Upcoming' }}
      />
      <UpcomingStack.Screen
        name="BookingDetail"
        component={BookingDetailScreen}
        options={{ title: 'Booking' }}
      />
    </UpcomingStack.Navigator>
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
      <Tab.Screen name="BookingsTab" component={BookingsNavigator} options={{ title: 'My Bookings' }} />
      <Tab.Screen name="UpcomingTab" component={UpcomingNavigator} options={{ title: 'Upcoming' }} />
      <Tab.Screen
        name="RegisterTab"
        component={RegisterScreen}
        options={{ title: 'Register', headerShown: true }}
      />
    </Tab.Navigator>
  );
}
