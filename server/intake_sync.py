#!/usr/bin/env python3
"""
Intake sync server (amendment §3).

A small, self-hosted relay that lets several Intake devices converge on the
same data. Standard library only: Python 3.10+ and SQLite, nothing to pip
install, so it runs the same in Docker, on a NAS, or on a Raspberry Pi.

Model
-----
Every Intake row is (table, id, updatedAt, deletedAt, payload). The server
keeps the latest version of each row by last-writer-wins on updatedAt, with
ties broken by the larger content hash. Devices apply the same rule, so the
order rows arrive in never changes the result - which is what makes this a
state-based CRDT (an LWW-element map) rather than a log to be replayed.
Deletions are rows with deletedAt set; nothing is ever removed.

Each accepted version gets the next sequence number. A device pulls
"everything after seq N" and remembers the last N it saw, so reconnecting
after a week offline is one paged pull.

Auth
----
Devices hold a random bearer token; the server stores only its SHA-256. A
device is added with a one-time pairing code (10 minutes, single use) that
either the server prints (first start, or `pair`) or an already paired
device asks for. Put the server behind HTTPS - a Cloudflare tunnel is the
easy way; see README.md.

Usage
-----
    python3 intake_sync.py serve            # listen (default 0.0.0.0:8787)
    python3 intake_sync.py pair             # print a new pairing code
    python3 intake_sync.py devices          # list devices
    python3 intake_sync.py revoke DEVICE_ID # cut a device off

Environment: INTAKE_SYNC_DB (default ./intake-sync.db), INTAKE_SYNC_HOST,
INTAKE_SYNC_PORT, INTAKE_SYNC_MAX_BODY_MB (default 32).
"""

from __future__ import annotations

import hashlib
import json
import os
import secrets
import sqlite3
import sys
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

VERSION = "1.0.0"
PAIRING_TTL_S = 600
PAIRING_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"  # no 0/O, 1/I/L
MAX_PAIR_FAILURES = 10
PULL_LIMIT_MAX = 1000

DB_PATH = os.environ.get("INTAKE_SYNC_DB", "intake-sync.db")
MAX_BODY = int(os.environ.get("INTAKE_SYNC_MAX_BODY_MB", "32")) * 1024 * 1024

