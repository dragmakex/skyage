import { Pressable } from 'react-native';
import { Txt, useTheme } from './theme';
import { alpha, radius } from './tokens';

export function Chip({ label, selected, onPress }: { label: string; selected?: boolean; onPress?: () => void }) {
  const { c } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      hitSlop={4}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={({ pressed }) => ({
        height: 32,
        paddingHorizontal: 14,
        borderRadius: radius.pill,
        justifyContent: 'center',
        backgroundColor: selected ? c.muted : 'transparent',
        borderWidth: 1,
        borderColor: selected ? alpha(c.foreground, 0.14) : 'transparent',
        opacity: pressed ? 0.8 : 1,
      })}
    >
      <Txt size="caption" weight="medium" color={selected ? c.foreground : c.mutedForeground}>{label}</Txt>
    </Pressable>
  );
}
