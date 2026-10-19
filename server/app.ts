import type { Database } from 'bun:sqlite';
import { betterAuth } from 'better-auth';
import { expo } from '@better-auth/expo';
import { emailOTP } from 'better-auth/plugins';
import { parseSyncRequest } from '../src/domain/sync';
import { sql } from './database';
import { CloudFlights } from './flights';

export type ServerOptions = {
  baseURL: string;
  secret: string;
  sendCode: (email: string, code: string) => Promise<void>;
};

export function createApp(db: Database, options: ServerOptions) {
  const auth = betterAuth({
    database: db,
    baseURL: options.baseURL,
    secret: options.secret,
    trustedOrigins: ['skyage://'],
    advanced: { ipAddress: { ipAddressHeaders: ['x-skyage-ip'] } },
    rateLimit: { enabled: true, storage: 'database', window: 60, max: 30 },
    user: { deleteUser: { enabled: true } },
    plugins: [expo(), emailOTP({
      expiresIn: 300,
      allowedAttempts: 3,
      storeOTP: 'hashed',
      rateLimit: { window: 60, max: 3 },
      sendVerificationOTP: async ({ email, otp }) => { await options.sendCode(email, otp); },
    })],
  });
  const flights = new CloudFlights(sql(db));
  const counts = new Map<string, number>();
  let minute = 0;

  async function handle(request: Request, ip: string): Promise<Response> {
    const headers = new Headers(request.headers);
    headers.set('x-skyage-ip', ip); // Never trust client-supplied forwarding headers.
    request = new Request(request, { headers });
    const path = new URL(request.url).pathname;
    if (path === '/health' && request.method === 'GET') return Response.json({ ok: true });
    if (path.startsWith('/api/auth/')) return auth.handler(request);
    if (path !== '/api/sync' || request.method !== 'POST') return Response.json({ error: 'Not found.' }, { status: 404 });
    const session = await auth.api.getSession({ headers: request.headers });
    if (!session) return Response.json({ error: 'Sign in to sync.' }, { status: 401 });
    if (request.headers.get('x-skyage-account') !== session.user.id) return Response.json({ error: 'Account changed. Sign in again.' }, { status: 401 });
    const now = Math.floor(Date.now() / 60_000);
    if (now !== minute) { counts.clear(); minute = now; }
    const count = (counts.get(session.user.id) ?? 0) + 1;
    counts.set(session.user.id, count);
    if (count > 60) return Response.json({ error: 'Too many sync requests.' }, { status: 429, headers: { 'Retry-After': '60' } });
    if (!request.headers.get('content-type')?.startsWith('application/json')) return Response.json({ error: 'Expected JSON.' }, { status: 415 });
    let body;
    try { body = parseSyncRequest(await request.json()); }
    catch { return Response.json({ error: 'Invalid sync request.' }, { status: 400 }); }
    try { return Response.json(flights.sync(session.user.id, body), { headers: { 'Cache-Control': 'no-store' } }); }
    catch { return Response.json({ error: 'Sync could not be committed. Please retry.' }, { status: 409 }); }
  }
  return { auth, handle };
}
