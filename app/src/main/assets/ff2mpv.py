#!/usr/bin/env python3
"""Freenux: native messaging host για την επέκταση ff2mpv του Firefox.
Παίρνει τον σύνδεσμο της σελίδας και τον ανοίγει στο mpv."""
import json, struct, subprocess, sys


def read():
    raw = sys.stdin.buffer.read(4)
    if len(raw) < 4:
        sys.exit(0)
    n = struct.unpack("@I", raw)[0]
    return json.loads(sys.stdin.buffer.read(n))


def send(obj):
    b = json.dumps(obj).encode()
    sys.stdout.buffer.write(struct.pack("@I", len(b)))
    sys.stdout.buffer.write(b)
    sys.stdout.buffer.flush()


msg = read()
url = msg.get("url") if isinstance(msg, dict) else None
if url:
    subprocess.Popen(["mpv", "--no-terminal", "--force-window=immediate", "--", url],
                     stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                     stderr=subprocess.DEVNULL, start_new_session=True)
send("ok")
