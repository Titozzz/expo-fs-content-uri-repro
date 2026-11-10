"""Accepts PUT /upload and answers with the number of bytes received."""
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_PUT(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        message = f"received {len(body)} bytes (Content-Length: {length})"
        print(message, flush=True)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(message.encode())


HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
