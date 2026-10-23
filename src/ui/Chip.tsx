import { Button } from './Button';

export function Chip({ label, selected, onPress }: { label: string; selected?: boolean; onPress?: () => void }) {
  return <Button title={label} selected={selected} onPress={onPress} variant="secondary" pill />;
}