SCHEMA = """
CREATE TABLE IF NOT EXISTS device (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER,
    revoked_at INTEGER
);
CREATE TABLE IF NOT EXISTS pairing_code (
    code_hash TEXT PRIMARY KEY,
    expires_at INTEGER NOT NULL,
    used_at INTEGER,
    created_by TEXT
);
CREATE TABLE IF NOT EXISTS row (
    tbl TEXT NOT NULL,
    id TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER,
    hash TEXT NOT NULL,
    payload TEXT NOT NULL,
    origin_device TEXT,
    seq INTEGER NOT NULL UNIQUE,
    PRIMARY KEY (tbl, id)
);
CREATE INDEX IF NOT EXISTS row_seq ON row (seq);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""

_lock = threading.Lock()
_pair_failures: list[float] = []


def now_ms() -> int:
    return int(time.time() * 1000)


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def connect() -> sqlite3.Connection:
    db = sqlite3.connect(DB_PATH, isolation_level=None, check_same_thread=False)
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    db.executescript(SCHEMA)
    return db


def wins(incoming_updated: int, incoming_hash: str, existing_updated: int, existing_hash: str) -> bool:
    """The ordering every device uses too (MergePlan.incomingWins)."""
    if incoming_updated != existing_updated:
        return incoming_updated > existing_updated
    return incoming_hash > existing_hash


def new_pairing_code(db: sqlite3.Connection, created_by: str | None) -> tuple[str, int]:
    raw = "".join(secrets.choice(PAIRING_ALPHABET) for _ in range(8))
    expires = now_ms() + PAIRING_TTL_S * 1000
    db.execute(
        "INSERT INTO pairing_code (code_hash, expires_at, created_by) VALUES (?, ?, ?)",
        (sha256(raw), expires, created_by),
    )
    return f"{raw[:4]}-{raw[4:]}", expires


def normalise_code(code: str) -> str:
    return "".join(ch for ch in code.upper() if ch in PAIRING_ALPHABET)


def next_seq(db: sqlite3.Connection) -> int:
    cur = db.execute("SELECT value FROM meta WHERE key = 'seq'").fetchone()
    value = int(cur[0]) + 1 if cur else 1
    db.execute("INSERT INTO meta (key, value) VALUES ('seq', ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", (str(value),))
    return value


class ApiError(Exception):
    def __init__(self, status: HTTPStatus, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


class Handler(BaseHTTPRequestHandler):
    server_version = f"IntakeSync/{VERSION}"
    db: sqlite3.Connection  # set on the class at startup

    # ---- plumbing -------------------------------------------------------

    def log_message(self, fmt: str, *args) -> None:  # no tokens, no bodies
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))

    def _send(self, status: int, body: dict) -> None:
        data = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY:
            raise ApiError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "Request too large; push in smaller batches.")
        try:
            parsed = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            raise ApiError(HTTPStatus.BAD_REQUEST, "Body is not JSON.")
        if not isinstance(parsed, dict):
            raise ApiError(HTTPStatus.BAD_REQUEST, "Body must be a JSON object.")
        return parsed

    def _device(self) -> sqlite3.Row:
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            raise ApiError(HTTPStatus.UNAUTHORIZED, "Missing device token.")
        row = self.db.execute(
            "SELECT id, name, revoked_at FROM device WHERE token_hash = ?", (sha256(auth[7:].strip()),)
        ).fetchone()
        if row is None or row[2] is not None:
            raise ApiError(HTTPStatus.UNAUTHORIZED, "This device is not paired, or was removed.")
        self.db.execute("UPDATE device SET last_seen_at = ? WHERE id = ?", (now_ms(), row[0]))
        return row

    def _dispatch(self, method: str) -> None:
        url = urlparse(self.path)
        try:
            with _lock:
                route = (method, url.path)
                if route == ("GET", "/v1/health"):
                    return self._send(200, {"ok": True, "version": VERSION})
                if route == ("POST", "/v1/pair"):
                    return self._send(200, self.pair(self._body()))
                device = self._device()
                if route == ("POST", "/v1/pairing-codes"):
                    code, expires = new_pairing_code(self.db, device[0])
                    return self._send(200, {"code": code, "expiresAt": expires})
                if route == ("GET", "/v1/devices"):
                    return self._send(200, {"devices": self.devices(device[0])})
                if method == "DELETE" and url.path.startswith("/v1/devices/"):
                    return self._send(200, self.revoke(url.path.rsplit("/", 1)[-1]))
                if route == ("POST", "/v1/push"):
                    return self._send(200, self.push(device[0], self._body()))
                if route == ("GET", "/v1/pull"):
                    return self._send(200, self.pull(parse_qs(url.query)))
                raise ApiError(HTTPStatus.NOT_FOUND, "No such endpoint.")
        except ApiError as e:
            self._send(e.status, {"error": e.message})
        except Exception as e:  # never leak internals
            sys.stderr.write(f"error: {type(e).__name__}: {e}\n")
            self._send(500, {"error": "Server error."})

    def do_GET(self) -> None:
        self._dispatch("GET")

    def do_POST(self) -> None:
        self._dispatch("POST")

    def do_DELETE(self) -> None:
        self._dispatch("DELETE")

    # ---- endpoints ------------------------------------------------------

    def pair(self, body: dict) -> dict:
        global _pair_failures
        cutoff = time.time() - PAIRING_TTL_S
        _pair_failures = [t for t in _pair_failures if t > cutoff]
        if len(_pair_failures) >= MAX_PAIR_FAILURES:
            raise ApiError(HTTPStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Wait ten minutes.")
        code = normalise_code(str(body.get("code", "")))
        name = str(body.get("deviceName", "Device")).strip()[:60] or "Device"
        row = self.db.execute(
            "SELECT code_hash FROM pairing_code WHERE code_hash = ? AND used_at IS NULL AND expires_at > ?",
            (sha256(code), now_ms()),
        ).fetchone()
        if row is None:
            _pair_failures.append(time.time())
            raise ApiError(HTTPStatus.FORBIDDEN, "That code is wrong, used or expired.")
        token = secrets.token_urlsafe(32)
        device_id = secrets.token_hex(8)
        self.db.execute("UPDATE pairing_code SET used_at = ? WHERE code_hash = ?", (now_ms(), row[0]))
        self.db.execute(
            "INSERT INTO device (id, name, token_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
            (device_id, name, sha256(token), now_ms(), now_ms()),
        )
        return {"deviceId": device_id, "token": token}

    def devices(self, current: str) -> list[dict]:
        rows = self.db.execute(
            "SELECT id, name, created_at, last_seen_at FROM device WHERE revoked_at IS NULL ORDER BY created_at"
        ).fetchall()
        return [
            {"id": r[0], "name": r[1], "createdAt": r[2], "lastSeenAt": r[3], "current": r[0] == current}
            for r in rows
        ]

    def revoke(self, device_id: str) -> dict:
        changed = self.db.execute(
            "UPDATE device SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL", (now_ms(), device_id)
        ).rowcount
        if not changed:
            raise ApiError(HTTPStatus.NOT_FOUND, "No such device.")
        return {"revoked": device_id}

    def push(self, device_id: str, body: dict) -> dict:
        rows = body.get("rows")
        if not isinstance(rows, list):
            raise ApiError(HTTPStatus.BAD_REQUEST, "Expected {\"rows\": [...]}.")
        accepted = ignored = 0
        self.db.execute("BEGIN IMMEDIATE")
        try:
            for r in rows:
                try:
                    tbl, rid = str(r["table"]), str(r["id"])
                    updated, row_hash = int(r["updatedAt"]), str(r["hash"])
                    deleted = r.get("deletedAt")
                    payload = json.dumps(r["payload"], separators=(",", ":"), ensure_ascii=False)
                except (KeyError, TypeError, ValueError):
                    raise ApiError(HTTPStatus.BAD_REQUEST, "Each row needs table, id, updatedAt, hash and payload.")
                existing = self.db.execute(
                    "SELECT updated_at, hash FROM row WHERE tbl = ? AND id = ?", (tbl, rid)
                ).fetchone()
                if existing is not None and not wins(updated, row_hash, existing[0], existing[1]):
                    ignored += 1
                    continue
                self.db.execute(
                    "INSERT INTO row (tbl, id, updated_at, deleted_at, hash, payload, origin_device, seq) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    "ON CONFLICT(tbl, id) DO UPDATE SET updated_at = excluded.updated_at, deleted_at = excluded.deleted_at, "
                    "hash = excluded.hash, payload = excluded.payload, origin_device = excluded.origin_device, seq = excluded.seq",
                    (tbl, rid, updated, None if deleted is None else int(deleted), row_hash, payload, device_id, next_seq(self.db)),
                )
                accepted += 1
            self.db.execute("COMMIT")
        except BaseException:
            self.db.execute("ROLLBACK")
            raise
        latest = self.db.execute("SELECT value FROM meta WHERE key = 'seq'").fetchone()
        return {"accepted": accepted, "ignored": ignored, "seq": int(latest[0]) if latest else 0}

    def pull(self, query: dict) -> dict:
        try:
            since = int(query.get("since", ["0"])[0])
            limit = max(1, min(int(query.get("limit", ["500"])[0]), PULL_LIMIT_MAX))
        except ValueError:
            raise ApiError(HTTPStatus.BAD_REQUEST, "since and limit must be numbers.")
        rows = self.db.execute(
            "SELECT tbl, id, updated_at, deleted_at, hash, payload, origin_device, seq FROM row WHERE seq > ? ORDER BY seq LIMIT ?",
            (since, limit + 1),
        ).fetchall()
        more = len(rows) > limit
        rows = rows[:limit]
        return {
            "rows": [
                {
                    "table": r[0], "id": r[1], "updatedAt": r[2], "deletedAt": r[3], "hash": r[4],
                    "payload": json.loads(r[5]), "originDevice": r[6], "seq": r[7],
                }
                for r in rows
            ],
            "next": rows[-1][7] if rows else since,
            "more": more,
        }


def serve() -> None:
    db = connect()
    Handler.db = db
    if db.execute("SELECT COUNT(*) FROM device WHERE revoked_at IS NULL").fetchone()[0] == 0:
        code, _ = new_pairing_code(db, None)
        print(f"No devices paired yet. Pairing code (valid 10 minutes): {code}", flush=True)
        print("Run `python3 intake_sync.py pair` for another.", flush=True)
    host = os.environ.get("INTAKE_SYNC_HOST", "0.0.0.0")
    port = int(os.environ.get("INTAKE_SYNC_PORT", "8787"))
    httpd = ThreadingHTTPServer((host, port), Handler)
    print(f"Intake sync {VERSION} listening on {host}:{port}, database {os.path.abspath(DB_PATH)}", flush=True)
    httpd.serve_forever()


def main(argv: list[str]) -> int:
    cmd = argv[1] if len(argv) > 1 else "serve"
    if cmd == "serve":
        serve()
        return 0
    db = connect()
    if cmd == "pair":
        code, expires = new_pairing_code(db, None)
        print(f"Pairing code: {code} (valid until {time.strftime('%H:%M', time.localtime(expires / 1000))})")
        return 0
    if cmd == "devices":
        for r in db.execute("SELECT id, name, last_seen_at, revoked_at FROM device ORDER BY created_at"):
            seen = time.strftime("%Y-%m-%d %H:%M", time.localtime(r[2] / 1000)) if r[2] else "never"
            print(f"{r[0]}  {r[1]:<24} last seen {seen}{'  (revoked)' if r[3] else ''}")
        return 0
    if cmd == "revoke" and len(argv) > 2:
        n = db.execute("UPDATE device SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL", (now_ms(), argv[2])).rowcount
        print("Revoked." if n else "No such device.")
        return 0 if n else 1
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
