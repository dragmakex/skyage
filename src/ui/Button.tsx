import type { ReactNode } from 'react';
import { Pressable, type StyleProp, type ViewStyle } from 'react-native';
import { Glass } from './Glass';
import { Txt, useTheme } from './theme';
import { radius } from './tokens';

type Props = {
  onPress?: () => void;
  variant?: 'primary' | 'secondary' | 'ghost';
  pill?: boolean;
  selected?: boolean;
  icon?: ReactNode;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
} & ({ title: string; accessibilityLabel?: string } | { title?: undefined; accessibilityLabel: string; icon: ReactNode });

export function Button({ title, accessibilityLabel, onPress, variant = 'primary', pill = true, selected, icon, disabled, style }: Props) {
  const { c } = useTheme();
  const iconOnly = title === undefined;
  const fg = variant === 'primary' ? c.background : c.foreground;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityState={{ disabled: !!disabled, selected }}
      style={({ pressed }) => [
        { minHeight: 44, minWidth: 44, borderRadius: pill || iconOnly ? radius.pill : radius.control, overflow: 'hidden', ...(iconOnly ? { width: 44, height: 44 } : {}), opacity: disabled ? 0.45 : pressed ? 0.85 : 1, transform: [{ scale: pressed ? 0.97 : 1 }] },
        style,
      ]}
    >
      <Glass
        interactive={!disabled}
        tone={selected ? 'selected' : variant === 'primary' ? 'primary' : variant === 'ghost' ? 'clear' : 'neutral'}
        style={{ flex: 1, minHeight: 44, paddingHorizontal: iconOnly ? 0 : 18, borderRadius: pill || iconOnly ? radius.pill : radius.control, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 }}
      >
        {icon}
        {title !== undefined && <Txt numberOfLines={1} weight={selected ? 'semi' : 'medium'} color={fg} style={{ flexShrink: 1 }}>{title}</Txt>}
      </Glass>
    </Pressable>
  );
}
