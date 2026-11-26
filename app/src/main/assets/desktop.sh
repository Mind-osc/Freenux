#!/bin/bash
# Freenux: εκκίνηση X (TigerVNC) + XFCE + noVNC στο 127.0.0.1:6080
export DISPLAY=:1 HOME=/root USER=root LANG=C.UTF-8
export MOZ_FAKE_NO_SANDBOX=1 MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1
export PATH="/root/.local/bin:$PATH"
export NO_AT_BRIDGE=1  # χωρίς τα μηνύματα AT-SPI
export XDG_RUNTIME_DIR=/tmp/runtime-root
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

# Εμφάνιση Mint (μία φορά· αν αποτύχει, συνεχίζουμε κανονικά)
bash /usr/local/bin/freenux-mint || echo "(η εμφάνιση Mint θα ξαναδοκιμαστεί στην επόμενη εκκίνηση)"
. /usr/local/bin/freenux-audio
# Το XFCE ψάχνει για αναστολή/αδρανοποίηση: απαντάμε «δεν υποστηρίζεται» αντί για σφάλμα
[ -x /usr/bin/pm-is-supported ] || { printf '#!/bin/sh\nexit 1\n' > /usr/bin/pm-is-supported; chmod 755 /usr/bin/pm-is-supported; }
QUIET='^\s*$|systemd proxy|system bus|ConsoleKit|GPG agent|SSH authentication agent|_NET_(NUMBER_OF_DESKTOPS|WORKAREA|CURRENT_DESKTOP)|Thumbnailer failed|pm-is-supported|extension "DPMS"|AT-SPI|a11y'
. /usr/local/bin/freenux-folders
. /usr/local/bin/freenux-ssh
mkdir -p /root/.config/autostart
printf '[Desktop Entry]\nType=Application\nName=Freenux συνεδρία\nExec=freenux-session\nNoDisplay=true\n' > /root/.config/autostart/freenux-session.desktop

pkill -f Xtigervnc 2>/dev/null; pkill -f xfce4-session 2>/dev/null
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix

echo ">>> X server"
Xtigervnc :1 -geometry "${GEOM:-1920x1080}" -depth 24 -desktop Freenux \
  -SecurityTypes None -localhost -rfbport 5901 -AlwaysShared -dpi 110 >/tmp/xvnc.log 2>&1 &

for i in $(seq 1 100); do [ -e /tmp/.X11-unix/X1 ] && break; sleep 0.2; done

# XFCE: αν κάνεις Log Out ή κρασάρει, ξεκινάει πάλι μόνο του (όχι μαύρη οθόνη)
echo ">>> XFCE"
(
  while [ -e /tmp/.X11-unix/X1 ]; do
    dbus-launch --exit-with-session startxfce4 >/tmp/xfce.log 2>&1
    echo ">>> Η συνεδρία XFCE έκλεισε – επανεκκίνηση"
    sleep 1
  done
) &

echo ">>> noVNC :6080"
exec python3 -m websockify --web /usr/share/novnc 127.0.0.1:6080 127.0.0.1:5901
