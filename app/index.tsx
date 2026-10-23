import BottomSheet, { BottomSheetSectionList, BottomSheetScrollView, type BottomSheetBackgroundProps } from '@gorhom/bottom-sheet';
import { BlurTargetView } from 'expo-blur';
import { router, useFocusEffect } from 'expo-router';
import { Gear, Plus, Trash, X } from 'phosphor-react-native';
import { useCallback, useContext, useMemo, useRef, useState } from 'react';
import { Alert, AppState, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AIRPORT_INDEX } from '../src/data/airports';
import { toGeoJSON } from '../src/domain/arcs';
import { fmtDate, fmtDistance, fmtDuration } from '../src/domain/format';
import { haversineKm } from '../src/domain/geo';
import { stats } from '../src/domain/stats';
import type { Flight } from '../src/domain/types';
import { GlobeView, type GlobeHandle } from '../src/map/GlobeView';
import { removeFlight, useFlights, useSettings } from '../src/store';
import { Button } from '../src/ui/Button';
import { Chip } from '../src/ui/Chip';
import { EmptyState } from '../src/ui/EmptyState';
import { FlightRow } from '../src/ui/FlightRow';
import { BlurTargetContext, Glass } from '../src/ui/Glass';
import { Txt, useTheme } from '../src/ui/theme';
import { PEEK_FRACTION, SHEET_SNAP_POINTS, alpha, radius } from '../src/ui/tokens';

