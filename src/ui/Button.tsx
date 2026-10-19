import type { ReactNode } from 'react';
import { Pressable, type StyleProp, type ViewStyle } from 'react-native';
import { Txt, useTheme } from './theme';
import { radius } from './tokens';

type Props = {
  title: string;
  onPress?: () => void;
  variant?: 'primary' | 'secondary' | 'ghost';
  pill?: boolean;
  icon?: ReactNode;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
};

export function Button({ title, onPress, variant = 'primary', pill, icon, disabled, style }: Props) {
  const { c } = useTheme();
  const fg = variant === 'primary' ? c.background : c.foreground;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      style={({ pressed }) => [
        {
          height: 44,
          paddingHorizontal: 18,
          borderRadius: pill ? radius.pill : radius.control,
          backgroundColor: variant === 'primary' ? c.foreground : variant === 'secondary' ? c.surface : 'transparent',
          borderWidth: variant === 'secondary' ? 1 : 0,
          borderColor: c.border,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
          opacity: disabled ? 0.5 : pressed ? 0.85 : 1,
          transform: [{ scale: pressed ? 0.98 : 1 }],
        },
        style,
      ]}
    >
      {icon}
      <Txt weight="medium" color={fg}>{title}</Txt>
    </Pressable>
  );
}
