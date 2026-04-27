import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, View } from 'react-native';
import { Button, Card, Dialog, Divider, Portal, Text } from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';
import type { RouteProp } from '@react-navigation/native';
import { BookingCard } from '../components/BookingCard';
import { AppointmentsSection } from '../components/AppointmentsSection';
import { useBookingDetail } from '../hooks/useBookingDetail';
import { cancelBooking, listTripBookings } from '../api/bookings';
import { apiErrorMessage } from '../api/errors';
import type { BookingResponse, BookingStatus } from '../api/types';

type DetailRoute = RouteProp<{ BookingDetail: { id: string } }, 'BookingDetail'>;

interface Props {
  route: DetailRoute;
}

type StepState = 'done' | 'active' | 'pending' | 'failed';

interface Step {
  label: string;
  state: StepState;
  detail?: string;
}

function buildSteps(booking: BookingResponse | null): Step[] {
  if (!booking) {
    return [
      { label: 'Submitted', state: 'pending' },
      { label: 'Reserving with provider', state: 'pending' },
      { label: 'Confirmed', state: 'pending' },
    ];
  }
  const status: BookingStatus = booking.status;
  if (status === 'PENDING') {
    return [
      { label: 'Submitted', state: 'done' },
      { label: 'Reserving with provider', state: 'active' },
      { label: 'Confirmed', state: 'pending' },
    ];
  }
  if (status === 'CONFIRMED') {
    return [
      { label: 'Submitted', state: 'done' },
      { label: 'Reserving with provider', state: 'done' },
      { label: 'Confirmed', state: 'done', detail: booking.providerRef },
    ];
  }
  // CANCELLED
  return [
    { label: 'Submitted', state: 'done' },
    { label: 'Reserving with provider', state: 'failed', detail: booking.cancellationReason },
    { label: 'Cancelled', state: 'failed' },
  ];
}

const STATE_STYLES: Record<StepState, { color: string; icon: keyof typeof Ionicons.glyphMap }> = {
  done: { color: '#2E7D32', icon: 'checkmark-circle' },
  active: { color: '#E65100', icon: 'time' },
  pending: { color: '#9E9E9E', icon: 'ellipse-outline' },
  failed: { color: '#B71C1C', icon: 'close-circle' },
};

function StepRow({ step, isActiveStep }: { step: Step; isActiveStep: boolean }) {
  const { color, icon } = STATE_STYLES[step.state];
  return (
    <View style={styles.stepRow}>
      <View style={styles.stepIcon}>
        {isActiveStep ? (
          <ActivityIndicator color={color} size="small" />
        ) : (
          <Ionicons name={icon} size={22} color={color} />
        )}
      </View>
      <View style={styles.stepText}>
        <Text variant="bodyMedium" style={{ color }}>
          {step.label}
        </Text>
        {step.detail ? (
          <Text variant="bodySmall" style={styles.stepDetail}>
            {step.detail}
          </Text>
        ) : null}
      </View>
    </View>
  );
}

