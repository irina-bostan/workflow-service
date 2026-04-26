import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Card, Chip, Text } from 'react-native-paper';
import type { BookingResponse, BookingStatus } from '../api/types';

const STATUS_COLOR: Record<BookingStatus, string> = {
  PENDING: '#E65100',
  CONFIRMED: '#2E7D32',
  CANCELLED: '#B71C1C',
};

interface Props {
  booking: BookingResponse;
  /** Optional extra content rendered between the title and the date row — e.g. a countdown */
  extra?: React.ReactNode;
  /** Date format applied to departure/return; defaults to short locale date */
  formatDate?: (iso: string) => string;
}

const defaultFormat = (iso: string) => new Date(iso).toLocaleDateString();

export function BookingCard({ booking, extra, formatDate = defaultFormat }: Props) {
  // If the server hasn't sent status (old build, missing migration, malformed payload)
  // skip the chip rather than rendering an empty grey square that pretends to be a state.
  const status = booking.status;
  const knownColor = status ? STATUS_COLOR[status] : undefined;
  const color = knownColor ?? '#616161';
  return (
    <Card style={styles.card}>
      <Card.Content>
        <View style={styles.header}>
          <Text variant="titleSmall" style={styles.title}>
            {booking.resourceType} → {booking.destination}
          </Text>
          {status ? (
            <Chip
              compact
              style={{ backgroundColor: color + '22' }}
              textStyle={{ color, fontSize: 11 }}
            >
              {status}
            </Chip>
          ) : null}
        </View>
        {extra}
        <Text variant="bodySmall">
          {formatDate(booking.departureDate)}
          {booking.returnDate ? ` – ${formatDate(booking.returnDate)}` : ''}
        </Text>
        <Text variant="bodySmall">
          {booking.travelerCount} traveler(s) · {booking.costCenterRef}
        </Text>
        {booking.tripPurpose ? (
          <Text variant="bodySmall" style={styles.purpose}>
            {booking.tripPurpose}
          </Text>
        ) : null}
      </Card.Content>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { marginBottom: 8 },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  title: { flexShrink: 1, marginRight: 8 },
  purpose: { marginTop: 2, color: '#616161' },
});
