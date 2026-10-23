import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import * as SystemUI from 'expo-system-ui';
import { Appearance, Platform, Text, type TextProps, type TextStyle } from 'react-native';
import { useSettings } from '../store';
import { font, palette, typeScale, type Colors, type Mode, type Role } from './tokens';

const Ctx = createContext<{ c: Colors; mode: Mode; nativeBackgroundReady: boolean }>({ c: palette.dark, mode: 'dark', nativeBackgroundReady: false });

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { theme } = useSettings();
  const mode: Mode = theme === 'light' ? 'light' : 'dark';
  const [nativeMode, setNativeMode] = useState<Mode>();
  useEffect(() => {
    if (Platform.OS !== 'android') return;
    let cancelled = false;
    // Android's blur library samples decorView.background, not the React screen's background.
    SystemUI.setBackgroundColorAsync(palette[mode].background)
      .then(() => { if (!cancelled) setNativeMode(mode); })
      .catch(() => { console.warn('Could not update the native window background; using glass without native blur.'); });
    return () => { cancelled = true; };
  }, [mode]);
  useEffect(() => {
    Appearance.setColorScheme(mode);
    return () => Appearance.setColorScheme('unspecified');
  }, [mode]);
  return <Ctx.Provider value={{ c: palette[mode], mode, nativeBackgroundReady: Platform.OS !== 'android' || nativeMode === mode }}>{children}</Ctx.Provider>;
}

export const useTheme = () => useContext(Ctx);

type TxtProps = TextProps & {
  size?: Role;
  mono?: boolean;
  weight?: 'regular' | 'medium' | 'semi';
  muted?: boolean;
  color?: string;
};

export function Txt({ size = 'body', mono, weight = 'regular', muted, color, style, ...rest }: TxtProps) {
  const { c } = useTheme();
  const family = mono
    ? weight === 'regular' ? font.mono : font.monoSemi
    : weight === 'regular' ? font.sans : weight === 'medium' ? font.sansMedium : font.sansSemi;
  const s: TextStyle = {
    fontFamily: family,
    color: color ?? (muted ? c.mutedForeground : c.foreground),
    ...typeScale[size],
    ...(mono ? { fontVariant: ['tabular-nums'] as TextStyle['fontVariant'] } : {}),
  };
  return <Text {...rest} style={[s, style]} />;
}
