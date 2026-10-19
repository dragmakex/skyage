import { configuredApp } from './config';

const app = configuredApp();
Bun.serve({
  hostname: '127.0.0.1',
  port: Number(process.env.PORT ?? 3000),
  maxRequestBodySize: 256 * 1024,
  idleTimeout: 30,
  async fetch(request, server) {
    try {
      // The reverse proxy must overwrite this header; the origin only listens on loopback.
      const ip = request.headers.get('x-skyage-ip') ?? server.requestIP(request)?.address ?? '127.0.0.1';
      return await app.handle(request, ip);
    } catch {
      console.error('Skyage request failed.');
      return Response.json({ error: 'Server unavailable. Please retry.' }, { status: 500 });
    }
  },
});
console.info('Skyage API listening on loopback.');
