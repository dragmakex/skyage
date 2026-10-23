import * as DocumentPicker from 'expo-document-picker';
import { File, Paths } from 'expo-file-system';
import { BlurTargetView } from 'expo-blur';
import { useRef } from 'react';
import { router } from 'expo-router';
import * as Sharing from 'expo-sharing';
import { ArrowLeft, DownloadSimple, Export } from 'phosphor-react-native';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { parseImport } from '../src/domain/csv';
import { addFlights, allFlights, isDuplicate, newId, setSettings, useFlights, useSettings } from '../src/store';
import { Button } from '../src/ui/Button';
import { Chip } from '../src/ui/Chip';
import { Txt, useTheme } from '../src/ui/theme';
import { BlurTargetContext, Glass } from '../src/ui/Glass';
import { radius } from '../src/ui/tokens';

export default function Settings() {
  const { c, mode } = useTheme();
  const insets = useSafeAreaInsets();
  const s = useSettings();
  const flights = useFlights();
  const backdrop = useRef<View>(null);

  const exportFlights = async () => {
    const file = new File(Paths.cache, 'skyage-flights.json');
    if (file.exists) file.delete();
    file.create();
    file.write(JSON.stringify(allFlights(), null, 2));
    await Sharing.shareAsync(file.uri, { mimeType: 'application/json', dialogTitle: 'Export flights' });
  };

  const importFlights = async () => {
    const r = await DocumentPicker.getDocumentAsync({ type: ['application/json', 'text/csv', 'text/comma-separated-values', 'text/plain'], copyToCacheDirectory: true });
    if (r.canceled) return;
    let text: string;
    try {
      text = new File(r.assets[0].uri).textSync();
    } catch {
      Alert.alert('Could not read that file.');
      return;
    }
    const rows = parseImport(text);
    if (rows.length === 0) {
      Alert.alert('Nothing to import', 'No flights found in that file. Expected the JSON export or a CSV with date, from, to, flight.');
      return;
    }
    const fresh = rows.filter((f) => !isDuplicate(f));
    Alert.alert('Import flights', `Found ${rows.length} flights, ${rows.length - fresh.length} already here.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: fresh.length ? `Import ${fresh.length}` : 'OK', onPress: () => fresh.length && addFlights(fresh.map((f) => ({ ...f, id: newId(), source: 'import' as const }))) },
    ]);
  };

  return (
    <BlurTargetContext.Provider value={backdrop}>
    <View style={{ flex: 1, backgroundColor: c.background, paddingTop: insets.top }}>
      <BlurTargetView ref={backdrop} style={[StyleSheet.absoluteFill, { backgroundColor: c.background }]} />
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, height: 56 }}>
        <Button onPress={() => router.back()} variant="ghost" accessibilityLabel="Back" icon={<ArrowLeft size={22} color={c.foreground} />} />
        <Txt size="title" weight="semi" style={{ marginLeft: 4 }}>Settings</Txt>
      </View>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 28, paddingBottom: insets.bottom + 24 }}>
        <Section title="Appearance">
          <View style={{ flexDirection: 'row', gap: 4 }}>
            {(['dark', 'light'] as const).map((theme) => (
              <Chip key={theme} label={theme === 'dark' ? 'Dark' : 'Light'} selected={mode === theme} onPress={() => setSettings({ theme })} />
            ))}
          </View>
        </Section>
        <Section title="Units">
          <View style={{ flexDirection: 'row', gap: 4 }}>
            {(['km', 'mi'] as const).map((u) => (
              <Chip key={u} label={u} selected={s.units === u} onPress={() => setSettings({ units: u })} />
            ))}
          </View>
        </Section>
        <Section title="Data">
          <Txt size="caption" muted>{flights.length} flights stored on this device only.</Txt>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button title="Export" variant="secondary" icon={<Export size={16} color={c.foreground} />} onPress={exportFlights} disabled={flights.length === 0} style={{ flex: 1 }} />
            <Button title="Import" variant="secondary" icon={<DownloadSimple size={16} color={c.foreground} />} onPress={importFlights} style={{ flex: 1 }} />
          </View>
          <Txt size="caption" muted>Import takes the JSON export or a CSV with the columns date, from, to, flight.</Txt>
        </Section>
      </ScrollView>
    </View>
    </BlurTargetContext.Provider>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Glass style={{ gap: 12, padding: 16, borderRadius: radius.sheet }}>
      <Txt weight="medium">{title}</Txt>
      {children}
    </Glass>
  );
}
