import { CaretRight } from 'phosphor-react-native';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { Glass } from './Glass';
import { Txt, useTheme } from './theme';
import { radius } from './tokens';

export function OptionRow({ icon, title, description, onPress }: { icon: ReactNode; title: string; description: string; onPress: () => void }) {
  const { c } = useTheme();
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={title} style={({ pressed }) => ({ opacity: pressed ? 0.85 : 1, transform: [{ scale: pressed ? 0.98 : 1 }] })}>
      <Glass interactive tone="clear" style={{ flexDirection: 'row', alignItems: 'center', gap: 14, padding: 12, borderRadius: radius.control }}>
        <View style={{ width: 40, height: 40, borderRadius: radius.control, backgroundColor: c.muted, alignItems: 'center', justifyContent: 'center' }}>
          {icon}
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Txt weight="medium">{title}</Txt>
          <Txt size="caption" muted>{description}</Txt>
        </View>
        <CaretRight size={16} color={c.foreground} />
      </Glass>
    </Pressable>
  );
}
