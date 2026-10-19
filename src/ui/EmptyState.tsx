import type { ReactNode } from 'react';
import { View } from 'react-native';
import { Txt } from './theme';

export function EmptyState({ title, body, action }: { title: string; body: string; action?: ReactNode }) {
  return (
    <View style={{ padding: 32, alignItems: 'center', gap: 8 }}>
      <Txt size="title" weight="semi">{title}</Txt>
      <Txt muted style={{ textAlign: 'center', maxWidth: 280 }}>{body}</Txt>
      {action ? <View style={{ marginTop: 8 }}>{action}</View> : null}
    </View>
  );
}
