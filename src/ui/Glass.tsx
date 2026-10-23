import { BlurView } from 'expo-blur';
import { GlassView, isGlassEffectAPIAvailable, isLiquidGlassAvailable } from 'expo-glass-effect';
import { createContext, useContext, useEffect, useId, useState, type ReactNode, type RefObject } from 'react';
import { AccessibilityInfo, Platform, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Svg, { Defs, LinearGradient, Path, Stop } from 'react-native-svg';
import { glassOutline } from './glassShape';
import { useTheme } from './theme';
import { alpha } from './tokens';

export const BlurTargetContext = createContext<RefObject<View | null> | null>(null);
type Tone = 'neutral' | 'primary' | 'selected' | 'clear';

// Native Liquid Glass on supported iOS; Android uses a clipped frosted material with a curved specular edge.
export function Glass({ style, children, tone = 'neutral', interactive = false }: {
  style?: StyleProp<ViewStyle>; children?: ReactNode; tone?: Tone; interactive?: boolean;
}) {
  const { c, mode, nativeBackgroundReady } = useTheme();
  const target = useContext(BlurTargetContext);
  const [reduceTransparency, setReduceTransparency] = useState(Platform.OS === 'ios');
  const [size, setSize] = useState({ width: 0, height: 0 });
  const id = useId().replace(/:/g, '');
  useEffect(() => {
    if (Platform.OS !== 'ios') return;
    let cancelled = false;
    AccessibilityInfo.isReduceTransparencyEnabled().then((value) => { if (!cancelled) setReduceTransparency(value); }).catch(() => { if (!cancelled) setReduceTransparency(true); });
    const listener = AccessibilityInfo.addEventListener('reduceTransparencyChanged', setReduceTransparency);
    return () => { cancelled = true; listener.remove(); };
  }, []);
  const nativeGlass = Platform.OS === 'ios' && !reduceTransparency && isLiquidGlassAvailable() && isGlassEffectAPIAvailable();
  // Android RenderEffect leaks square bounds on small transformed controls; panels keep native backdrop blur.
  const blur = !nativeGlass && !reduceTransparency && (Platform.OS === 'ios' || (!interactive && nativeBackgroundReady && target !== null && Number(Platform.Version) >= 31));
  const flat = StyleSheet.flatten(style) ?? {};
  const radius = typeof flat.borderRadius === 'number' ? flat.borderRadius : 0;
  const corners = [flat.borderTopLeftRadius, flat.borderTopRightRadius, flat.borderBottomRightRadius, flat.borderBottomLeftRadius]
    .map((value) => typeof value === 'number' ? value : radius) as [number, number, number, number];
  const limit = size.width > 0 && size.height > 0 ? Math.min(size.width, size.height) / 2 : Infinity;
  const [tl, tr, br, bl] = corners.map((corner) => Math.min(corner, limit));
  const rounding = { borderTopLeftRadius: tl, borderTopRightRadius: tr, borderBottomRightRadius: br, borderBottomLeftRadius: bl };
  const tint = tone === 'primary' ? alpha(c.foreground, 0.9)
    : tone === 'selected' ? alpha(c.muted, 0.88)
    : alpha(c.surface, blur || nativeGlass ? tone === 'clear' ? 0.3 : 0.62 : 0.94);
  const outline = glassOutline(size.width, size.height, corners);
  return (
    <View
      onLayout={({ nativeEvent: { layout } }) => setSize({ width: layout.width, height: layout.height })}
      style={[{ overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth, borderColor: 'transparent', backgroundColor: reduceTransparency ? tone === 'primary' ? c.foreground : c.surface : alpha(c.surface, 0.01), boxShadow: `0 8px 24px rgba(0, 0, 0, ${mode === 'dark' ? 0.18 : 0.07})` }, style, rounding]}
    >
      {nativeGlass ? (
        <GlassView pointerEvents="none" glassEffectStyle={tone === 'clear' ? 'clear' : 'regular'} colorScheme={mode} tintColor={tint} isInteractive={interactive} style={[StyleSheet.absoluteFill, rounding]} />
      ) : (
        <>
          {blur && <BlurView pointerEvents="none" blurTarget={target ?? undefined} blurMethod="dimezisBlurViewSdk31Plus" intensity={36} tint={mode === 'dark' ? 'dark' : 'light'} style={[StyleSheet.absoluteFill, rounding]} />}
          <View pointerEvents="none" style={[StyleSheet.absoluteFill, rounding, { backgroundColor: reduceTransparency ? tone === 'primary' ? c.foreground : c.surface : tint }]} />
          {!!outline && !reduceTransparency && (
            <Svg pointerEvents="none" width={size.width} height={size.height} style={StyleSheet.absoluteFill}>
              <Defs>
                <LinearGradient id={`${id}-wash`} x1="0%" y1="0%" x2="100%" y2="100%">
                  <Stop offset="0" stopColor={c.glassHighlight} stopOpacity={mode === 'dark' ? 0.1 : 0.35} />
                  <Stop offset="0.48" stopColor={c.glassHighlight} stopOpacity="0" />
                  <Stop offset="1" stopColor={c.glassHighlight} stopOpacity="0.04" />
                </LinearGradient>
                <LinearGradient id={`${id}-edge`} x1="0%" y1="0%" x2="100%" y2="100%">
                  <Stop offset="0" stopColor={c.glassHighlight} stopOpacity={mode === 'dark' ? 0.4 : 0.85} />
                  <Stop offset="0.45" stopColor={c.foreground} stopOpacity="0.08" />
                  <Stop offset="1" stopColor={c.glassHighlight} stopOpacity={mode === 'dark' ? 0.14 : 0.5} />
                </LinearGradient>
              </Defs>
              <Path d={outline} fill={`url(#${id}-wash)`} stroke={`url(#${id}-edge)`} strokeWidth={1} />
            </Svg>
          )}
        </>
      )}
      {children}
    </View>
  );
}
