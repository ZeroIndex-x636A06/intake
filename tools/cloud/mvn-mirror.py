"""Caching Maven Central mirror: fetches with curl (Gradle's own client gets 429 here)."""
import http.server, os, subprocess, socketserver, sys
CACHE = os.environ.get("MVN_MIRROR_CACHE", "/root/mvnproxy/cache")
UP = "https://repo.maven.apache.org/maven2"
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _get(self, head):
        path = self.path.split("?")[0]
        if ".." in path: self.send_error(400); return
        f = CACHE + path
        miss = f + ".404"
        if not os.path.isfile(f) and not os.path.exists(miss):
            os.makedirs(os.path.dirname(f), exist_ok=True)
            tmp = f + ".part%d" % os.getpid()
            for attempt in range(4):
                r = subprocess.run(["curl", "-sS", "-L", "-m", "300", "-o", tmp, "-w", "%{http_code}", UP + path], capture_output=True, text=True)
                code = r.stdout.strip()
                if code == "200": os.replace(tmp, f); break
                if code == "404": open(miss, "w").close(); break
                import time; time.sleep(2 ** attempt)
            if os.path.exists(tmp): os.remove(tmp)
        if not os.path.isfile(f):
            self.send_error(404 if os.path.exists(miss) else 502); return
        self.send_response(200)
        self.send_header("Content-Length", str(os.path.getsize(f)))
        self.end_headers()
        if not head:
            with open(f, "rb") as fh: self.wfile.write(fh.read())
    def do_GET(self): self._get(False)
    def do_HEAD(self): self._get(True)
class S(socketserver.ThreadingMixIn, http.server.HTTPServer): daemon_threads = True
S(("127.0.0.1", 8099), H).serve_forever()
