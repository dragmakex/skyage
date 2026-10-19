import { Pressable, StyleSheet, View } from 'react-native';
import { fmtDate } from '../domain/format';
import type { AirportIndex, Flight } from '../domain/types';
import { Txt, useTheme } from './theme';

export function FlightRow({ flight, index, onPress }: { flight: Flight; index: AirportIndex; onPress?: () => void }) {
  const { c } = useTheme();
  const a = index[flight.from];
  const b = index[flight.to];
  const ident = flight.airline && flight.number ? `${flight.airline} ${flight.number}` : undefined;
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        paddingVertical: 12,
        paddingHorizontal: 20,
        gap: 2,
        backgroundColor: pressed ? c.muted : 'transparent',
        borderBottomWidth: StyleSheet.hairlineWidth,
        borderBottomColor: c.border,
      })}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Txt size="title" mono weight="semi">{flight.from}</Txt>
        <View style={{ width: 24, height: 1, backgroundColor: c.flight }} />
        <Txt size="title" mono weight="semi">{flight.to}</Txt>
        <View style={{ flex: 1 }} />
        <Txt size="caption" mono muted>{fmtDate(flight.date)}</Txt>
      </View>
      <Txt size="caption" muted numberOfLines={1}>
        {a?.city ?? flight.from} to {b?.city ?? flight.to}{ident ? `, ${ident}` : ''}
      </Txt>
    </Pressable>
  );
}
