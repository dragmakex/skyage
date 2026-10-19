# Optional Skyage accounts and sync

One Bun process and one SQLite file on local disk. No PostgreSQL, Redis, hosted auth subscription, or public database port. Better Auth stores users, sessions and hashed email codes in the same server database as cloud flights. Resend delivers the emails; replace only `sendCode` in `config.ts` to use another provider.

## Run and configure

Install Bun 1.3+ and use `bun install --frozen-lockfile` from the repository root. Run as a dedicated, unprivileged `skyage` user. Protect the state directory and environment file; set `umask 077`. The database contains personal travel history. Do not put it on NFS or an ephemeral filesystem.

Copy `server/.env.example` to a private file outside the repository, such as `/etc/skyage/api.env`. Configure:

- `SKYAGE_URL`: the public HTTPS origin, e.g. `https://api.example.com` (no `/api` suffix).
- `BETTER_AUTH_SECRET`: a persistent, random secret of at least 32 characters. Generate one with `openssl rand -hex 32`. Do not change it on each restart.
- `RESEND_API_KEY` and `EMAIL_FROM`: an API key and sender on a verified Resend domain. Configure the provider's SPF/DKIM records. There is no fake-email or log-code fallback in production.
- `DATABASE_PATH`: an absolute path on persistent local disk, writable only by the service user.
- `PORT`: defaults to 3000. The API always binds to `127.0.0.1`.

Create a consistent backup before upgrades, stop the service, then run:

```sh
bun --env-file=/etc/skyage/api.env run server:migrate
bun --env-file=/etc/skyage/api.env run server:start
```

`server:migrate` applies Better Auth's schema migrations. The application tables are created idempotently. The API checks for pending auth schema changes at startup and refuses to listen until they are resolved. On a brand-new empty database, Better Auth may log an initial schema-mismatch diagnostic before its migration creates the tables. A failed migration must block deployment; do not start the service after an error. On subsequent deployments, review upstream auth migrations before running them.

Use a systemd service with `User=skyage`, the repository as `WorkingDirectory`, `EnvironmentFile=/etc/skyage/api.env`, an absolute Bun executable path, `ExecStart=.../bun run server:start`, `Restart=on-failure`, `UMask=0077`, and `NoNewPrivileges=true`. Run migrations separately, not on every request.

## HTTPS reverse proxy

Reuse an existing reverse proxy if available. Example Caddy configuration, with your real domain substituted:

```caddyfile
api.example.com {
    request_body {
        max_size 256KB
    }
    reverse_proxy 127.0.0.1:3000 {
        header_up X-Skyage-IP {remote_host}
    }
}
```

The proxy **must overwrite** `X-Skyage-IP`; Better Auth uses it for rate limiting. The application passes it as its only trusted IP header. Do not expose the origin port publicly or forward a user-supplied header unchanged. If another trusted proxy/CDN is in front of Caddy, configure the real-client-IP chain deliberately before launch.

Check `GET /health` through HTTPS. Run only one API instance against this database. Bun limits request bodies to 256 KiB. Auth has persistent per-IP rate limits, the OTP plugin limits requests to 3 per minute, codes expire after five minutes with three verification attempts, and sync is limited to 60 requests per account per minute. These controls are not a substitute for monitoring email-provider spend and abuse.

## Mobile configuration

Set `EXPO_PUBLIC_API_URL=https://api.example.com` in the app's build environment. It is a public origin, not a secret. Leave it blank to keep the app local-only. Never put database credentials, the auth secret or the email-provider key in an `EXPO_PUBLIC_*` variable.

Generate native projects from the updated `app.json` so the SecureStore plugin applies its Android backup exclusions (for an existing generated Android folder, run `bunx expo prebuild --platform android`). Rebuild native development clients with `bun run android` after installing the new SecureStore, Crypto and Network modules. Restart Metro after changing the API URL; release builds need a new bundle/build for a different URL. The existing `skyage://` app scheme is the trusted native origin. Browser/web login is not implemented.

For an Android emulator, `http://10.0.2.2:3000` reaches the host's loopback API. `http://127.0.0.1:3000` also works after `adb reverse tcp:3000 tcp:3000`. HTTP is accepted only for these development loopback addresses. Use HTTPS for a real remote server.

## Storage and sync contract

