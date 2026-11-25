#!/bin/bash
# Freenux: εκκίνηση X (TigerVNC) + XFCE + noVNC στο 127.0.0.1:6080
export DISPLAY=:1 HOME=/root USER=root LANG=C.UTF-8
export MOZ_FAKE_NO_SANDBOX=1 MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1
export PATH="/root/.local/bin:$PATH"
export XDG_RUNTIME_DIR=/tmp/runtime-root
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix

echo ">>> X server"
Xtigervnc :1 -geometry "${GEOM:-1920x1080}" -depth 24 -desktop Freenux \
  -SecurityTypes None -localhost -rfbport 5901 -AlwaysShared -dpi 110 >/tmp/xvnc.log 2>&1 &

for i in $(seq 1 100); do [ -e /tmp/.X11-unix/X1 ] && break; sleep 0.2; done

echo ">>> XFCE"
dbus-launch --exit-with-session startxfce4 >/tmp/xfce.log 2>&1 &

echo ">>> noVNC :6080"
exec python3 -m websockify --web /usr/share/novnc 127.0.0.1:6080 127.0.0.1:5901
