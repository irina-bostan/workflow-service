import React, { useState } from 'react';
import { Modal, Platform, StyleSheet, View } from 'react-native';
import { Button, Surface, Text, TouchableRipple, useTheme } from 'react-native-paper';
import DateTimePicker, { DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { Ionicons } from '@expo/vector-icons';

type PickerMode = 'date' | 'datetime';

interface Props {
  label: string;
  value: Date | null;
  onChange: (date: Date) => void;
  minimumDate?: Date;
  /** When 'datetime', the picker captures hours/minutes too. iOS shows a combined
   *  picker; Android chains date → time pickers since it has no native datetime mode. */
  mode?: PickerMode;
  style?: object;
}

export function DatePickerField({
  label,
  value,
  onChange,
  minimumDate,
  mode = 'date',
  style,
}: Props) {
  const [show, setShow] = useState(false);
  // Android uses a separate time-picker step after the date is picked.
  const [androidStep, setAndroidStep] = useState<'date' | 'time'>('date');
  const [tempDate, setTempDate] = useState<Date>(value ?? new Date());
  const theme = useTheme();

  function handleIosChange(_: DateTimePickerEvent, selected?: Date) {
    if (selected) setTempDate(selected);
  }

  function handleAndroidChange(_: DateTimePickerEvent, selected?: Date) {
    setShow(false);
    if (!selected) return;
    if (mode === 'datetime' && androidStep === 'date') {
      // Date chosen — open the time picker next.
      setTempDate(selected);
      setAndroidStep('time');
      setShow(true);
      return;
    }
    onChange(selected);
    setAndroidStep('date'); // reset for next use
  }

  function handleConfirm() {
    onChange(tempDate);
    setShow(false);
  }

  function openPicker() {
    setTempDate(value ?? new Date());
    setAndroidStep('date');
    setShow(true);
  }

  const displayValue = value
    ? mode === 'datetime'
      ? value.toLocaleString(undefined, {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        })
      : value.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
    : label;

  return (
    <View style={[styles.container, style]}>
      <TouchableRipple
        onPress={openPicker}
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
                mode={mode}
                display="inline"
                onChange={handleIosChange}
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
            mode={mode === 'datetime' ? androidStep : 'date'}
            display="default"
            onChange={handleAndroidChange}
            minimumDate={minimumDate}
            is24Hour
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
