# Intake sync server

Optional. Intake works fully on one phone without it. Run this if you want a
phone and a tablet (or a phone and a car head unit) to share the same log.

It is one Python file using only the standard library and SQLite. Your data
stays on a machine you own; the server holds the latest version of each row
and nothing else.

## How sync works

- Every row Intake stores has an id, an `updatedAt` time and, once deleted, a
  `deletedAt` time. Nothing is ever hard-deleted, so a deletion syncs like any
  other change.
- The newest `updatedAt` wins, row by row. Equal times are broken by a hash of
  the row's content, so every device and the server pick the same winner
  whatever order changes arrive in. That makes the data set a state-based CRDT
  (a last-writer-wins map): devices can be offline for weeks and still converge.
- Timestamps come from a hybrid logical clock on each device: wall time that
  never runs backwards and never falls behind a time already seen from another
  device (capped at five minutes ahead, so one badly set clock cannot drag the
  others into the future).
- When both devices edited the same row between syncs, the newer edit wins and
  the other version is kept on the device under **Backup & sync → Conflicts**,
  where you can restore it. Nothing is silently thrown away.
- Health Connect weigh-ins get ids from Health Connect's own record ids, and
  daily health summaries are keyed by day, so two devices reading the same
  scale or watch do not create duplicates.

## Run it

### Docker (recommended)

```sh
cd server
docker compose up -d
docker compose logs intake-sync      # shows the first pairing code
```

Data lives in `server/data/intake-sync.db`. Back that file up like any other.

### Without Docker

```sh
python3 server/intake_sync.py serve          # 0.0.0.0:8787, ./intake-sync.db
INTAKE_SYNC_DB=/srv/intake/sync.db INTAKE_SYNC_PORT=8787 python3 server/intake_sync.py serve
```

## Give it an https address

Intake only syncs over `https://` (plain `http://` is allowed for an address
that is plainly on your home network, such as `http://192.168.1.20:8787`).

**Cloudflare tunnel** - no port opened on your router:

1. Cloudflare dashboard → Zero Trust → Networks → Tunnels → Create a tunnel
   (type *Cloudflared*). Copy its token.
2. Add a public hostname, e.g. `intake-sync.example.com`, service
   `http://intake-sync:8787`.
3. `echo TUNNEL_TOKEN=<token> > server/.env` and
   `docker compose --profile tunnel up -d`.
4. Optional but sensible: put a Cloudflare Access policy on the hostname so only
   your devices' traffic reaches it at all. Intake's own device tokens still
   apply either way.

Any reverse proxy with a certificate (Caddy, nginx, Traefik) works the same way.

## Pair devices

1. On the server: `docker compose exec intake-sync python3 intake_sync.py pair`
   (or read the code from the logs on first start). Codes look like `QCBQ-8FQS`,
   work once, and expire after ten minutes.
2. In Intake: More → Backup & sync → Device sync → enter the server address and
   the code → **Pair this device**.
3. For each further device, either repeat step 1, or on an already paired
   device tap **Add another device** to get a code.

To remove a lost device: Backup & sync → its row → Remove, or on the server
`python3 intake_sync.py revoke <device-id>` (`devices` lists ids). Its token
stops working immediately.

## Security notes

- Device tokens are 256-bit random values; the server stores only their SHA-256.
- Ten wrong pairing codes in ten minutes lock pairing for ten minutes.
- The server logs requests without headers or bodies.
- Data on the server is plaintext SQLite, so the (future) MCP server can query
  it. Keep the machine and the `data/` folder private, and use the encrypted
  nightly backup for off-site copies.

## API

All JSON. Every endpoint except `/v1/health` and `/v1/pair` needs
`Authorization: Bearer <token>`.

| Method | Path | Body / query | Returns |
| --- | --- | --- | --- |
| GET | `/v1/health` | | `{ok, version}` |
| POST | `/v1/pair` | `{code, deviceName}` | `{deviceId, token}` |
| POST | `/v1/pairing-codes` | | `{code, expiresAt}` |
| GET | `/v1/devices` | | `{devices: [{id, name, createdAt, lastSeenAt, current}]}` |
| DELETE | `/v1/devices/{id}` | | `{revoked}` |
| POST | `/v1/push` | `{rows: [{table, id, updatedAt, deletedAt, hash, payload}]}` | `{accepted, ignored, seq}` |
| GET | `/v1/pull` | `?since=<seq>&limit=<n≤1000>` | `{rows: [... , seq, originDevice], next, more}` |

## Tests

```sh
cd server && python3 -m unittest test_intake_sync.py
```
