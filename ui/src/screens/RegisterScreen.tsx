import React, { useState } from 'react';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { Button, Text, TextInput } from 'react-native-paper';
import { registerEmployee } from '../api/employees';
import { apiErrorMessage } from '../api/errors';

export function RegisterScreen() {
  const [employeeId, setEmployeeId] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [department, setDepartment] = useState('');
  const [costCentreDefault, setCostCentreDefault] = useState('');
  const [loading, setLoading] = useState(false);

  function reset() {
    setEmployeeId('');
    setFirstName('');
    setLastName('');
    setEmail('');
    setDepartment('');
    setCostCentreDefault('');
  }

  async function handleRegister() {
    if (!employeeId || !firstName || !lastName || !email || !department) {
      Alert.alert('Validation', 'All fields except Cost Centre are required');
      return;
    }
    setLoading(true);
    try {
      const emp = await registerEmployee({
        employeeId: employeeId.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim().toLowerCase(),
        department: department.trim(),
        costCentreDefault: costCentreDefault.trim() || undefined,
      });
      Alert.alert(
        'Registered',
        `${emp.firstName} ${emp.lastName} (${emp.employeeId}) registered successfully.`,
        [{ text: 'OK', onPress: reset }],
      );
    } catch (e) {
      Alert.alert('Error', apiErrorMessage(e, 'Registration failed'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text variant="titleMedium" style={styles.title}>
        New Employee
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
        testID="firstName"
        label="First name"
        value={firstName}
        onChangeText={setFirstName}
        mode="outlined"
        style={styles.input}
      />
      <TextInput
        testID="lastName"
        label="Last name"
        value={lastName}
        onChangeText={setLastName}
        mode="outlined"
        style={styles.input}
      />
      <TextInput
        testID="email"
        label="Email"
        value={email}
        onChangeText={setEmail}
        mode="outlined"
        style={styles.input}
        keyboardType="email-address"
        autoCapitalize="none"
      />
      <TextInput
        testID="department"
        label="Department"
        value={department}
        onChangeText={setDepartment}
        mode="outlined"
        style={styles.input}
      />
      <TextInput
        label="Cost centre (optional)"
        value={costCentreDefault}
        onChangeText={setCostCentreDefault}
        mode="outlined"
        style={styles.input}
        placeholder="CC-456"
      />
      <Button mode="contained" onPress={handleRegister} loading={loading} style={styles.button}>
        Register Employee
      </Button>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  content: { padding: 16 },
  title: { marginBottom: 16, fontWeight: '600' },
  input: { marginBottom: 8 },
  button: { marginTop: 8 },
});
