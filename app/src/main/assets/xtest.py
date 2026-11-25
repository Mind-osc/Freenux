#!/usr/bin/env python3
"""Freenux: ελέγχει ποιο αίτημα X11 δεν απαντάει (χωρίς εξωτερικές βιβλιοθήκες)."""
import socket, struct, sys

TIMEOUT = 4


def connect(addr):
    s = socket.socket(socket.AF_UNIX)
    s.settimeout(TIMEOUT)
    s.connect(addr)
    s.sendall(struct.pack("<BxHHHH2x", 0x6C, 11, 0, 0, 0))
    head = recv(s, 8)
    if head[0] != 1:
        raise RuntimeError("setup απορρίφθηκε: %r" % head)
    extra = struct.unpack("<H", head[6:8])[0] * 4
    data = recv(s, extra)
    global ROOT, BASE, WIDTH, HEIGHT
    BASE = struct.unpack("<I", data[4:8])[0]
    vlen = struct.unpack("<H", data[16:18])[0]
    nfmt = data[21]
    off = 32 + vlen + (4 - vlen % 4) % 4 + 8 * nfmt
    ROOT = struct.unpack("<I", data[off:off + 4])[0]
    WIDTH, HEIGHT = struct.unpack("<HH", data[off + 20:off + 24])
    return s


def recv(s, n):
    b = b""
    while len(b) < n:
        c = s.recv(n - len(b))
        if not c:
            raise RuntimeError("έκλεισε η σύνδεση")
        b += c
    return b


def reply(s):
    r = recv(s, 32)
    if r[0] == 0:
        return "error code %d" % r[1]
    extra = struct.unpack("<I", r[4:8])[0] * 4
    if r[0] == 1 and extra:
        recv(s, extra)
    return r


def query_ext(s, name):
    n = name.encode()
    pad = (4 - len(n) % 4) % 4
    s.sendall(struct.pack("<BxHH2x", 98, 2 + (len(n) + pad) // 4, len(n)) + n + b"\0" * pad)
    r = reply(s)
    if isinstance(r, str):
        return False, 0, r
    return bool(r[8]), r[9], ""


def step(label, fn):
    try:
        out = fn()
        print("   %-22s %s" % (label, out))
        return True
    except socket.timeout:
        print("   %-22s ΔΕΝ ΑΠΑΝΤΑΕΙ (timeout) ✗" % label)
        return False
    except Exception as e:
        print("   %-22s σφάλμα: %s" % (label, e))
        return False


def main():
    print(">>> [xtest] abstract socket @/tmp/.X11-unix/X0:")
    step("abstract", lambda: (connect("\0/tmp/.X11-unix/X0").close(), "συνδέθηκε (κάποιος ακούει εκεί!)")[1])

    print(">>> [xtest] αρχείο /tmp/.X11-unix/X0:")
    try:
        s = connect("/tmp/.X11-unix/X0")
    except Exception as e:
        print("   σύνδεση: ΣΦΑΛΜΑ %s" % e)
        return
    print("   σύνδεση + setup       OK")

    ok = step("GetInputFocus", lambda: (s.sendall(struct.pack("<BxH", 43, 1)), reply(s), "OK")[2])
    if not ok:
        return

    opcodes = {}
    for ext in ["BIG-REQUESTS", "XKEYBOARD", "RANDR", "XInputExtension", "RENDER",
                "XFIXES", "Composite", "DAMAGE", "SYNC", "MIT-SHM", "Present", "DRI3", "GLX"]:
        def q(ext=ext):
            present, op, err = query_ext(s, ext)
            if err:
                return err
            if present:
                opcodes[ext] = op
            return "ναι (op %d)" % op if present else "όχι"
        if not step(ext, q):
            return

    if "BIG-REQUESTS" in opcodes:
        step("BigReqEnable", lambda: (s.sendall(struct.pack("<BBH", opcodes["BIG-REQUESTS"], 0, 1)), reply(s), "OK")[2])
    if "XKEYBOARD" in opcodes:
        step("XkbUseExtension", lambda: (s.sendall(struct.pack("<BBHHH", opcodes["XKEYBOARD"], 0, 2, 1, 0)), reply(s), "OK")[2])
        # XkbGetMap (όπως κάνουν GTK/xkbcommon): full map για core keyboard (0x100)
        step("XkbGetMap", lambda: (s.sendall(struct.pack("<BBHHHHBBBBBBBBHBBBBBB2x", opcodes["XKEYBOARD"], 8, 7,
                                                         0x100, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                                   reply(s), "OK")[2])
    if "RANDR" in opcodes:
        step("RRQueryVersion", lambda: (s.sendall(struct.pack("<BBHII", opcodes["RANDR"], 0, 3, 1, 6)), reply(s), "OK")[2])
    if "XInputExtension" in opcodes:
        step("XIQueryVersion", lambda: (s.sendall(struct.pack("<BBHHH", opcodes["XInputExtension"], 47, 2, 2, 3)), reply(s), "OK")[2])
    print(">>> [xtest] τέλος")


def intern(s, name):
    n = name.encode(); pad = (4 - len(n) % 4) % 4
    s.sendall(struct.pack("<BBHH2x", 16, 1, 2 + (len(n) + pad) // 4, len(n)) + n + b"\0" * pad)
    r = reply(s)
    return 0 if isinstance(r, str) else struct.unpack("<I", r[8:12])[0]


def tree():
    import time
    s = connect("/tmp/.X11-unix/X0")
    print(">>> [xtest] οθόνη X: %dx%d" % (WIDTH, HEIGHT))
    s.sendall(struct.pack("<BxHI", 15, 2, ROOT))
    r = recv(s, 32)
    n = struct.unpack("<H", r[16:18])[0]
    kids = struct.unpack("<%dI" % n, recv(s, 4 * n)) if n else []
    mapped = 0
    for w in kids:
        s.sendall(struct.pack("<BxHI", 3, 2, w))
        a = reply(s)
        if not isinstance(a, str) and a[26] == 2:
            mapped += 1
    print(">>> [xtest] παράθυρα: %d (ορατά: %d)" % (n, mapped))
    atom = intern(s, "_NET_SUPPORTING_WM_CHECK")
    wm = False
    if atom:
        s.sendall(struct.pack("<BBHIIIII", 20, 0, 6, ROOT, atom, 0, 0, 1))
        g = reply(s)
        wm = not isinstance(g, str) and struct.unpack("<I", g[16:20])[0] > 0
    print(">>> [xtest] διαχειριστής παραθύρων: " + ("ναι" if wm else "όχι"))
    # Κόκκινο τεστ: παράθυρο σε όλη την οθόνη για 10''
    wid = BASE | 0x77
    s.sendall(struct.pack("<BBHIIhhHHHHIII", 1, 0, 10, wid, ROOT, 0, 0, WIDTH, HEIGHT, 0, 1, 0, 0x202, 0xFF0000) + struct.pack("<I", 1))
    s.sendall(struct.pack("<BxHI", 8, 2, wid))
    s.sendall(struct.pack("<BxH", 43, 1)); reply(s)
    print(">>> [xtest] ΚΟΚΚΙΝΗ ΟΘΟΝΗ για 10'' – φαίνεται στο Termux:X11;")
    sys.stdout.flush()
    time.sleep(10)
    s.sendall(struct.pack("<BxHI", 4, 2, wid))
    s.sendall(struct.pack("<BxH", 43, 1)); reply(s)
    print(">>> [xtest] τέλος κόκκινου τεστ")


if len(sys.argv) > 1 and sys.argv[1] == "--tree":
    tree()
else:
    main()
sys.stdout.flush()
