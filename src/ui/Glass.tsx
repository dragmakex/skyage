import { BlurView } from 'expo-blur';
import { createContext, useContext, useEffect, useState, type ReactNode, type RefObject } from 'react';
import { AccessibilityInfo, Platform, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from './theme';
import { GLASS_BLUR, alpha } from './tokens';

export const BlurTargetContext = createContext<RefObject<View | null> | null>(null);

// Native approximation of glassapp.dev's dark material, not its DOM/WebGPU renderer.
export function Glass({ style, children }: { style?: StyleProp<ViewStyle>; children?: ReactNode }) {
  const { c, mode } = useTheme();
  const target = useContext(BlurTargetContext);
  const [reduceTransparency, setReduceTransparency] = useState(false);
  useEffect(() => {
    if (Platform.OS !== 'ios') return;
    let cancelled = false;
    AccessibilityInfo.isReduceTransparencyEnabled().then((value) => { if (!cancelled) setReduceTransparency(value); }).catch(() => { if (!cancelled) setReduceTransparency(true); });
    const listener = AccessibilityInfo.addEventListener('reduceTransparencyChanged', setReduceTransparency);
    return () => { cancelled = true; listener.remove(); };
  }, []);
  const blur = GLASS_BLUR && !reduceTransparency && (Platform.OS === 'ios' || (target !== null && Number(Platform.Version) >= 31));
  return (
    <View style={[
      {
        overflow: 'hidden',
        backgroundColor: alpha(c.surface, blur ? 0.5 : 0.98),
        borderWidth: 1,
        borderColor: alpha(c.foreground, 0.12),
        borderTopColor: alpha(c.foreground, 0.3),
        boxShadow: `0 12px 40px rgba(0, 0, 0, ${mode === 'dark' ? 0.4 : 0.1})`,
      },
      style,
    ]}>
      {blur && <BlurView pointerEvents="none" blurTarget={target ?? undefined} blurMethod="dimezisBlurViewSdk31Plus" intensity={48} tint={mode === 'dark' ? 'systemThinMaterialDark' : 'systemThinMaterialLight'} style={StyleSheet.absoluteFill} />}
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: alpha(c.surface, blur ? 0.35 : 0), borderTopWidth: 1, borderTopColor: alpha(c.foreground, 0.12) }]} />
      {children}
    </View>
  );
}
