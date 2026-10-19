import { createAuthClient } from 'better-auth/react';
import { emailOTPClient } from 'better-auth/client/plugins';
import { expoClient } from '@better-auth/expo/client';
import * as SecureStore from 'expo-secure-store';

const configured = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, '');
export const API_URL = configured || null;
if (API_URL) {
  const url = new URL(API_URL);
  if (url.protocol !== 'https:' && !['localhost', '127.0.0.1', '10.0.2.2'].includes(url.hostname)) throw new Error('Cloud sync requires an HTTPS server.');
}

export const authClient = createAuthClient({
  baseURL: API_URL ?? 'http://localhost:3000',
  plugins: [expoClient({ scheme: 'skyage', storagePrefix: 'skyage', storage: SecureStore }), emailOTPClient()],
});
