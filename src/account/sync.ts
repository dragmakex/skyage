import { useEffect, useSyncExternalStore } from 'react';
import { AppState } from 'react-native';
import * as Network from 'expo-network';
import { parseSyncResponse } from '../domain/sync';
import { ownerFor } from '../storage/flights';
import { applySync, currentAccount, pendingMutations, subscribeToFlightChanges } from '../store';
import { API_URL, authClient } from './auth';

type SyncStatus = { busy: boolean; message: string; needsLogin: boolean; lastSynced: string | null };
let state: SyncStatus = { busy: false, message: 'Saved on this device.', needsLogin: false, lastSynced: null };
const listeners = new Set<() => void>();
const subscribe = (l: () => void) => { listeners.add(l); return () => { listeners.delete(l); }; };
const publish = (patch: Partial<SyncStatus>) => { state = { ...state, ...patch }; listeners.forEach((l) => l()); };
export const useSyncStatus = () => useSyncExternalStore(subscribe, () => state);
let running: Promise<boolean> | null = null;
export function resetSyncStatus() { publish({ busy: false, message: 'Saved on this device.', needsLogin: false, lastSynced: null }); }

export function syncNow(): Promise<boolean> {
  if (running) return running;
  const account = currentAccount();
  if (!API_URL || !account) return Promise.resolve(false);
  const owner = ownerFor(account);
  publish({ busy: true, message: 'Syncing…', needsLogin: false });
  running = (async () => {
    try {
      let more = true;
      while (more) {
        if (currentAccount()?.id !== account.id) return false;
        const sent = pendingMutations(owner);
        const cookie = await authClient.getCookie();
        const response = await fetch(`${API_URL}/api/sync`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Cookie: cookie, 'x-skyage-account': account.id },
          body: JSON.stringify({ mutations: sent }),
          signal: AbortSignal.timeout(20_000),
        });
        if (currentAccount()?.id !== account.id) return false;
        if (response.status === 401) {
          publish({ needsLogin: true, message: 'Sign in again. Your flights remain saved on this device.' });
          return false;
        }
        if (!response.ok) throw new Error('Cloud sync is unavailable. Your changes remain saved on this device.');
        applySync(owner, sent, parseSyncResponse(await response.json()));
        more = pendingMutations(owner).length > 0;
      }
      publish({ message: 'Synced.', lastSynced: new Date().toISOString() });
      return true;
    } catch (error) {
      publish({ message: error instanceof Error && error.message.startsWith('Cloud sync') ? error.message : 'Offline or server unavailable. Changes will retry while the app is open.' });
      return false;
    } finally {
      running = null;
      publish({ busy: false });
    }
  })();
  return running;
}

export function useSyncLifecycle() {
  useEffect(() => {
    if (!API_URL) return;
    let disposed = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const schedule = () => {
      clearTimeout(timer);
      timer = setTimeout(() => { if (!disposed && AppState.currentState === 'active') void syncNow(); }, 750);
    };
    const changes = subscribeToFlightChanges(() => {
      publish({ message: currentAccount() ? 'Sync pending. Saved on this device.' : 'Saved on this device.' });
      schedule();
    });
    const network = Network.addNetworkStateListener((s) => { if (s.isConnected) schedule(); });
    const app = AppState.addEventListener('change', (s) => { if (s === 'active') schedule(); });
    const interval = setInterval(schedule, 60_000);
    schedule();
    return () => { disposed = true; clearTimeout(timer); clearInterval(interval); changes(); network.remove(); app.remove(); };
  }, []);
}
