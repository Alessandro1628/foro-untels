"""Doble local de Google Books usado por sprint_acceptance.py."""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlsplit


class BooksFixture(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path)
        query = parse_qs(path.query).get("q", [""])[0]
        book = {"id": "vol_1", "volumeInfo": {"title": "Álgebra lineal", "authors": ["Ana Ruiz"]}}
        if query == "vista":
            book["accessInfo"] = {"viewability": "PARTIAL"}
        status = 200
        if path.path == "/volumes/vol_1":
            result = book
        elif path.path != "/volumes":
            status, result = 404, {}
        elif query == "caida":
            status, result = 503, {}
        elif query == "sinresultados":
            result = {"totalItems": 0}
        else:
            result = {"items": [book]}
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(result).encode())


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 18090), BooksFixture).serve_forever()
