import React, { useState } from 'react';
import { Modal, Platform, StyleSheet, View } from 'react-native';
import { Button, Surface, Text, TouchableRipple, useTheme } from 'react-native-paper';
import DateTimePicker, { DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { Ionicons } from '@expo/vector-icons';

interface Props {
  label: string;
  value: Date | null;
  onChange: (date: Date) => void;
  minimumDate?: Date;
  style?: object;
}

export function DatePickerField({ label, value, onChange, minimumDate, style }: Props) {
  const [show, setShow] = useState(false);
  const [tempDate, setTempDate] = useState<Date>(value ?? new Date());
  const theme = useTheme();

  function handleChange(_: DateTimePickerEvent, selected?: Date) {
    if (selected) setTempDate(selected);
    if (Platform.OS === 'android') {
      setShow(false);
      if (selected) onChange(selected);
    }
  }

  function handleConfirm() {
    onChange(tempDate);
    setShow(false);
  }

  const displayValue = value
    ? value.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
    : label;

  return (
    <View style={[styles.container, style]}>
      <TouchableRipple
        onPress={() => setShow(true)}
        style={[styles.field, { borderColor: theme.colors.outline }]}
      >
        <View style={styles.row}>
          <Text style={[styles.text, !value && styles.placeholder]}>{displayValue}</Text>
          <Ionicons name="calendar-outline" size={18} color={theme.colors.primary} />
        </View>
      </TouchableRipple>

      {Platform.OS === 'ios' ? (
        <Modal visible={show} transparent animationType="fade" onRequestClose={() => setShow(false)}>
          <View style={styles.backdrop}>
            <Surface style={styles.sheet} elevation={4}>
              <DateTimePicker
                value={tempDate}
                mode="date"
                display="inline"
                onChange={handleChange}
                minimumDate={minimumDate}
                style={styles.picker}
              />
              <View style={styles.actions}>
                <Button onPress={() => setShow(false)}>Cancel</Button>
                <Button mode="contained" onPress={handleConfirm}>
                  Confirm
                </Button>
              </View>
            </Surface>
          </View>
        </Modal>
      ) : (
        show && (
          <DateTimePicker
            value={tempDate}
            mode="date"
            display="default"
            onChange={handleChange}
            minimumDate={minimumDate}
          />
        )
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginBottom: 8 },
  field: {
    borderWidth: 1,
    borderRadius: 4,
    paddingHorizontal: 12,
    paddingVertical: 14,
    backgroundColor: '#fff',
  },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  text: { fontSize: 16, color: '#1C1B1F' },
  placeholder: { color: '#79747E' },
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  sheet: {
    borderRadius: 12,
    padding: 16,
    width: '100%',
  },
  picker: { alignSelf: 'stretch' },
  actions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
    marginTop: 8,
  },
});