export default function Globe() {
  const { c } = useTheme();
  const insets = useSafeAreaInsets();
  const { height } = useWindowDimensions();
  const flights = useFlights();
  const { units } = useSettings();

  const [year, setYear] = useState<string>('all');
  const [selectedId, setSelectedId] = useState<string>();
  const [city, setCity] = useState<string>();

  const globe = useRef<GlobeHandle>(null);
  const sheet = useRef<BottomSheet>(null);
  const blurTarget = useContext(BlurTargetContext);
  const [sheetIndex, setSheetIndex] = useState(0);
  const [sheetSurface, setSheetSurface] = useState(0);
  useFocusEffect(useCallback(() => {
    // Native sheets can retain a detached/transparent surface after navigation or resume.
    const restore = () => setSheetSurface((value) => value + 1);
    restore();
    const app = AppState.addEventListener('change', (state) => { if (state === 'active') restore(); });
    return () => app.remove();
  }, []));
  const sheetTop = insets.top + 128;
  const peekHeight = (height - sheetTop) * PEEK_FRACTION;

  const years = useMemo(() => [...new Set(flights.map((f) => f.date.slice(0, 4)))].sort().reverse(), [flights]);
  const visible = useMemo(() => (year === 'all' ? flights : flights.filter((f) => f.date.startsWith(year))), [flights, year]);
  const geo = useMemo(() => toGeoJSON(visible, AIRPORT_INDEX, selectedId), [visible, selectedId]);
  const totals = useMemo(() => stats(visible, AIRPORT_INDEX), [visible]);
  const selected = selectedId ? flights.find((f) => f.id === selectedId) : undefined;

  // Launch center: mean of the cities flown through, or 20N 0E.
  const center = useMemo<[number, number]>(() => {
    const pts = geo.cities.features.map((f) => (f.geometry as GeoJSON.Point).coordinates);
    if (pts.length === 0) return [0, 20];
    return [pts.reduce((s, p) => s + p[0], 0) / pts.length, pts.reduce((s, p) => s + p[1], 0) / pts.length];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stopIdle = useCallback(() => globe.current?.stopIdle(), []);

  const flyToFlight = useCallback(
    (f: Flight) => {
      const a = AIRPORT_INDEX[f.from];
      const b = AIRPORT_INDEX[f.to];
      if (!a || !b) return;
      globe.current?.focus([a.lon, a.lat], [b.lon, b.lat]);
    },
    [],
  );

  const selectFlight = useCallback(
    (id: string) => {
      stopIdle();
      setCity(undefined);
      setSelectedId(id);
      const f = flights.find((x) => x.id === id);
      if (f) flyToFlight(f);
      sheet.current?.snapToIndex(1);
    },
    [flights, flyToFlight, stopIdle],
  );

  const selectCity = useCallback(
    (iata: string) => {
      stopIdle();
      setSelectedId(undefined);
      setCity(iata);
      sheet.current?.snapToIndex(1);
    },
    [stopIdle],
  );

  const clear = useCallback(() => {
    setSelectedId(undefined);
    setCity(undefined);
  }, []);

  const selectYear = useCallback((value: string) => {
    setYear(value);
    clear();
    sheet.current?.snapToIndex(0);
  }, [clear]);

  const confirmDelete = useCallback((f: Flight) => {
    Alert.alert('Delete flight?', `${f.from} to ${f.to} on ${fmtDate(f.date)}`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => { removeFlight(f.id); setSelectedId(undefined); } },
    ]);
  }, []);

  const sections = useMemo(() => {
    const list = city ? visible.filter((f) => f.from === city || f.to === city) : visible;
    const ys = [...new Set(list.map((f) => f.date.slice(0, 4)))].sort().reverse();
    return ys.map((y) => ({ title: y, data: list.filter((f) => f.date.startsWith(y)) }));
  }, [visible, city]);

  const background = useCallback(
    ({ style }: BottomSheetBackgroundProps) => (
      <Glass style={[style, { borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet }]} />
    ),
    [],
  );

  return (
      <View style={{ flex: 1, backgroundColor: c.background }}>
        <BlurTargetView ref={blurTarget ?? undefined} style={[StyleSheet.absoluteFill, { backgroundColor: c.background }]}>
          <View style={{ position: 'absolute', top: sheetTop, left: 0, right: 0, bottom: peekHeight + 8 }}>
            <GlobeView ref={globe} data={geo} selectedId={selectedId} center={center} onCity={selectCity} onFlight={selectFlight} onClear={clear} />
          </View>
        </BlurTargetView>

        <View style={{ position: 'absolute', top: insets.top + 12, left: 20, right: 20, gap: 16 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Txt size="stat" weight="semi" style={{ letterSpacing: -0.8 }}>Skyage</Txt>
            <Button accessibilityLabel="Settings" variant="secondary" icon={<Gear size={19} color={c.foreground} />} onPress={() => router.push('/settings')} />
          </View>
          <View style={{ alignSelf: 'flex-start', maxWidth: '100%', height: 52 }}>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, alignItems: 'center', height: 52 }}>
              <Chip label="All flights" selected={year === 'all'} onPress={() => selectYear('all')} />
              {years.map((y) => <Chip key={y} label={y} selected={year === y} onPress={() => selectYear(y)} />)}
            </ScrollView>
          </View>
        </View>

        <BottomSheet
          key={sheetSurface}
          ref={sheet}
          index={sheetIndex}
          animateOnMount={false}
          enablePanDownToClose={false}
          onChange={(index) => { if (index >= 0) setSheetIndex(index); }}
          snapPoints={SHEET_SNAP_POINTS}
          enableDynamicSizing={false}
          topInset={sheetTop}
          // Keep a native surface so Android does not flatten away the translated menu.
          style={{ backgroundColor: alpha(c.surface, 0.01), borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet }}
          backgroundComponent={background}
          handleComponent={() => (
            <Pressable
              onPress={() => sheet.current?.snapToIndex(sheetIndex === 0 ? 1 : 0)}
              accessibilityRole="button"
              accessibilityLabel={sheetIndex === 0 ? 'Expand flight menu' : 'Collapse flight menu'}
              accessibilityHint="Drag to resize the flight menu."
              style={{ height: 28, alignItems: 'center', justifyContent: 'center' }}
            >
              <View style={{ width: 36, height: 4, borderRadius: radius.pill, backgroundColor: alpha(c.foreground, 0.3) }} />
            </Pressable>
          )}
        >
          {selected ? (
            <BottomSheetScrollView contentContainerStyle={{ padding: 20, paddingBottom: insets.bottom + 24, gap: 16 }}>
              <Detail flight={selected} units={units} onClose={clear} onShow={() => { flyToFlight(selected); sheet.current?.snapToIndex(0); }} onDelete={() => confirmDelete(selected)} />
            </BottomSheetScrollView>
          ) : (
            <BottomSheetSectionList
              sections={sections}
              keyExtractor={(f: Flight) => f.id}
              stickySectionHeadersEnabled
              contentContainerStyle={{ paddingBottom: insets.bottom + 24 }}
              ListHeaderComponent={
                city ? (
                  <View style={{ minHeight: peekHeight - 28, paddingHorizontal: 20, paddingBottom: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                    <View>
                      <Txt size="stat" weight="semi">{AIRPORT_INDEX[city]?.city ?? city}</Txt>
                      <Txt size="caption" mono muted>{sections.reduce((n, s) => n + s.data.length, 0)} flights</Txt>
                    </View>
                    <Button onPress={clear} variant="ghost" accessibilityLabel="Close city flights" icon={<X size={20} color={c.foreground} />} />
                  </View>
                ) : (
                  <View style={{ minHeight: peekHeight - 28, paddingHorizontal: 20, paddingTop: 4, paddingBottom: 20, justifyContent: 'center', gap: 16 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                      <View style={{ flex: 1, gap: 3 }}>
                        <Txt size="stat" weight="semi" style={{ letterSpacing: -0.6 }}>{totals.flights} {totals.flights === 1 ? 'flight' : 'flights'}</Txt>
                        {flights.length === 0 && <Txt size="caption" muted>Add your first flight.</Txt>}
                      </View>
                      <Button title="Add flight" pill icon={<Plus size={16} color={c.background} />} onPress={() => router.push('/add')} />
                    </View>
                    <View style={{ flexDirection: 'row', gap: 24, alignItems: 'center' }}>
                      <Txt size="caption" mono muted>{fmtDistance(totals.km, units)} traveled</Txt>
                      <View style={{ width: 1, height: 12, backgroundColor: alpha(c.foreground, 0.16) }} />
                      <Txt size="caption" mono muted>{totals.airports} airports</Txt>
                    </View>
                  </View>
                )
              }
              renderSectionHeader={({ section }) => (
                <View style={{ paddingHorizontal: 20, paddingVertical: 8, backgroundColor: alpha(c.background, 0.92) }}>
                  <Txt size="caption" mono muted>{section.title}</Txt>
                </View>
              )}
              renderItem={({ item }) => <FlightRow flight={item} index={AIRPORT_INDEX} onPress={() => selectFlight(item.id)} />}
              ListEmptyComponent={
                flights.length > 0 ? <EmptyState title="Nothing here" body="No flights match this filter." /> : null
              }
            />
          )}
        </BottomSheet>
      </View>
  );
}

function Detail({ flight, units, onClose, onShow, onDelete }: { flight: Flight; units: 'km' | 'mi'; onClose: () => void; onShow: () => void; onDelete: () => void }) {
  const { c } = useTheme();
  const a = AIRPORT_INDEX[flight.from];
  const b = AIRPORT_INDEX[flight.to];
  const km = a && b ? haversineKm([a.lon, a.lat], [b.lon, b.lat]) : undefined;
  const duration = fmtDuration(flight.depTime, flight.arrTime);
  const rows: [string, string][] = [
    ['Date', fmtDate(flight.date)],
    ...(flight.airline && flight.number ? [['Flight', `${flight.airline} ${flight.number}`] as [string, string]] : []),
    ...(km ? [['Distance', fmtDistance(km, units)] as [string, string]] : []),
    ...(duration ? [['Duration', duration] as [string, string]] : []),
    ...(flight.seat ? [['Seat', flight.seat] as [string, string]] : []),
  ];
  return (
    <>
      <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
        <Button onPress={onClose} variant="ghost" accessibilityLabel="Close flight details" icon={<X size={20} color={c.foreground} />} />
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 16 }}>
        <View style={{ gap: 2 }}>
          <Txt size="display" mono weight="semi">{flight.from}</Txt>
          <Txt size="caption" muted>{a?.city ?? flight.from}</Txt>
        </View>
        <View style={{ flex: 1, height: 1, backgroundColor: c.border }} />
        <View style={{ gap: 2, alignItems: 'flex-end' }}>
          <Txt size="display" mono weight="semi">{flight.to}</Txt>
          <Txt size="caption" muted>{b?.city ?? flight.to}</Txt>
        </View>
      </View>
      <View>
        {rows.map(([label, value]) => (
          <View key={label} style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: c.border }}>
            <Txt muted>{label}</Txt>
            <Txt mono>{value}</Txt>
          </View>
        ))}
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button title="Show on globe" variant="secondary" onPress={onShow} style={{ flex: 1 }} />
        <Button title="Delete" variant="ghost" icon={<Trash size={16} color={c.foreground} />} onPress={onDelete} />
      </View>
    </>
  );
}
