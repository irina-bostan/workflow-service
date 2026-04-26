import React, { useState } from 'react';
import { Alert, FlatList, StyleSheet, View } from 'react-native';
import { Button, Text, TextInput } from 'react-native-paper';
import { listBookings } from '../api/bookings';
import { apiErrorMessage } from '../api/errors';
import type { BookingResponse } from '../api/types';
import { BookingCard } from '../components/BookingCard';

export function BookingsScreen() {
  const [employeeId, setEmployeeId] = useState('');
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [searched, setSearched] = useState(false);

  async function fetchBookings(p = 0) {
    if (!employeeId.trim()) {
      Alert.alert('Validation', 'Enter an employee ID');
      return;
    }
    setLoading(true);
    try {
      const paged = await listBookings(employeeId.trim(), p);
      setBookings((prev) => (p === 0 ? paged.bookings : [...prev, ...paged.bookings]));
      setTotalPages(paged.totalPages);
      setPage(p);
      setSearched(true);
    } catch (e) {
      Alert.alert('Error', apiErrorMessage(e, 'Failed to load bookings'));
    } finally {
      setLoading(false);
    }
  }

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
          data={bookings}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => <BookingCard booking={item} />}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          nestedScrollEnabled
          ListEmptyComponent={
            searched && !loading ? (
              <Text style={styles.empty}>No bookings found for "{employeeId}".</Text>
            ) : null
          }
          ListFooterComponent={
            page + 1 < totalPages ? (
              <Button onPress={() => fetchBookings(page + 1)} loading={loading}>
                Load more
              </Button>
            ) : null
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
});