- Guests use SQLite on the phone with no registration or server calls. The app's old KV flight blob migrates in one transaction; it is removed only after that transaction succeeds. Invalid old records abort the migration instead of being discarded. Existing IDs are preserved, and new flights/operations use random UUIDs.
- Every account has a separate local flight namespace. The last selected account remains available offline, including after its online session expires. Tokens/cookies live in OS SecureStore; flight rows remain in the app sandbox, not an encrypted flight database. There is no end-to-end encryption: the server operator can access cloud flight data.
- Signing in never automatically uploads guest flights. The explicit Upload action first downloads cloud state, then moves guest rows into that account in a transaction. It skips entries matching date, route and flight number; ID collisions get a new ID. Signing out reveals the remaining guest namespace, not another account's cache.
- Local account writes and their pending-operation IDs commit together. The UI continues to read local SQLite. Sync runs on local changes, foregrounding, network reconnection, and once a minute while active. Background execution is not promised. Errors leave queued writes intact.
- `POST /api/sync` accepts up to 100 mutations. Ownership always comes from the validated session; `X-Skyage-Account` is an additional mismatch guard, not authorization. Cross-account requests are rejected before any mutation.
- The server commits a batch and idempotency receipts in one transaction. Repeating an operation ID with the same body is safe; reusing it for different data fails. Per-flight revisions detect concurrent edits. Delete tombstones stop a stale phone from restoring deleted flights.
- Responses contain a full account snapshot. This deliberately favors personal flight histories over a cursor protocol. Keep tombstones and receipts indefinitely for now; expiring them requires a stale-client recovery protocol. Add pagination/delta sync when measured history sizes warrant it.
- Conflicts preserve the local version and expose both versions in Settings. Use device queues a new mutation against the latest known server revision; Use cloud explicitly accepts the server version. Further concurrent changes can conflict again instead of being silently lost.
- Sign-out requires a server connection so the session can be revoked. Downloaded account rows and pending changes remain on this phone, hidden from guest/other accounts; signing back in resumes them. Account deletion requires a fresh session, deletes live server data via foreign-key cascades, and clears that account's cache on the initiating phone. Other offline phones can still retain their own cached data; the server cannot remotely erase an offline device.
- Theme/units stay device-local. JSON export/import continues to work for the currently selected namespace. Export before deleting an account if a separate local copy is wanted.

## Backups and recovery

Sync is **not** a backup. Deletions also sync. Schedule consistent snapshots, encrypt off-server copies, restrict access, and set a documented retention policy (including how deleted-account data ages out of backups).

```sh
bun --env-file=/etc/skyage/api.env run server:backup /private/backups/skyage-YYYY-MM-DD.sqlite
```

The destination directory must exist and the filename must be new. The command uses SQLite `VACUUM INTO`, which takes a consistent snapshot while the database is live. Do not copy just the live `.sqlite` file while WAL writes are active. Send the snapshot to independent storage; this command does not upload it or schedule itself.

Test restore regularly: stop the API, retain the old database and its sidecars for recovery, replace the database with a snapshot in the private state directory, ensure no stale `-wal`/`-shm` sidecars remain at the replacement path, restore permissions, run `PRAGMA integrity_check`, then start the API and verify a test user's data. Restore the matching environment secret as needed. A rollback to an older server database can conflict with newer device state: preserve/export that local state, do not resolve conflicts blindly.

Live deletion removes auth/cloud rows, but SQLite can retain freed pages and backups can retain historical copies. If physical erasure is required, define retention, secure-delete/VACUUM and encrypted-storage procedures before launch; a live SQL DELETE alone is not a physical-erasure guarantee.

## Verification and launch gate

```sh
bun run typecheck
bun run test --runInBand
bun run test:server
```

The server check uses real SQLite and real Better Auth handlers with a captured email sender, never paid email delivery. It exercises migration rollback, namespace isolation, pending writes across restart, two-phone convergence, idempotency, concurrent edits/deletions, OTP hashing/replay/rate limits, session revocation, account deletion and a restored snapshot.

Before opening registration publicly, publish the privacy/retention policy and check real domain/TLS, email delivery and spam placement, account recovery via a fresh email code, offline/online native behavior on two physical phones, disk-space monitoring, backup scheduling and restore, and upstream auth security updates. Alert on sustained sync failures, failed email delivery, database/disk errors and abnormal mail volume. Avoid logging codes, cookies, raw request bodies, or flight details.
