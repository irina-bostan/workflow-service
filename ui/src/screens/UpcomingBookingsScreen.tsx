import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { Button, Text, TextInput } from 'react-native-paper';
import { Agenda, type AgendaSchedule } from 'react-native-calendars';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { listBookings } from '../api/bookings';
import { apiErrorMessage } from '../api/errors';
import type { BookingResponse } from '../api/types';
import { BookingCard } from '../components/BookingCard';
import { usePollWhilePending } from '../hooks/usePollWhilePending';
import type { UpcomingStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<UpcomingStackParamList, 'Upcoming'>;

// AgendaEntry requires {name, height, day}; we tack on `booking` and double-cast through
// `unknown` because react-native-calendars' types don't model the Agenda's "extra fields"
// extension that renderItem actually receives back.
interface AgendaItem {
  name: string;
  height: number;
  day: string;
  booking: BookingResponse;
}

const longDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, {
    weekday: 'short',
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });

const todayIso = () => new Date().toISOString().slice(0, 10);

function toAgendaItems(bookings: BookingResponse[]): AgendaSchedule {
  // Agenda is keyed by YYYY-MM-DD; one booking → one entry on its departureDate.
  const items: AgendaSchedule = {};
  bookings.forEach((b) => {
    const day = b.departureDate.slice(0, 10);
    if (!items[day]) items[day] = [];
    items[day].push({ name: b.id, booking: b, height: 0, day } as unknown as AgendaSchedule[string][number]);
  });
  return items;
}

export function UpcomingBookingsScreen({ navigation }: Props) {
  const [employeeId, setEmployeeId] = useState('');
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const fetchUpcoming = useCallback(
    async (isPoll = false) => {
      if (!employeeId.trim()) {
        if (!isPoll) Alert.alert('Validation', 'Enter an employee ID');
        return;
      }
      if (!isPoll) setLoading(true);
      try {
        const paged = await listBookings(employeeId.trim(), 0, 100);
        const now = Date.now();
        const upcoming = paged.bookings
          .filter((b) => new Date(b.departureDate).getTime() > now && b.status !== 'CANCELLED')
          .sort((a, b) => new Date(a.departureDate).getTime() - new Date(b.departureDate).getTime());
        setBookings(upcoming);
        setSearched(true);
      } catch (e) {
        if (!isPoll) Alert.alert('Error', apiErrorMessage(e, 'Failed to load bookings'));
      } finally {
        if (!isPoll) setLoading(false);
      }
    },
    [employeeId],
  );

  usePollWhilePending(bookings, () => void fetchUpcoming(true));

  // Refetch on focus so post-cancel state changes propagate when the user navigates
  // back from BookingDetail. Same pattern as BookingsScreen.
  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      if (employeeId.trim() && searched) {
        void fetchUpcoming(true);
      }
    });
    return unsubscribe;
  }, [navigation, employeeId, searched, fetchUpcoming]);

  const items = useMemo(() => toAgendaItems(bookings), [bookings]);
  // Open the agenda on the first upcoming trip's day when present, else today.
  const selectedDay = bookings[0]?.departureDate.slice(0, 10) ?? todayIso();

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <TextInput
          label="Employee ID"
          value={employeeId}
          onChangeText={setEmployeeId}
          mode="outlined"
          style={styles.idInput}
          placeholder="EMP1234"
          autoCapitalize="characters"
        />
        <Button mode="contained" onPress={() => fetchUpcoming()} loading={loading} style={styles.loadBtn}>
          Load
        </Button>
      </View>

      {searched ? (
        <Agenda
          items={items}
          selected={selectedDay}
          minDate={todayIso()}
          renderItem={(item: AgendaItem) => (
            <View style={styles.item}>
              <BookingCard
                booking={item.booking}
                formatDate={longDate}
                onPress={() => navigation.navigate('BookingDetail', { id: item.booking.id })}
              />
            </View>
          )}
          renderEmptyData={() => (
            <View style={styles.empty}>
              <Text style={styles.emptyText}>No bookings on this day.</Text>
            </View>
          )}
          theme={{
            agendaKnobColor: '#1565C0',
            selectedDayBackgroundColor: '#1565C0',
            todayTextColor: '#1565C0',
            dotColor: '#1565C0',
          }}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  row: { flexDirection: 'row', alignItems: 'center', marginHorizontal: 16, marginVertical: 12 },
  idInput: { flex: 1, marginRight: 8 },
  loadBtn: { marginTop: 6 },
  item: { paddingHorizontal: 16, paddingVertical: 8 },
  empty: { padding: 16 },
  emptyText: { textAlign: 'center', color: '#9E9E9E' },
});