export function BookingDetailScreen({ route }: Props) {
  const { id } = route.params;
  const { booking, loading, error, polling, setBooking } = useBookingDetail(id);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [tripBookings, setTripBookings] = useState<BookingResponse[]>([]);

  // Fetch the trip's other bookings whenever the current booking's tripId changes —
  // used to populate the "Trip" section and to size the cancel-cascade copy.
  useEffect(() => {
    if (!booking?.tripId) return;
    let cancelled = false;
    listTripBookings(booking.tripId)
      .then((items) => {
        if (!cancelled) setTripBookings(items);
      })
      .catch(() => {
        // Silent — siblings are a polish; the rest of the screen still works.
      });
    return () => {
      cancelled = true;
    };
  }, [booking?.tripId, booking?.status]);

  const siblings = tripBookings.filter((b) => b.id !== id);
  const cancellableInTrip = tripBookings.filter(
    (b) => b.status === 'PENDING' || b.status === 'CONFIRMED',
  ).length;

  async function handleCancel() {
    setCancelling(true);
    try {
      const updated = await cancelBooking(id);
      setBooking(updated);
      // Refetch the trip so the siblings section + counts reflect the cascade.
      if (booking?.tripId) {
        try {
          const refreshed = await listTripBookings(booking.tripId);
          setTripBookings(refreshed);
        } catch {
          // ignore
        }
      }
      setConfirmOpen(false);
    } catch (e) {
      Alert.alert('Error', apiErrorMessage(e, 'Failed to cancel booking'));
    } finally {
      setCancelling(false);
    }
  }

  const canCancel = booking?.status === 'PENDING' || booking?.status === 'CONFIRMED';
  // Asymmetric cascade rule (must mirror BookingService.cancelByUser):
  //   FLIGHT cancel → cancels every sibling in the trip ("anchor" of the trip)
  //   HOTEL  cancel → cancels only this booking, leaves the flight active
  const cascadesOnCancel = booking?.resourceType === 'FLIGHT' && cancellableInTrip > 1;
  const cancelButtonLabel = cascadesOnCancel
    ? `Cancel trip (${cancellableInTrip} bookings)`
    : 'Cancel booking';

  if (loading && !booking) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (error && !booking) {
    return (
      <View style={styles.centered}>
        <Text variant="titleMedium" style={styles.errorText}>
          {apiErrorMessage(error, 'Failed to load booking')}
        </Text>
      </View>
    );
  }

  const steps = buildSteps(booking);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {booking ? <BookingCard booking={booking} /> : null}

      <Card style={styles.workflowCard}>
        <Card.Title title="Workflow status" />
        <Card.Content>
          {steps.map((step, idx) => (
            <View key={step.label}>
              <StepRow step={step} isActiveStep={polling && step.state === 'active'} />
              {idx < steps.length - 1 ? <Divider style={styles.divider} /> : null}
            </View>
          ))}
        </Card.Content>
      </Card>

      {siblings.length > 0 ? (
        <Card style={styles.tripCard}>
          <Card.Title
            title="Trip"
            subtitle={`${tripBookings.length} bookings · trip ${booking?.tripId.slice(0, 8)}…`}
          />
          <Card.Content>
            {siblings.map((s, idx) => (
              <View key={s.id}>
                <View style={styles.siblingRow}>
                  <Text variant="bodyMedium">
                    {s.resourceType} → {s.destination}
                  </Text>
                  <Text variant="bodySmall" style={{ color: STATUS_COLOR[s.status] ?? '#616161' }}>
                    {s.status}
                  </Text>
                </View>
                {idx < siblings.length - 1 ? <Divider style={styles.divider} /> : null}
              </View>
            ))}
          </Card.Content>
        </Card>
      ) : null}

      {booking?.resourceType === 'HOTEL' ? (
        <AppointmentsSection bookingId={booking.id} />
      ) : null}

      {canCancel ? (
        <Button
          mode="outlined"
          onPress={() => setConfirmOpen(true)}
          style={styles.cancelButton}
          textColor="#B71C1C"
        >
          {cancelButtonLabel}
        </Button>
      ) : null}

      <Portal>
        <Dialog visible={confirmOpen} onDismiss={() => setConfirmOpen(false)}>
          <Dialog.Title>
            {cascadesOnCancel ? 'Cancel this trip?' : 'Cancel this booking?'}
          </Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">
              {cascadesOnCancel
                ? `Cancelling the flight cancels the entire trip — all ${cancellableInTrip} bookings will be marked CANCELLED. This cannot be undone.`
                : siblings.length > 0
                  ? 'Only this booking will be cancelled. Other bookings in the trip stay active.'
                  : 'The booking will be marked CANCELLED. This cannot be undone.'}
            </Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setConfirmOpen(false)} disabled={cancelling}>
              Keep
            </Button>
            <Button
              mode="contained"
              onPress={handleCancel}
              loading={cancelling}
              buttonColor="#B71C1C"
            >
              {cascadesOnCancel ? 'Cancel trip' : 'Cancel booking'}
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </ScrollView>
  );
}

const STATUS_COLOR: Record<BookingStatus, string> = {
  PENDING: '#E65100',
  CONFIRMED: '#2E7D32',
  CANCELLED: '#B71C1C',
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  content: { padding: 16 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  errorText: { color: '#B71C1C', textAlign: 'center' },
  workflowCard: { marginTop: 12 },
  stepRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8 },
  stepIcon: { width: 28, alignItems: 'center', justifyContent: 'center' },
  stepText: { flex: 1, marginLeft: 12 },
  stepDetail: { color: '#616161', marginTop: 2 },
  divider: { marginLeft: 40 },
  cancelButton: { marginTop: 16, borderColor: '#B71C1C' },
  tripCard: { marginTop: 12 },
  siblingRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
});
