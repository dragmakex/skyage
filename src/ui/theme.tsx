import { createContext, useContext, useEffect, type ReactNode } from 'react';
import { Appearance, Text, type TextProps, type TextStyle } from 'react-native';
import { useSettings } from '../store';
import { font, palette, typeScale, type Colors, type Mode, type Role } from './tokens';

const Ctx = createContext<{ c: Colors; mode: Mode }>({ c: palette.dark, mode: 'dark' });

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { theme } = useSettings();
  const mode: Mode = theme === 'light' ? 'light' : 'dark';
  useEffect(() => {
    Appearance.setColorScheme(mode);
    return () => Appearance.setColorScheme('unspecified');
  }, [mode]);
  return <Ctx.Provider value={{ c: palette[mode], mode }}>{children}</Ctx.Provider>;
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
