import React, { useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { Button, Divider, Switch, Text, TextInput } from 'react-native-paper';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { createBooking, listBookings } from '../api/bookings';
import { apiErrorMessage } from '../api/errors';
import type { BookingResponse } from '../api/types';
import type { SearchStackParamList } from '../navigation/types';

interface OpenTripSummary {
  tripId: string;
  primaryDestination: string;
  bookingCount: number;
}

/**
 * Walks the employee's bookings, picks the most recently-created one whose status is
 * still PENDING or CONFIRMED, and reports its trip alongside the count of siblings.
 * Returns null if no open trip exists. The form uses this to default the new booking
 * into the user's current trip — that's how a flight + hotel get linked so a later
 * cancel cascades across both.
 */
function findMostRecentOpenTrip(bookings: BookingResponse[]): OpenTripSummary | null {
  const open = bookings.filter((b) => b.status === 'PENDING' || b.status === 'CONFIRMED');
  if (open.length === 0) return null;
  const mostRecent = [...open].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
  const siblings = open.filter((b) => b.tripId === mostRecent.tripId);
  return {
    tripId: mostRecent.tripId,
    primaryDestination: siblings[0].destination,
    bookingCount: siblings.length,
  };
}

type Props = NativeStackScreenProps<SearchStackParamList, 'BookingForm'>;

export function BookingFormScreen({ route, navigation }: Props) {
  const { result } = route.params;
  const [employeeId, setEmployeeId] = useState('');
  const [costCenterRef, setCostCenterRef] = useState('');
  const [travelerCount, setTravelerCount] = useState('1');
  const [tripPurpose, setTripPurpose] = useState('');
  const [loading, setLoading] = useState(false);

  // After the user has typed enough of an employee id, look up their open bookings to
  // detect an in-progress trip; we offer to attach the new booking to it so a later
  // cancel cascades across the whole trip.
  const [openTrip, setOpenTrip] = useState<OpenTripSummary | null>(null);
  const [addToExistingTrip, setAddToExistingTrip] = useState(true);

  useEffect(() => {
    const trimmed = employeeId.trim();
    if (trimmed.length < 5) {
      setOpenTrip(null);
      return;
    }
    let cancelled = false;
    listBookings(trimmed, 0, 100)
      .then((paged) => {
        if (cancelled) return;
        setOpenTrip(findMostRecentOpenTrip(paged.bookings));
      })
      .catch(() => {
        // Silent — the trip toggle is a polish; the form still works without it.
      });
    return () => {
      cancelled = true;
    };
  }, [employeeId]);

  async function handleBook() {
    if (!employeeId.trim() || !costCenterRef.trim()) {
      Alert.alert('Validation', 'Employee ID and Cost Center are required');
      return;
    }
    setLoading(true);
    try {
      const booking = await createBooking({
        employeeId: employeeId.trim(),
        resourceType: result.resourceType,
        destination: result.destination,
        departureDate: result.departureTime,
        // Hotels: search builds arrivalTime as check-out, so it's the correct
        // returnDate to send. Flights: arrivalTime is the outbound arrival, not
        // a return date — leave undefined so the booking is one-way.
        returnDate: result.resourceType === 'HOTEL' ? result.arrivalTime : undefined,
        travelerCount: parseInt(travelerCount, 10) || 1,
        costCenterRef: costCenterRef.trim(),
        tripPurpose: tripPurpose.trim() || undefined,
        // If the user kept "add to existing trip" toggled on, link this booking into
        // the open trip's tripId — otherwise the server generates a fresh one.
        tripId: addToExistingTrip && openTrip ? openTrip.tripId : undefined,
      });
      // Land the user in the BookingsTab's BookingDetail so the workflow stepper renders.
      // `initial: false` is essential — without it React Navigation jumps straight to
      // BookingDetail with an empty back stack, so the header's back arrow disappears.
      // Setting it to false enters BookingsStack via its initial route (Bookings) and
      // pushes BookingDetail on top, leaving the stack as [Bookings, BookingDetail].
      const parent = navigation.getParent();
      if (parent) {
        parent.navigate('BookingsTab', {
          screen: 'BookingDetail',
          initial: false,
          params: { id: booking.id },
        });
      }
      navigation.popToTop();
    } catch (e) {
      Alert.alert('Error', apiErrorMessage(e, 'Booking failed'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        Trip summary
      </Text>
      <Text variant="bodyMedium">
        {result.providerName} · {result.resourceType}
      </Text>
      <Text variant="bodyMedium">Destination: {result.destination}</Text>
      <Text variant="bodyMedium">
        {result.pricePerPerson} {result.currency} / person
      </Text>
      {result.resourceType === 'HOTEL' ? (
        <>
          {result.departureTime ? (
            <Text variant="bodySmall">
              Check-in: {new Date(result.departureTime).toLocaleDateString()}
            </Text>
          ) : null}
          {result.arrivalTime ? (
            <Text variant="bodySmall">
              Check-out: {new Date(result.arrivalTime).toLocaleDateString()}
            </Text>
          ) : null}
        </>
      ) : (
        result.departureTime ? (
          <Text variant="bodySmall">
            Departs: {new Date(result.departureTime).toLocaleString()}
          </Text>
        ) : null
      )}

      <Divider style={styles.divider} />

      <Text variant="titleMedium" style={styles.sectionTitle}>
        Your details
      </Text>
      <TextInput
        label="Employee ID"
        value={employeeId}
        onChangeText={setEmployeeId}
        mode="outlined"
        style={styles.input}
        placeholder="EMP1234"
        autoCapitalize="characters"
      />
      <TextInput
        label="Cost Center"
        value={costCenterRef}
        onChangeText={setCostCenterRef}
        mode="outlined"
        style={styles.input}
        placeholder="CC-456"
      />
      <TextInput
        label="Travelers"
        value={travelerCount}
        onChangeText={setTravelerCount}
        mode="outlined"
        style={styles.input}
        keyboardType="numeric"
      />
      <TextInput
        label="Trip purpose (optional)"
        value={tripPurpose}
        onChangeText={setTripPurpose}
        mode="outlined"
        style={styles.input}
        multiline
        numberOfLines={2}
      />

      {openTrip ? (
        <View style={styles.tripRow}>
          <Switch value={addToExistingTrip} onValueChange={setAddToExistingTrip} />
          <View style={styles.tripText}>
            <Text variant="bodyMedium" style={styles.tripLabel}>
              Add to existing trip
            </Text>
            <Text variant="bodySmall" style={styles.tripHint}>
              {openTrip.bookingCount} open booking{openTrip.bookingCount > 1 ? 's' : ''} to{' '}
              {openTrip.primaryDestination}. Cancellation will cascade across all bookings in the trip.
            </Text>
          </View>
        </View>
      ) : null}

      <Button mode="contained" onPress={handleBook} loading={loading} style={styles.button}>
        Confirm Booking
      </Button>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  content: { padding: 16 },
  sectionTitle: { marginBottom: 8, fontWeight: '600' },
  divider: { marginVertical: 16 },
  input: { marginBottom: 8 },
  button: { marginTop: 16 },
  tripRow: { flexDirection: 'row', alignItems: 'center', marginTop: 8 },
  tripText: { flex: 1, marginLeft: 12 },
  tripLabel: { fontWeight: '500' },
  tripHint: { color: '#616161', marginTop: 2 },
});
