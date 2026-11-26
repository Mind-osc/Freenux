#!/usr/bin/env python3
"""Freenux touchpad: δέχεται εντολές από την εφαρμογή Android (unix socket /tmp/freenux-touch.sock) και
κουνάει το ποντίκι / πατάει κουμπιά μέσω της επέκτασης XTEST του X server (χωρίς βιβλιοθήκες).

Εντολές, μία ανά γραμμή:
  m dx dy     κίνηση (σχετική)
  d b / u b   κουμπί κάτω / πάνω (1 αριστερό, 2 μεσαίο, 3 δεξί)
  c b         κλικ
  s n         κύλιση (+ κάτω, - πάνω)
  t κείμενο   πληκτρολόγηση (xdotool)
  k όνομα     πλήκτρο (π.χ. BackSpace, Return, ctrl+c)
"""
import os, socket, struct, subprocess, threading

DISPLAY = os.environ.get("DISPLAY", ":0")
NUM = DISPLAY.split(":")[-1].split(".")[0] or "0"


def x_connect():
    s = socket.socket(socket.AF_UNIX)
    s.connect("/tmp/.X11-unix/X" + NUM)
    s.sendall(struct.pack("<BxHHHH2x", 0x6C, 11, 0, 0, 0))
    head = s.recv(8, socket.MSG_WAITALL)
    extra = struct.unpack("<H", head[6:8])[0] * 4
    recv_all(s, extra)
    name = b"XTEST"
    pad = (4 - len(name) % 4) % 4
    s.sendall(struct.pack("<BxHH2x", 98, 2 + (len(name) + pad) // 4, len(name)) + name + b"\0" * pad)
    r = recv_all(s, 32)
    if not r[8]:
        raise SystemExit("ο X server δεν έχει XTEST")
    return s, r[9]


def recv_all(s, n):
    b = b""
    while len(b) < n:
        c = s.recv(n - len(b))
        if not c:
            raise SystemExit("έκλεισε ο X server")
        b += c
    return b


X, OP = x_connect()
# Αδειάζουμε ό,τι στέλνει ο server (σφάλματα/γεγονότα), για να μη γεμίσει ο buffer
threading.Thread(target=lambda: [X.recv(4096) for _ in iter(int, 1)], daemon=True).start()


def fake(kind, detail, x=0, y=0):
    X.sendall(struct.pack("<BBHBBHIIIIhhIHBB", OP, 2, 9, kind, detail, 0, 0, 0, 0, 0, x, y, 0, 0, 0, 0))


def handle(line):
    parts = line.rstrip("\n").split(" ", 1)
    cmd, arg = parts[0], (parts[1] if len(parts) > 1 else "")
    if cmd == "m":
        dx, dy = (int(v) for v in arg.split())
        fake(6, 1, max(-32768, min(32767, dx)), max(-32768, min(32767, dy)))  # MotionNotify, σχετική
    elif cmd == "d":
        fake(4, int(arg))
    elif cmd == "u":
        fake(5, int(arg))
    elif cmd == "c":
        fake(4, int(arg)); fake(5, int(arg))
    elif cmd == "s":
        n = int(arg)
        b = 5 if n > 0 else 4
        for _ in range(min(abs(n), 20)):
            fake(4, b); fake(5, b)
    elif cmd == "t" and arg:
        subprocess.run(["xdotool", "type", "--clearmodifiers", "--delay", "0", "--", arg])
    elif cmd == "k" and arg:
        subprocess.run(["xdotool", "key", "--clearmodifiers", "--", arg])


def client(conn):
    with conn, conn.makefile("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                handle(line)
            except Exception:
                pass


PATH = "/tmp/freenux-touch.sock"
try:
    os.unlink(PATH)
except FileNotFoundError:
    pass
srv = socket.socket(socket.AF_UNIX)
srv.bind(PATH)
os.chmod(PATH, 0o600)
srv.listen(2)
while True:
    c, _ = srv.accept()
    threading.Thread(target=client, args=(c,), daemon=True).start()
