import React, { useState } from 'react';
import { FlatList, StyleSheet, View, Alert } from 'react-native';
import {
  ActivityIndicator,
  Button,
  Card,
  SegmentedButtons,
  Text,
  TextInput,
} from 'react-native-paper';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { searchOptions } from '../api/search';
import { apiErrorMessage } from '../api/errors';
import type { ResourceType, SearchResult } from '../api/types';
import type { SearchStackParamList } from '../navigation/types';
import { DatePickerField } from '../components/DatePickerField';

type Props = NativeStackScreenProps<SearchStackParamList, 'Search'>;

function toIsoUtc(date: Date): string {
  // Backend takes OffsetDateTime — needs an offset (Z = UTC).
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T00:00:00Z`;
}

export function SearchScreen({ navigation }: Props) {
  const [resourceType, setResourceType] = useState<ResourceType>('FLIGHT');
  const [destination, setDestination] = useState('');
  const [departureDate, setDepartureDate] = useState<Date | null>(null);
  const [returnDate, setReturnDate] = useState<Date | null>(null);
  const [travelerCount, setTravelerCount] = useState('1');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  async function handleSearch() {
    if (!destination.trim()) {
      Alert.alert('Validation', 'Destination is required');
      return;
    }
    setLoading(true);
    try {
      const data = await searchOptions({
        resourceType,
        destination: destination.trim(),
        departureDate: departureDate ? toIsoUtc(departureDate) : undefined,
        returnDate: returnDate ? toIsoUtc(returnDate) : undefined,
        travelerCount: parseInt(travelerCount, 10) || 1,
      });
      setResults(data);
      setSearched(true);
    } catch (e) {
      Alert.alert('Error', apiErrorMessage(e, 'Search failed'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.formArea}>
        <SegmentedButtons
          value={resourceType}
          onValueChange={(v) => setResourceType(v as ResourceType)}
          buttons={[
            { value: 'FLIGHT', label: 'Flights' },
            { value: 'HOTEL', label: 'Hotels' },
          ]}
          style={styles.segment}
        />
        <TextInput
          label="Destination"
          value={destination}
          onChangeText={setDestination}
          mode="outlined"
          style={styles.input}
          placeholder="e.g. NYC"
          autoCapitalize="characters"
        />
        <DatePickerField
          label="Departure date"
          value={departureDate}
          onChange={setDepartureDate}
          minimumDate={new Date()}
        />
        <DatePickerField
          label="Return date (optional)"
          value={returnDate}
          onChange={setReturnDate}
          minimumDate={departureDate ?? new Date()}
        />
        <TextInput
          label="Travelers"
          value={travelerCount}
          onChangeText={setTravelerCount}
          mode="outlined"
          style={styles.input}
          keyboardType="numeric"
        />
        <Button mode="contained" onPress={handleSearch} style={styles.button}>
          Search
        </Button>
      </View>

      <View style={styles.listArea}>
        {loading ? (
          <ActivityIndicator style={styles.spinner} />
        ) : (
          <FlatList
            data={results}
            keyExtractor={(item) => item.providerId}
            contentContainerStyle={styles.listContent}
            showsVerticalScrollIndicator
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="on-drag"
            nestedScrollEnabled
            renderItem={({ item }) => (
              <Card
                style={styles.card}
                onPress={() => navigation.navigate('BookingForm', { result: item })}
              >
                <Card.Title
                  title={`${item.providerName} — ${item.destination}`}
                  subtitle={`${item.resourceType} · ${item.availableSeats} seats available`}
                />
                <Card.Content>
                  <Text variant="bodyMedium">
                    {item.pricePerPerson} {item.currency} / person
                  </Text>
                  {item.departureTime ? (
                    <Text variant="bodySmall">
                      {new Date(item.departureTime).toLocaleString()}
                    </Text>
                  ) : null}
                </Card.Content>
              </Card>
            )}
            ListEmptyComponent={
              searched ? (
                <Text style={styles.empty}>No results found for "{destination}".</Text>
              ) : null
            }
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  formArea: { paddingHorizontal: 16, paddingTop: 16 },
  segment: { marginBottom: 12 },
  input: { marginBottom: 8 },
  button: { marginBottom: 12 },
  // FlatList must live inside a flex-1 parent so it has a bounded height to scroll within.
  listArea: { flex: 1 },
  listContent: { paddingHorizontal: 16, paddingBottom: 24 },
  spinner: { marginTop: 32 },
  card: { marginBottom: 8 },
  empty: { textAlign: 'center', marginTop: 32, color: '#9E9E9E' },
});
