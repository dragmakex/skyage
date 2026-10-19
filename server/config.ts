import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { openDatabase } from './database';
import { createApp } from './app';

export function configuredApp() {
  const baseURL = process.env.SKYAGE_URL;
  const secret = process.env.BETTER_AUTH_SECRET;
  const emailKey = process.env.RESEND_API_KEY;
  const from = process.env.EMAIL_FROM;
  if (!baseURL || !secret || secret.length < 32 || !emailKey || !from) throw new Error('Set SKYAGE_URL, BETTER_AUTH_SECRET (32+ characters), RESEND_API_KEY and EMAIL_FROM.');
  const url = new URL(baseURL);
  if (url.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(url.hostname)) throw new Error('The public server URL must use HTTPS.');
  const path = process.env.DATABASE_PATH ?? './server/data/skyage.sqlite';
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  const db = openDatabase(path);
  const app = createApp(db, {
    baseURL,
    secret,
    async sendCode(email, code) {
      const response = await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: { Authorization: `Bearer ${emailKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ from, to: [email], subject: 'Your Skyage sign-in code', text: `Your Skyage code is ${code}. It expires in 5 minutes. If you did not request it, ignore this email.` }),
        signal: AbortSignal.timeout(15_000),
      });
      if (!response.ok) throw new Error('Could not deliver the sign-in email.');
    },
  });
  return { ...app, db };
}
