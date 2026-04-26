import React, { useState } from 'react';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { Button, Divider, Text, TextInput } from 'react-native-paper';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { createBooking } from '../api/bookings';
import { apiErrorMessage } from '../api/errors';
import type { SearchStackParamList } from '../navigation/types';

type Props = NativeStackScreenProps<SearchStackParamList, 'BookingForm'>;

export function BookingFormScreen({ route, navigation }: Props) {
  const { result } = route.params;
  const [employeeId, setEmployeeId] = useState('');
  const [costCenterRef, setCostCenterRef] = useState('');
  const [travelerCount, setTravelerCount] = useState('1');
  const [tripPurpose, setTripPurpose] = useState('');
  const [loading, setLoading] = useState(false);

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
      });
      Alert.alert('Booking confirmed', `Booking ID: ${booking.id}`, [
        { text: 'Done', onPress: () => navigation.popToTop() },
      ]);
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
  button: { marginTop: 8 },
});
