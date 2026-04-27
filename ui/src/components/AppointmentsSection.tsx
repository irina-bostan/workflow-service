import React, { useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import {
  ActivityIndicator,
  Button,
  Card,
  Dialog,
  Divider,
  Menu,
  Portal,
  Text,
  TextInput,
  TouchableRipple,
  useTheme,
} from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';
import { DatePickerField } from './DatePickerField';
import { useAppointments } from '../hooks/useAppointments';
import { apiErrorMessage } from '../api/errors';
import type { AppointmentType } from '../api/types';

const TYPE_OPTIONS: { value: AppointmentType; label: string }[] = [
  { value: 'CHECK_IN', label: 'Check-in' },
  { value: 'SPA', label: 'Spa' },
  { value: 'RESTAURANT', label: 'Restaurant' },
  { value: 'CONCIERGE', label: 'Concierge' },
];

const formatScheduled = (iso: string) =>
  new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

interface Props {
  bookingId: string;
}

export function AppointmentsSection({ bookingId }: Props) {
  const theme = useTheme();
  const { appointments, loading, error, add } = useAppointments(bookingId, true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [typeMenuOpen, setTypeMenuOpen] = useState(false);
  const [appointmentType, setAppointmentType] = useState<AppointmentType>('SPA');
  const [scheduledAt, setScheduledAt] = useState<Date | null>(null);
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const selectedTypeLabel =
    TYPE_OPTIONS.find((opt) => opt.value === appointmentType)?.label ?? appointmentType;

  function reset() {
    setAppointmentType('SPA');
    setScheduledAt(null);
    setNotes('');
  }

  async function handleSubmit() {
    if (!scheduledAt) {
      Alert.alert('Validation', 'Please pick a date and time');
      return;
    }
    setSubmitting(true);
    try {
      await add({
        appointmentType,
        scheduledAt: scheduledAt.toISOString(),
        notes: notes.trim() || undefined,
      });
      setDialogOpen(false);
      reset();
    } catch (e) {
      Alert.alert('Error', apiErrorMessage(e, 'Failed to create appointment'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card style={styles.card}>
      <Card.Title title="Appointments" />
      <Card.Content>
        {loading ? (
          <ActivityIndicator />
        ) : error ? (
          <Text style={styles.errorText}>{apiErrorMessage(error, 'Failed to load appointments')}</Text>
        ) : appointments.length === 0 ? (
          <Text style={styles.empty}>No appointments yet.</Text>
        ) : (
          appointments.map((appt, idx) => (
            <View key={appt.id}>
              <View style={styles.row}>
                <Text variant="bodyMedium" style={styles.type}>
                  {appt.appointmentType.replace('_', ' ')}
                </Text>
                <Text variant="bodySmall">{formatScheduled(appt.scheduledAt)}</Text>
              </View>
              {appt.notes ? (
                <Text variant="bodySmall" style={styles.notes}>
                  {appt.notes}
                </Text>
              ) : null}
              {idx < appointments.length - 1 ? <Divider style={styles.divider} /> : null}
            </View>
          ))
        )}
        <Button mode="outlined" onPress={() => setDialogOpen(true)} style={styles.addBtn}>
          Add appointment
        </Button>
      </Card.Content>

      <Portal>
        <Dialog visible={dialogOpen} onDismiss={() => setDialogOpen(false)}>
          <Dialog.Title>New appointment</Dialog.Title>
          <Dialog.Content>
            <Text variant="labelLarge" style={styles.fieldLabel}>
              Type
            </Text>
            <Menu
              visible={typeMenuOpen}
              onDismiss={() => setTypeMenuOpen(false)}
              anchor={
                <TouchableRipple
                  onPress={() => setTypeMenuOpen(true)}
                  style={[styles.selector, { borderColor: theme.colors.outline }]}
                >
                  <View style={styles.selectorRow}>
                    <Text style={styles.selectorText}>{selectedTypeLabel}</Text>
                    <Ionicons name="chevron-down" size={18} color={theme.colors.primary} />
                  </View>
                </TouchableRipple>
              }
            >
              {TYPE_OPTIONS.map((opt) => (
                <Menu.Item
                  key={opt.value}
                  title={opt.label}
                  onPress={() => {
                    setAppointmentType(opt.value);
                    setTypeMenuOpen(false);
                  }}
                />
              ))}
            </Menu>
            <DatePickerField
              label="Scheduled at"
              value={scheduledAt}
              onChange={setScheduledAt}
              minimumDate={new Date()}
              mode="datetime"
            />
            <TextInput
              label="Notes (optional)"
              value={notes}
              onChangeText={setNotes}
              mode="outlined"
              multiline
              numberOfLines={2}
              style={styles.input}
            />
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDialogOpen(false)}>Cancel</Button>
            <Button mode="contained" onPress={handleSubmit} loading={submitting}>
              Save
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { marginTop: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
  type: { fontWeight: '600' },
  notes: { color: '#616161', marginBottom: 4 },
  empty: { color: '#9E9E9E' },
  divider: { marginVertical: 4 },
  addBtn: { marginTop: 12 },
  fieldLabel: { marginBottom: 4, marginTop: 4 },
  selector: {
    borderWidth: 1,
    borderRadius: 4,
    paddingHorizontal: 12,
    paddingVertical: 14,
    backgroundColor: '#fff',
    marginBottom: 4,
  },
  selectorRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  selectorText: { fontSize: 16, color: '#1C1B1F' },
  input: { marginTop: 12 },
  errorText: { color: '#B71C1C' },
});
