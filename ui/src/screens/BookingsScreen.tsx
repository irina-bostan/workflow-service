import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, FlatList, StyleSheet, View } from 'react-native';
import { Button, Card, List, Text, TextInput } from 'react-native-paper';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { listBookings } from '../api/bookings';
import { apiErrorMessage } from '../api/errors';
import type { BookingResponse } from '../api/types';
import { BookingCard } from '../components/BookingCard';
import { usePollWhilePending } from '../hooks/usePollWhilePending';
import type { BookingsStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<BookingsStackParamList, 'Bookings'>;

interface TripGroup {
  tripId: string;
  bookings: BookingResponse[];
  earliestDeparture: number;
}

/**
 * Group bookings by tripId, then bucket each trip into one of three sections:
 *
 *   upcoming  — at least one booking is still PENDING/CONFIRMED with a future departure
 *               sorted by earliest departure ASC (nearest trip first)
 *   past      — all bookings have past departures, but at least one is not CANCELLED
 *               (i.e. trips that were taken). Sorted by earliest departure DESC.
 *   cancelled — every booking in the trip is CANCELLED, regardless of date.
 *               Sorted by earliest departure DESC.
 *
 * A trip is never split across sections — flight + hotel always travel together.
 */
export function classifyTrips(bookings: BookingResponse[]): {
  upcoming: TripGroup[];
  past: TripGroup[];
  cancelled: TripGroup[];
} {
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const todayMs = startOfToday.getTime();

  const groups = new Map<string, BookingResponse[]>();
  for (const b of bookings) {
    if (!groups.has(b.tripId)) groups.set(b.tripId, []);
    groups.get(b.tripId)!.push(b);
  }

  const upcoming: TripGroup[] = [];
  const past: TripGroup[] = [];
  const cancelled: TripGroup[] = [];
  for (const [tripId, items] of groups) {
    const earliest = Math.min(...items.map((b) => new Date(b.departureDate).getTime()));
    const trip: TripGroup = { tripId, bookings: items, earliestDeparture: earliest };

    const allCancelled = items.every((b) => b.status === 'CANCELLED');
    if (allCancelled) {
      cancelled.push(trip);
      continue;
    }
    const anyFuture = items.some((b) => new Date(b.departureDate).getTime() >= todayMs);
    (anyFuture ? upcoming : past).push(trip);
  }

  const byDepartureAsc = (a: TripGroup, b: TripGroup) => a.earliestDeparture - b.earliestDeparture;
  const byDepartureDesc = (a: TripGroup, b: TripGroup) => b.earliestDeparture - a.earliestDeparture;
  return {
    upcoming: upcoming.sort(byDepartureAsc),
    past: past.sort(byDepartureDesc),
    cancelled: cancelled.sort(byDepartureDesc),
  };
}

/**
 * Renders one trip in the bookings list. A solo trip (one booking) renders as a single
 * BookingCard so the list looks the same as before for that case. Multi-booking trips
 * render a wrapping Card with a header counting bookings, so the visual binding is clear
 * — and matches the cascade-cancel behaviour on the detail screen.
 */
function TripGroupItem({
  trip,
  onBookingPress,
}: {
  trip: TripGroup;
  onBookingPress: (id: string) => void;
}) {
  if (trip.bookings.length === 1) {
    const only = trip.bookings[0];
    return <BookingCard booking={only} onPress={() => onBookingPress(only.id)} />;
  }
  // Use any booking for the title destination — they belong to the same trip.
  const primary = trip.bookings[0];
  return (
    <Card style={styles.tripCard}>
      <Card.Title
        title={`Trip to ${primary.destination}`}
        subtitle={`${trip.bookings.length} bookings · trip ${trip.tripId.slice(0, 8)}…`}
      />
      <Card.Content style={styles.tripContent}>
        {trip.bookings.map((b) => (
          <BookingCard key={b.id} booking={b} onPress={() => onBookingPress(b.id)} />
        ))}
      </Card.Content>
    </Card>
  );
}

export function BookingsScreen({ navigation }: Props) {
  const [employeeId, setEmployeeId] = useState('');
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [searched, setSearched] = useState(false);

  const fetchBookings = useCallback(
    async (p = 0, isPoll = false) => {
      if (!employeeId.trim()) {
        if (!isPoll) Alert.alert('Validation', 'Enter an employee ID');
        return;
      }
      if (!isPoll) setLoading(true);
      try {
        const paged = await listBookings(employeeId.trim(), p);
        setBookings((prev) => (p === 0 ? paged.bookings : [...prev, ...paged.bookings]));
        setTotalPages(paged.totalPages);
        setPage(p);
        setSearched(true);
      } catch (e) {
        if (!isPoll) Alert.alert('Error', apiErrorMessage(e, 'Failed to load bookings'));
      } finally {
        if (!isPoll) setLoading(false);
      }
    },
    [employeeId],
  );

  // Live-refresh page 0 while any booking is PENDING. Polling stops automatically
  // when every row settles into CONFIRMED/CANCELLED.
  usePollWhilePending(bookings, () => void fetchBookings(0, true));

  // Refetch when the screen regains focus — covers "user drills into BookingDetail,
  // cancels, then taps back". `searched` gate ensures we don't fire on the very first
  // mount before the user has entered an employee ID.
  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      if (employeeId.trim() && searched) {
        void fetchBookings(0, true);
      }
    });
    return unsubscribe;
  }, [navigation, employeeId, searched, fetchBookings]);

  const [pastExpanded, setPastExpanded] = useState(false);
  const [cancelledExpanded, setCancelledExpanded] = useState(false);

  const { upcoming, past, cancelled } = useMemo(() => classifyTrips(bookings), [bookings]);

  const navigateToDetail = (id: string) => navigation.navigate('BookingDetail', { id });

  return (
    <View style={styles.container}>
      <View style={styles.formArea}>
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
          <Button
            mode="contained"
            onPress={() => fetchBookings(0)}
            loading={loading}
            style={styles.loadBtn}
          >
            Load
          </Button>
        </View>
      </View>

      <View style={styles.listArea}>
        <FlatList
          data={upcoming}
          keyExtractor={(item) => item.tripId}
          renderItem={({ item }) => (
            <TripGroupItem trip={item} onBookingPress={navigateToDetail} />
          )}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          nestedScrollEnabled
          ListEmptyComponent={
            searched && !loading && past.length === 0 && cancelled.length === 0 ? (
              <Text style={styles.empty}>No bookings found for "{employeeId}".</Text>
            ) : null
          }
          ListFooterComponent={
            <>
              {page + 1 < totalPages ? (
                <Button onPress={() => fetchBookings(page + 1)} loading={loading}>
                  Load more
                </Button>
              ) : null}
              {past.length > 0 ? (
                <List.Accordion
                  title={`Past trips (${past.length})`}
                  expanded={pastExpanded}
                  onPress={() => setPastExpanded(!pastExpanded)}
                  style={styles.bottomAccordion}
                  titleStyle={styles.accordionTitle}
                >
                  {past.map((trip) => (
                    <TripGroupItem
                      key={trip.tripId}
                      trip={trip}
                      onBookingPress={navigateToDetail}
                    />
                  ))}
                </List.Accordion>
              ) : null}
              {cancelled.length > 0 ? (
                <List.Accordion
                  title={`Cancelled trips (${cancelled.length})`}
                  expanded={cancelledExpanded}
                  onPress={() => setCancelledExpanded(!cancelledExpanded)}
                  style={styles.bottomAccordion}
                  titleStyle={[styles.accordionTitle, styles.cancelledTitle]}
                >
                  {cancelled.map((trip) => (
                    <TripGroupItem
                      key={trip.tripId}
                      trip={trip}
                      onBookingPress={navigateToDetail}
                    />
                  ))}
                </List.Accordion>
              ) : null}
            </>
          }
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  formArea: { paddingHorizontal: 16, paddingTop: 16 },
  row: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  idInput: { flex: 1, marginRight: 8 },
  loadBtn: { marginTop: 6 },
  // The FlatList must live inside a flex-1 View so it has a bounded height to scroll within.
  listArea: { flex: 1 },
  listContent: { paddingHorizontal: 16, paddingBottom: 24 },
  empty: { textAlign: 'center', marginTop: 32, color: '#9E9E9E' },
  // The Accordion comes from react-native-paper; cancel its default left padding
  // so child BookingCards align with the upcoming list above.
  bottomAccordion: { backgroundColor: 'transparent', paddingLeft: 0, marginTop: 12 },
  accordionTitle: { fontWeight: '500' },
  // Cancelled-trips accordion title — slightly muted red so the section reads as
  // "things that aren't happening" without screaming.
  cancelledTitle: { color: '#B71C1C' },
  tripCard: { marginBottom: 8, backgroundColor: '#ECEFF1' },
  // Removes Card.Content's default 16-px padding so nested BookingCards align with the
  // top-level (solo-trip) cards above them.
  tripContent: { paddingHorizontal: 8, paddingBottom: 8 },
});
