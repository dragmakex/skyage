import { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { CameraView, scanFromURLAsync, useCameraPermissions, type BarcodeType } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { router } from 'expo-router';
import { CaretLeft, Hash, Image as ImageIcon, MapTrifold, Scan, X } from 'phosphor-react-native';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, KeyboardAvoidingView, Linking, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { lookupFlight, type Leg } from '../src/api/aerodatabox';
import { AIRPORT_INDEX, searchAirports } from '../src/data/airports';
import { dateFromDayOfYear, parseBcbp, type Bcbp } from '../src/domain/bcbp';
import { parseFlightNumber } from '../src/domain/flightNumber';
import { fmtDate, isoToday, toIsoDate } from '../src/domain/format';
import type { Flight } from '../src/domain/types';
import { addFlights, isDuplicate, newId } from '../src/store';
import { Button } from '../src/ui/Button';
import { Field } from '../src/ui/Field';
import { Glass } from '../src/ui/Glass';
import { OptionRow } from '../src/ui/OptionRow';
import { Txt, useTheme } from '../src/ui/theme';
import { alpha, radius } from '../src/ui/tokens';

type Step = 'menu' | 'scan' | 'number' | 'route' | 'review';
type Draft = Omit<Flight, 'id'>;
const TYPES: BarcodeType[] = ['pdf417', 'aztec', 'qr'];
const TITLES: Record<Step, string> = { menu: 'Add flight', scan: 'Scan boarding pass', number: 'Flight number', route: 'Route', review: 'Review' };

export default function Add() {
  const { c } = useTheme();
  const insets = useSafeAreaInsets();
  const [step, setStep] = useState<Step>('menu');
  const [draft, setDraft] = useState<Draft>({ from: '', to: '', date: isoToday(), source: 'manual' });

  const go = (patch: Partial<Draft>, next: Step) => {
    setDraft((d) => ({ ...d, ...patch }));
    setStep(next);
  };

  const save = (d: Draft) => {
    const f: Flight = { ...d, id: newId(), from: d.from.toUpperCase(), to: d.to.toUpperCase() };
    const commit = () => {
      addFlights([f]);
      router.back();
    };
    if (isDuplicate(f)) {
      Alert.alert('Already added', `${f.from} to ${f.to} on ${fmtDate(f.date)} is already in your list.`, [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Add anyway', onPress: commit },
      ]);
    } else commit();
  };

  return (
    <View style={{ flex: 1, justifyContent: 'flex-end' }}>
      <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: alpha(c.background, 0.72) }]} onPress={() => router.back()} accessibilityLabel="Close" />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Glass style={{ borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet, maxHeight: '92%' }}>
          <View style={{ alignItems: 'center', paddingTop: 10 }}>
            <View style={{ width: 36, height: 4, borderRadius: 2, backgroundColor: c.mutedForeground }} />
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingTop: 8 }}>
            {step === 'menu' ? <View style={{ width: 44, height: 44 }} /> : <Button onPress={() => setStep('menu')} variant="ghost" accessibilityLabel="Back" icon={<CaretLeft size={20} color={c.foreground} />} />}
            <Txt size="title" weight="semi" style={{ flex: 1, textAlign: 'center' }}>{TITLES[step]}</Txt>
            <Button onPress={() => router.back()} variant="ghost" accessibilityLabel="Close add flight" icon={<X size={20} color={c.foreground} />} />
          </View>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 20, gap: 12, paddingBottom: insets.bottom + 20 }}>
            {step === 'menu' && (
              <>
                <OptionRow icon={<Scan size={20} color={c.foreground} />} title="Scan boarding pass" description="Camera, or a screenshot from the gallery" onPress={() => setStep('scan')} />
                <OptionRow icon={<Hash size={20} color={c.foreground} />} title="Flight number" description="UA 123 and a date, looked up online" onPress={() => setStep('number')} />
                <OptionRow icon={<MapTrifold size={20} color={c.foreground} />} title="Route" description="From, to and a date. Works offline" onPress={() => setStep('route')} />
              </>
            )}
            {step === 'scan' && (
              <ScanStep
                onResult={(b) => go({ from: b.from, to: b.to, airline: b.airline, number: b.number, seat: b.seat, date: dateFromDayOfYear(b.dayOfYear), source: 'scan' }, 'review')}
                onManual={() => setStep('number')}
              />
            )}
            {step === 'number' && <NumberStep draft={draft} onDone={(d) => go(d, 'review')} onRoute={(d) => go(d, 'route')} />}
            {step === 'route' && <RouteStep draft={draft} onDone={(d) => go(d, 'review')} />}
            {step === 'review' && <ReviewStep draft={draft} onSave={save} />}
          </ScrollView>
        </Glass>
      </KeyboardAvoidingView>
    </View>
  );
}

