#!/usr/bin/env python3
"""A stand-in for OpenAI's Responses and Embeddings APIs, for smoke runs: canned answers in the shape each job's
schema asks for, so the flows run without cost or flakiness. Usage: fake_openai.py [port], default 9999."""
import hashlib
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIMENSIONS = 1536

ANSWERS = {
    "itinerary": {"summary": "A smoke-test itinerary", "days": [
        {"dayNumber": 1, "title": "Arrival", "summary": "Settle in", "items": [
            {"timeOfDay": "MORNING", "title": "Walk the old town", "description": "On foot", "placeId": "",
             "durationMinutes": 120, "tips": "Wear good shoes"}]}]},
    "itinerary_day": {"dayNumber": 1, "title": "Rewritten day", "summary": "Slower", "items": [
        {"timeOfDay": "AFTERNOON", "title": "Museum", "description": "Inside", "placeId": "",
         "durationMinutes": 90, "tips": ""}]},
    "visa_guidance": {"summary": "Apply online before you travel", "steps": ["Fill the form", "Pay the fee"],
                      "officialLinks": [], "documents": [], "notes": []},
    "place_answer": {"answer": "Yes, it is busy in the morning", "tips": ["Go early"], "caveats": []},
}
GENERIC = {"summary": "A place worth seeing", "highlights": ["Views"], "goodFor": ["Walking"]}


def vector(text):
    # Deterministic, so the same text always lands in the same place.
    seed = hashlib.sha256(text.encode()).digest()
    return [((seed[i % len(seed)] / 255.0) - 0.5) for i in range(DIMENSIONS)]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path.endswith("/embeddings"):
            inputs = body.get("input", [])
            reply = {"model": body.get("model", "fake-embedding"), "usage": {"prompt_tokens": len(inputs)},
                     "data": [{"index": i, "embedding": vector(text)} for i, text in enumerate(inputs)]}
        elif self.path.endswith("/responses"):
            name = body.get("text", {}).get("format", {}).get("name", "")
            text = json.dumps(ANSWERS.get(name, GENERIC)) if name else "A plain answer"
            reply = {"model": body.get("model", "fake-model"), "status": "completed",
                     "usage": {"input_tokens": 100, "output_tokens": 50},
                     "output": [{"type": "message", "content": [{"type": "output_text", "text": text,
                                                                 "annotations": []}]}]}
        else:
            self.send_response(404)
            self.end_headers()
            return
        payload = json.dumps(reply).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9999
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
