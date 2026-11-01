"""Quiet static server for the packaged offline game and browser checks."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial

class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 4173), partial(Handler, directory='app/src/main/assets')).serve_forever()
