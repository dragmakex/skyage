import { TextInput, View, type TextInputProps } from 'react-native';
import { Txt, useTheme } from './theme';
import { font, radius } from './tokens';

export function Field({ label, mono, style, ...input }: TextInputProps & { label: string; mono?: boolean }) {
  const { c } = useTheme();
  return (
    <View style={{ gap: 6 }}>
      <Txt size="caption" muted>{label}</Txt>
      <TextInput
        accessibilityLabel={label}
        selectionColor={c.flight}
        placeholderTextColor={c.mutedForeground}
        {...input}
        style={[
          {
            height: 44,
            borderWidth: 1,
            borderColor: c.border,
            borderRadius: radius.control,
            paddingHorizontal: 12,
            color: c.foreground,
            fontFamily: mono ? font.mono : font.sans,
            fontSize: 16,
            backgroundColor: c.surface,
          },
          style,
        ]}
      />
    </View>
  );
}
