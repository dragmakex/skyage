import { CaretRight } from 'phosphor-react-native';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { Txt, useTheme } from './theme';
import { radius } from './tokens';

export function OptionRow({ icon, title, description, onPress }: { icon: ReactNode; title: string; description: string; onPress: () => void }) {
  const { c } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={({ pressed }) => ({
        flexDirection: 'row',
        alignItems: 'center',
        gap: 14,
        padding: 12,
        borderRadius: radius.control,
        borderWidth: 1,
        borderColor: c.border,
        backgroundColor: pressed ? c.muted : c.surface,
      })}
    >
      <View style={{ width: 40, height: 40, borderRadius: radius.control, backgroundColor: c.muted, alignItems: 'center', justifyContent: 'center' }}>
        {icon}
      </View>
      <View style={{ flex: 1, gap: 2 }}>
        <Txt weight="medium">{title}</Txt>
        <Txt size="caption" muted>{description}</Txt>
      </View>
      <CaretRight size={16} color={c.mutedForeground} />
    </Pressable>
  );
}
