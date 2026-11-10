import { File, Paths, UploadType } from 'expo-file-system';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { Button, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

// server.py on the host machine; 10.0.2.2 is the host as seen from an emulator
const DEFAULT_UPLOAD_URL = 'http://10.0.2.2:8080/upload';

const errorMessage = (error: unknown) =>
  error instanceof Error
    ? [error.message, error.cause instanceof Error ? error.cause.message : null]
        .filter(Boolean)
        .join(' → ')
    : String(error);

// Opens the picker directly in the app's own "Repro files" provider
const REPRO_FILES_URI =
  'content://expo.modules.reprodocumentsprovider.documents/document/root';

const pickFile = async () => {
  const result = await File.pickFileAsync({ initialUri: REPRO_FILES_URI });
  if (result.canceled || !result.result) {
    return null;
  }
  return Array.isArray(result.result) ? result.result[0] : result.result;
};

export default function App() {
  const [uploadUrl, setUploadUrl] = useState(DEFAULT_UPLOAD_URL);
  const [logs, setLogs] = useState<string[]>([]);
  const log = (line: string) => {
    console.log(`[repro] ${line}`);
    setLogs((previous) => [...previous, line]);
  };

  const pickAndUpload = async () => {
    const file = await pickFile();
    if (!file) {
      return;
    }
    log(`UPLOAD ${file.name}: uri=${file.uri} reported size=${file.size}`);
    try {
      const result = await file.upload(uploadUrl, {
        httpMethod: 'PUT',
        uploadType: UploadType.BINARY_CONTENT,
      });
      log(`UPLOAD ${file.name}: HTTP ${result.status} ${result.body}`);
    } catch (error) {
      log(`UPLOAD ${file.name}: FAILED ${errorMessage(error)}`);
    }
  };

  const pickAndCopy = async () => {
    const file = await pickFile();
    if (!file) {
      return;
    }
    const copy = new File(Paths.cache, `copy-${Date.now()}`);
    try {
      await file.copy(copy);
      const streamLength = (await file.bytes()).length;
      log(
        `COPY ${file.name}: reported size=${file.size} stream length=${streamLength} copy size=${copy.size}`,
      );
    } catch (error) {
      log(`COPY ${file.name}: FAILED ${errorMessage(error)}`);
    } finally {
      if (copy.exists) {
        copy.delete();
      }
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Upload URL</Text>
      <TextInput
        style={styles.input}
        value={uploadUrl}
        onChangeText={setUploadUrl}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <View style={styles.buttons}>
        <Button title="Pick + upload" onPress={pickAndUpload} testID="upload" />
        <Button title="Pick + copy" onPress={pickAndCopy} testID="copy" />
        <Button title="Clear" onPress={() => setLogs([])} />
      </View>
      <ScrollView style={styles.logs}>
        {logs.map((line, index) => (
          <Text key={index} style={styles.log} selectable>
            {line}
          </Text>
        ))}
      </ScrollView>
      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingTop: 60, paddingHorizontal: 16, gap: 8 },
  label: { fontWeight: 'bold' },
  input: { borderWidth: 1, borderColor: '#999', padding: 8 },
  buttons: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  logs: { flex: 1 },
  log: { fontFamily: 'monospace', fontSize: 12, marginBottom: 8 },
});