function ScanStep({ onResult, onManual }: { onResult: (b: Bcbp) => void; onManual: () => void }) {
  const { c } = useTheme();
  const [perm, request] = useCameraPermissions();
  const [msg, setMsg] = useState<string>();
  const lock = useRef(false);

  useEffect(() => {
    if (perm && !perm.granted && perm.canAskAgain) request();
  }, [perm, request]);

  const handle = (data: string) => {
    if (lock.current) return;
    const b = parseBcbp(data);
    if (!b) {
      setMsg('That barcode is not a boarding pass.');
      return;
    }
    lock.current = true;
    onResult(b);
  };

  const fromGallery = async () => {
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 1 });
    if (r.canceled) return;
    const found = await scanFromURLAsync(r.assets[0].uri, TYPES);
    const hit = found.map((x) => parseBcbp(x.data)).find((b): b is Bcbp => b !== null);
    if (hit) onResult(hit);
    else setMsg('No barcode in that picture. Try a screenshot of the pass, or type the flight number.');
  };

  return (
    <>
      {perm?.granted ? (
        <CameraView style={{ height: 280, borderRadius: radius.control, overflow: 'hidden' }} barcodeScannerSettings={{ barcodeTypes: TYPES }} onBarcodeScanned={(e) => handle(e.data)} />
      ) : (
        <View style={{ height: 280, borderRadius: radius.control, backgroundColor: c.muted, alignItems: 'center', justifyContent: 'center', gap: 12, padding: 20 }}>
          <Txt muted style={{ textAlign: 'center' }}>Camera permission is needed to scan a pass.</Txt>
          <Button title={perm?.canAskAgain === false ? 'Open app settings' : 'Allow camera'} variant="secondary" onPress={perm?.canAskAgain === false ? Linking.openSettings : request} />
        </View>
      )}
      <Txt size="caption" muted>{msg ?? 'Point at the barcode on the boarding pass.'}</Txt>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button title="From gallery" variant="secondary" icon={<ImageIcon size={16} color={c.foreground} />} onPress={fromGallery} style={{ flex: 1 }} />
        <Button title="Type flight number" variant="ghost" onPress={onManual} />
      </View>
    </>
  );
}

