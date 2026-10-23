import { Image, StyleSheet, View } from 'react-native';
import { useTheme } from '../ui/theme';

// Native images remain visible before the first GL frame and during context recovery.
export function GlobePreview({ diameter }: { diameter: number }) {
  const { c } = useTheme();
  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center' }]}>
      <View style={{ width: diameter, height: diameter, borderRadius: diameter / 2, overflow: 'hidden', backgroundColor: c.background, borderWidth: StyleSheet.hairlineWidth, borderColor: c.countryBorder }}>
        <Image accessible={false} source={require('../../assets/globe-land.png')} style={[StyleSheet.absoluteFill, { width: diameter, height: diameter, tintColor: c.land }]} />
        <Image accessible={false} source={require('../../assets/globe-borders.png')} style={[StyleSheet.absoluteFill, { width: diameter, height: diameter, tintColor: c.countryBorder }]} />
      </View>
    </View>
  );
}
