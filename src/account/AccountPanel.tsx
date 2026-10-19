import { useState } from 'react';
import { Alert, View } from 'react-native';
import { activateAccount, forgetAccount, resolveConflict, uploadGuestFlights, useAccountData } from '../store';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { Txt } from '../ui/theme';
import { API_URL, authClient } from './auth';
import { resetSyncStatus, syncNow, useSyncStatus } from './sync';

export function AccountPanel() {
  const { account, pending, conflicts, guestCount } = useAccountData();
  const sync = useSyncStatus();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [sentTo, setSentTo] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [reauthenticate, setReauthenticate] = useState(false);
  const locked = busy || sync.busy;

  async function run(work: () => Promise<void>) {
    setBusy(true); setError('');
    try { await work(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Please try again.'); }
    finally { setBusy(false); }
  }
  async function sendCode() {
    const address = (account?.email ?? email).trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)) throw new Error('Enter a valid email address.');
    const result = await authClient.emailOtp.sendVerificationOtp({ email: address, type: 'sign-in' });
    if (result.error) throw new Error(result.error.message ?? 'Could not send a code.');
    setSentTo(address); setCode('');
  }
  async function signIn() {
    if (!sentTo || !/^\d{6}$/.test(code)) throw new Error('Enter the six-digit code from your email.');
    if (account && sentTo !== account.email.toLowerCase()) throw new Error('Sign out before using a different account.');
    const result = await authClient.signIn.emailOtp({ email: sentTo, otp: code });
    if (result.error) throw new Error(result.error.message ?? 'Could not sign in. Request a new code.');
    if (!result.data?.user) throw new Error('Could not confirm your account.');
    activateAccount({ id: result.data.user.id, email: result.data.user.email });
    setSentTo(undefined); setCode(''); setReauthenticate(false);
    await syncNow();
  }
  function signOut() {
    Alert.alert('Sign out?', `${pending ? `${pending} changes have not synced. ` : ''}Account flights stay on this phone, hidden while signed out. Sign in to this account again to resume sync.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Sign out', onPress: () => { void run(async () => {
        const result = await authClient.signOut();
        if (result.error) throw new Error('Could not revoke the session. Connect to the internet and retry.');
        activateAccount(null); resetSyncStatus(); setSentTo(undefined); setCode(''); setEmail('');
      }); } },
    ]);
  }
  function deleteAccount() {
    Alert.alert('Delete account and cloud flights?', 'This permanently deletes your online account, flights, sessions and this phone’s downloaded account data. Unsynced account changes will also be lost. Export any flights you want to keep first. Separate guest flights are not deleted.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete account', style: 'destructive', onPress: () => { void run(async () => {
        const result = await authClient.deleteUser();
        if (result.error) {
          setReauthenticate(true);
          throw new Error('Could not delete your account. Sign in again with a new code below, then retry.');
        }
        forgetAccount(); resetSyncStatus(); setSentTo(undefined); setEmail(''); setCode('');
      }); } },
    ]);
  }
  function importGuest() {
    Alert.alert('Upload local flights?', `Move ${guestCount} guest flights into this account and sync them. Matching date, route and flight-number entries already in this account are skipped.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Upload', onPress: () => { void run(async () => {
        if (!await syncNow()) throw new Error('Connect and sync this account before uploading guest flights.');
        uploadGuestFlights(); await syncNow();
      }); } },
    ]);
  }

  if (!API_URL) return <Txt muted>Flights are saved on this device. Optional accounts will be available when a cloud server is configured for this build.</Txt>;
  return (
    <View style={{ gap: 12 }}>
      {account ? (
        <>
          <Txt>{account.email}</Txt>
          <Txt size="caption" muted>{conflicts.length ? `${conflicts.length} conflicts need your choice below. ` : ''}{pending ? `${pending} changes pending. ` : ''}{sync.message}</Txt>
          {sync.lastSynced && <Txt size="caption" muted>Last synced: {new Date(sync.lastSynced).toLocaleString()}</Txt>}
          <Button title={sync.busy ? 'Syncing…' : 'Sync now'} onPress={() => { void syncNow(); }} disabled={locked} variant="secondary" />
          {guestCount > 0 && <Button title={`Upload ${guestCount} local flights`} onPress={importGuest} disabled={locked} variant="secondary" />}
          {conflicts.map((conflict) => {
            const f = conflict.local ?? conflict.cloud.flight;
            return (
              <View key={conflict.id} style={{ gap: 8 }}>
                <Txt weight="medium">{f ? `${f.from} → ${f.to}, ${f.date}` : 'Deleted flight'}</Txt>
                <Txt size="caption" muted>Device: {conflict.local ? JSON.stringify(conflict.local) : 'Deleted'}{'\n'}Cloud: {conflict.cloud.flight ? JSON.stringify(conflict.cloud.flight) : 'Deleted'}</Txt>
                <View style={{ flexDirection: 'row', gap: 8 }}>
                  <Button title="Use device" variant="secondary" disabled={locked} onPress={() => resolveConflict(conflict.id, 'local')} />
                  <Button title="Use cloud" variant="secondary" disabled={locked} onPress={() => resolveConflict(conflict.id, 'cloud')} />
                </View>
              </View>
            );
          })}
          <Button title="Sign out" variant="ghost" disabled={locked} onPress={signOut} />
          <Button title="Delete account" variant="ghost" disabled={locked} onPress={deleteAccount} />
        </>
      ) : <Txt muted>No account needed. Sign in to store your flights online and sync between phones. Existing local flights are uploaded only with your permission.</Txt>}
      {(!account || sync.needsLogin || reauthenticate) && (
        <>
          <Field label="Email" value={account?.email ?? email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" keyboardType="email-address" editable={!account && !locked && !sentTo} />
          {sentTo ? (
            <>
              <Txt size="caption" muted>We sent a code to {sentTo}. It expires in five minutes.</Txt>
              <Field label="Sign-in code" value={code} onChangeText={setCode} keyboardType="number-pad" autoComplete="one-time-code" maxLength={6} editable={!locked} />
              <Button title={busy ? 'Please wait…' : 'Verify code'} disabled={locked} onPress={() => { void run(signIn); }} />
              <Button title="Request a new code / change email" variant="ghost" disabled={locked} onPress={() => { setSentTo(undefined); setCode(''); }} />
            </>
          ) : <Button title={busy ? 'Sending…' : 'Email me a sign-in code'} disabled={locked} onPress={() => { void run(sendCode); }} />}
        </>
      )}
      {!!error && <Txt accessibilityRole="alert">{error}</Txt>}
    </View>
  );
}