function NumberStep({ draft, onDone, onRoute }: { draft: Draft; onDone: (d: Partial<Draft>) => void; onRoute: (d: Partial<Draft>) => void }) {
  const { c } = useTheme();
  const [ident, setIdent] = useState(draft.airline && draft.number ? `${draft.airline} ${draft.number}` : '');
  const [date, setDate] = useState(draft.date);
  const [legs, setLegs] = useState<Leg[]>();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string>();
  const parsed = parseFlightNumber(ident);

  const pick = (l: Leg) => parsed && onDone({ from: l.from, to: l.to, date, airline: parsed.airline, number: parsed.number, depTime: l.depTime, arrTime: l.arrTime, source: 'lookup' });

  const lookup = async () => {
    if (!parsed) return;
    setBusy(true);
    setErr(undefined);
    setLegs(undefined);
    try {
      const r = await lookupFlight(parsed.airline, parsed.number, date);
      if (r.length === 0) setErr('No record for that flight on that date. Enter the route instead.');
      else if (r.length === 1) pick(r[0]);
      else setLegs(r);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Lookup failed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Field label="Flight number" value={ident} onChangeText={setIdent} placeholder="UA 123" autoCapitalize="characters" autoCorrect={false} mono />
      <DateField value={date} onChange={setDate} />
      {busy ? <View style={{ height: 44, borderRadius: radius.control, backgroundColor: c.muted }} /> : null}
      {err ? (
        <View style={{ gap: 8 }}>
          <Txt size="caption">{err}</Txt>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button title="Retry" variant="secondary" onPress={lookup} />
            <Button title="Enter the route" variant="ghost" onPress={() => onRoute({ date, airline: parsed?.airline, number: parsed?.number })} />
          </View>
        </View>
      ) : null}
      {legs?.map((l, i) => (
        <OptionRow key={i} onPress={() => pick(l)} icon={<MapTrifold size={20} color={c.foreground} />} title={`${l.from} to ${l.to}`} description={`${AIRPORT_INDEX[l.from]?.city ?? l.from} to ${AIRPORT_INDEX[l.to]?.city ?? l.to}`} />
      ))}
      {!err ? <Button title="Look up" onPress={lookup} disabled={!parsed || busy} /> : null}
    </>
  );
}

function RouteStep({ draft, onDone }: { draft: Draft; onDone: (d: Partial<Draft>) => void }) {
  const [from, setFrom] = useState(draft.from);
  const [to, setTo] = useState(draft.to);
  const [date, setDate] = useState(draft.date);
  const [ident, setIdent] = useState(draft.airline && draft.number ? `${draft.airline} ${draft.number}` : '');
  const parsed = parseFlightNumber(ident);
  return (
    <>
      <AirportField label="From" value={from} onChange={setFrom} />
      <AirportField label="To" value={to} onChange={setTo} />
      <DateField value={date} onChange={setDate} />
      <Field label="Flight number (optional)" value={ident} onChangeText={setIdent} placeholder="UA 123" autoCapitalize="characters" autoCorrect={false} mono />
      <Button
        title="Continue"
        disabled={!AIRPORT_INDEX[from] || !AIRPORT_INDEX[to]}
        onPress={() => onDone({ from, to, date, airline: parsed?.airline, number: parsed?.number, source: 'manual' })}
      />
    </>
  );
}

function ReviewStep({ draft, onSave }: { draft: Draft; onSave: (d: Draft) => void }) {
  const [d, setD] = useState(draft);
  const set = (patch: Partial<Draft>) => setD((x) => ({ ...x, ...patch }));
  return (
    <>
      <AirportField label="From" value={d.from} onChange={(from) => set({ from })} />
      <AirportField label="To" value={d.to} onChange={(to) => set({ to })} />
      <DateField value={d.date} onChange={(date) => set({ date })} />
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Field label="Airline" value={d.airline ?? ''} onChangeText={(v) => set({ airline: v.toUpperCase() || undefined })} placeholder="UA" autoCapitalize="characters" autoCorrect={false} mono maxLength={3} />
        </View>
        <View style={{ flex: 1 }}>
          <Field label="Number" value={d.number ?? ''} onChangeText={(v) => set({ number: v.replace(/\D/g, '') || undefined })} placeholder="123" keyboardType="number-pad" mono maxLength={4} />
        </View>
        <View style={{ flex: 1 }}>
          <Field label="Seat" value={d.seat ?? ''} onChangeText={(v) => set({ seat: v.toUpperCase() || undefined })} placeholder="12A" autoCapitalize="characters" autoCorrect={false} mono maxLength={4} />
        </View>
      </View>
      <Button title="Save flight" disabled={!AIRPORT_INDEX[d.from] || !AIRPORT_INDEX[d.to]} onPress={() => onSave(d)} />
    </>
  );
}

function AirportField({ label, value, onChange }: { label: string; value: string; onChange: (iata: string) => void }) {
  const { c } = useTheme();
  const [q, setQ] = useState(value);
  const [open, setOpen] = useState(false);
  const results = useMemo(() => (open ? searchAirports(q) : []), [q, open]);
  const known = AIRPORT_INDEX[value];
  return (
    <View style={{ gap: 6 }}>
      <Field
        label={known ? `${label}, ${known.city}` : label}
        value={q}
        onChangeText={(t) => {
          setQ(t);
          setOpen(true);
          onChange(t.trim().toUpperCase());
        }}
        onFocus={() => setOpen(true)}
        placeholder="City or airport code"
        autoCapitalize="characters"
        autoCorrect={false}
        mono
      />
      {results.map((a) => (
        <Pressable
          key={a.iata}
          accessibilityRole="button"
          accessibilityLabel={`${a.iata}, ${a.city}, ${a.name}`}
          onPress={() => {
            setQ(a.iata);
            onChange(a.iata);
            setOpen(false);
          }}
          style={({ pressed }) => ({ opacity: pressed ? 0.85 : 1 })}
        >
          <Glass interactive tone="clear" style={{ flexDirection: 'row', gap: 12, alignItems: 'center', minHeight: 44, paddingVertical: 8, paddingHorizontal: 12, borderRadius: radius.control }}>
            <Txt mono weight="medium" style={{ width: 44 }}>{a.iata}</Txt>
            <Txt size="caption" muted numberOfLines={1} style={{ flex: 1 }}>{a.city}, {a.name}</Txt>
          </Glass>
        </Pressable>
      ))}
    </View>
  );
}

function DateField({ value, onChange }: { value: string; onChange: (iso: string) => void }) {
  if (Platform.OS !== 'android') {
    return <Field label="Date" value={value} onChangeText={onChange} placeholder="YYYY-MM-DD" mono />;
  }
  const open = () =>
    DateTimePickerAndroid.open({
      value: new Date(`${value}T12:00:00`),
      mode: 'date',
      onChange: (_, d) => d && onChange(toIsoDate(d)),
    });
  return (
    <View style={{ gap: 6 }}>
      <Txt size="caption" muted>Date</Txt>
      <Glass interactive style={{ borderRadius: radius.control }}>
        <Pressable onPress={open} accessibilityRole="button" accessibilityLabel="Choose flight date" style={{ minHeight: 44, paddingHorizontal: 12, justifyContent: 'center' }}>
          <Txt mono style={{ fontSize: 16 }}>{fmtDate(value)}</Txt>
        </Pressable>
      </Glass>
    </View>
  );
}
