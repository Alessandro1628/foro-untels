"""Proveedores locales para las pruebas de recuperación y sincronización. Solo loopback."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from urllib.parse import urlsplit

messages = {}
events = {}


class LocalProvider(BaseHTTPRequestHandler):
    def reply(self, status, data):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def do_POST(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                length = int(self.rfile.readline().split(b";")[0].strip(), 16)
                if not length:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(length))
                self.rfile.readline()
            raw = b"".join(chunks)
        else:
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        path = urlsplit(self.path).path
        if path == "/token":
            return self.reply(200, {"access_token": "local"})
        data = json.loads(raw)
        if path == "/mail":
            messages[data["to"][0]] = data["text"]
            return self.reply(200, {"id": "local"})
        if path == "/calendars/primary/events":
            if data["id"] in events:
                return self.reply(409, {})
            events[data["id"]] = data
            if os.getenv("FORO_TEST_CALENDAR_RETRY") == "1":
                return self.reply(503, {})
            return self.reply(200, {"hangoutLink": "https://meet.google.com/local-test"})
        self.reply(404, {})

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/mail":
            return self.reply(200, messages)
        if path == "/events":
            return self.reply(200, list(events.values()))
        if path.rsplit("/", 1)[-1] in events:
            return self.reply(200, {"hangoutLink": "https://meet.google.com/local-test"})
        self.reply(404, {})


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 18091), LocalProvider).serve_forever()
