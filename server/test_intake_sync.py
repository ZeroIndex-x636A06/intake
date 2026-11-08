"""Tests for the sync server: python3 -m unittest server/test_intake_sync.py"""

import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

os.environ["INTAKE_SYNC_DB"] = os.path.join(tempfile.mkdtemp(), "t.db")
import intake_sync  # noqa: E402


class SyncServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.db = intake_sync.connect()
        intake_sync.Handler.db = cls.db
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), intake_sync.Handler)
        cls.base = f"http://127.0.0.1:{cls.httpd.server_address[1]}"
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def call(self, method, path, body=None, token=None):
        req = urllib.request.Request(self.base + path, method=method,
                                     data=None if body is None else json.dumps(body).encode())
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def paired(self, name):
        code, _ = intake_sync.new_pairing_code(self.db, None)
        status, body = self.call("POST", "/v1/pair", {"code": code.lower(), "deviceName": name})
        self.assertEqual(status, 200, body)
        return body["token"]

    def row(self, rid, updated, value, h=None, table="food_log"):
        return {"table": table, "id": rid, "updatedAt": updated, "deletedAt": None,
                "hash": h or f"{value:064d}", "payload": {"id": rid, "v": value}}

    def test_pairing_codes_are_single_use(self):
        code, _ = intake_sync.new_pairing_code(self.db, None)
        self.assertEqual(self.call("POST", "/v1/pair", {"code": code, "deviceName": "a"})[0], 200)
        status, body = self.call("POST", "/v1/pair", {"code": code, "deviceName": "b"})
        self.assertEqual(status, 403)
        self.assertNotIn(code, json.dumps(body))

    def test_requests_need_a_token(self):
        self.assertEqual(self.call("GET", "/v1/pull?since=0")[0], 401)
        self.assertEqual(self.call("GET", "/v1/pull?since=0", token="nope")[0], 401)

    def test_last_writer_wins_and_ties_break_by_hash(self):
        a, b = self.paired("phone"), self.paired("tablet")
        self.call("POST", "/v1/push", {"rows": [self.row("r1", 100, 1)]}, a)
        # Older version from the other device is ignored.
        _, res = self.call("POST", "/v1/push", {"rows": [self.row("r1", 90, 2)]}, b)
        self.assertEqual(res["ignored"], 1)
        # Same timestamp, larger hash wins regardless of arrival order.
        _, res = self.call("POST", "/v1/push", {"rows": [self.row("r1", 100, 3)]}, b)
        self.assertEqual(res["accepted"], 1)
        _, res = self.call("POST", "/v1/push", {"rows": [self.row("r1", 100, 1)]}, a)
        self.assertEqual(res["ignored"], 1)
        _, pulled = self.call("GET", "/v1/pull?since=0", token=a)
        latest = [r for r in pulled["rows"] if r["id"] == "r1"]
        self.assertEqual(len(latest), 1)
        self.assertEqual(latest[0]["payload"]["v"], 3)

    def test_pull_pages_by_sequence(self):
        t = self.paired("pager")
        _, start = self.call("GET", "/v1/pull?since=0&limit=1000", token=t)
        since = start["next"]
        while start["more"]:
            _, start = self.call("GET", f"/v1/pull?since={since}&limit=1000", token=t)
            since = start["next"]
        self.call("POST", "/v1/push", {"rows": [self.row(f"p{i}", 10 + i, i, table="water") for i in range(5)]}, t)
        _, page1 = self.call("GET", f"/v1/pull?since={since}&limit=3", token=t)
        self.assertEqual(len(page1["rows"]), 3)
        self.assertTrue(page1["more"])
        _, page2 = self.call("GET", f"/v1/pull?since={page1['next']}&limit=3", token=t)
        self.assertEqual(len(page2["rows"]), 2)
        self.assertFalse(page2["more"])

    def test_revoked_device_is_locked_out(self):
        a, b = self.paired("keeper"), self.paired("lost phone")
        _, devices = self.call("GET", "/v1/devices", token=a)
        lost = next(d for d in devices["devices"] if d["name"] == "lost phone")
        self.assertEqual(self.call("DELETE", f"/v1/devices/{lost['id']}", token=a)[0], 200)
        self.assertEqual(self.call("GET", "/v1/pull?since=0", token=b)[0], 401)

    def test_a_paired_device_can_mint_a_code_for_the_next_one(self):
        a = self.paired("first")
        _, minted = self.call("POST", "/v1/pairing-codes", {}, a)
        status, _ = self.call("POST", "/v1/pair", {"code": minted["code"], "deviceName": "second"})
        self.assertEqual(status, 200)


if __name__ == "__main__":
    unittest.main()
