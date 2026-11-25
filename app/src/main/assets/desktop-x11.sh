#!/bin/bash
# Freenux: XFCE πάνω στον native X server του Termux:X11 (DISPLAY=:0), χωρίς VNC
export DISPLAY=:0 HOME=/root USER=root LANG=C.UTF-8
export MOZ_FAKE_NO_SANDBOX=1 MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1
export PATH="/root/.local/bin:$PATH"
export XDG_RUNTIME_DIR=/tmp/runtime-root
export QT_X11_NO_MITSHM=1 _X11_NO_MITSHM=1 _MITSHM=0
# Γραφικά μόνο από τον επεξεργαστή: χωρίς DRI3 (κολλάει στο proot)
export LIBGL_DRI3_DISABLE=1 LIBGL_ALWAYS_SOFTWARE=1 GDK_GL=disable
export XDG_CURRENT_DESKTOP=XFCE XDG_SESSION_TYPE=x11 XDG_SESSION_DESKTOP=xfce
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

bash /usr/local/bin/freenux-mint || echo "(η εμφάνιση Mint θα ξαναδοκιμαστεί στην επόμενη εκκίνηση)"
pkill -f xfce4-session 2>/dev/null

for i in $(seq 1 150); do [ -e /tmp/.X11-unix/X0 ] && break; sleep 0.2; done
[ -e /tmp/.X11-unix/X0 ] && echo ">>> X socket OK" || echo ">>> ΣΦΑΛΜΑ: δεν βρέθηκε /tmp/.X11-unix/X0"
ls -la /tmp/.X11-unix/ 2>&1
echo ">>> XFCE στο Termux:X11"

# Τεστ: απαντάει ο X server;
python3 - <<'PY'
import socket, struct
try:
    s = socket.socket(socket.AF_UNIX); s.settimeout(5)
    s.connect("/tmp/.X11-unix/X0")
    s.send(struct.pack("<BxHHHH2x", 0x6C, 11, 0, 0, 0))
    d = s.recv(8)
    print(">>> [τεστ X] " + ("απαντάει ✓" if d and d[0] == 1 else "απάντησε λάθος: %r" % d))
except Exception as e:
    print(">>> [τεστ X] ΣΦΑΛΜΑ: %s" % e)
PY

python3 /usr/local/bin/freenux-xtest 2>&1

# Πού έχει κολλήσει κάθε πρόγραμμα του XFCE
diag_stuck() {
  echo ">>> [διάγνωση] πού περιμένει κάθε πρόγραμμα:"
  for n in xfsettingsd xfwm4 xfdesktop xfce4-panel; do
    for pid in $(pgrep -x $n); do
      st=$(awk '/^State/{print $2$3}' /proc/$pid/status 2>/dev/null)
      wc=$(cat /proc/$pid/wchan 2>/dev/null)
      fds=$(ls -l /proc/$pid/fd 2>/dev/null | awk 'NR>1{print $NF}' | grep -v "^/dev" | tr '\n' ' ' | cut -c1-160)
      echo "   $n: $st wchan=$wc"
      echo "      fd: $fds"
    done
  done
  echo ">>> [διάγνωση] xdpyinfo (απλό πρόγραμμα X):"
  command -v xdpyinfo >/dev/null || apt-get install -y -q x11-utils >/dev/null 2>&1
  timeout 10 xdpyinfo 2>&1 | head -4 | sed 's/^/   /'
  echo "   (κωδικός ${PIPESTATUS[0]}, 124 = κόλλησε)"
  echo ">>> [διάγνωση] xfwm4 με timeout:"
  DISPLAY=:0 timeout 8 xfwm4 --version 2>&1 | head -2 | sed 's/^/   /'
  echo ">>> [διάγνωση] dbus: $DBUS_SESSION_BUS_ADDRESS"
  timeout 5 dbus-send --session --print-reply --dest=org.freedesktop.DBus / org.freedesktop.DBus.ListNames 2>&1 | tr -s ' \n' ' ' | cut -c1-300 | sed 's/^/   /'
  echo
}

log() { sed -u "s/^/[$1] /"; }

# Χωρίς xfce4-session: ξεκινάμε τα κομμάτια του XFCE ένα-ένα
manual_xfce() {
  echo ">>> XFCE χειροκίνητα (χωρίς session manager)"
  xfsettingsd --replace 2>&1 | log xfsettingsd &
  sleep 1
  xfwm4 --replace 2>&1 | log xfwm4 &
  xfdesktop 2>&1 | log xfdesktop &
  xfce4-panel 2>&1 | log panel &
  sleep 3
  echo ">>> [διάγνωση] διεργασίες:"
  ps -eo pid,args | grep -E "xfwm4|xfdesktop|xfce4-panel|xfsettingsd" | grep -v grep | grep -v sed | sed 's/^/   /'
  sleep 5
  python3 /usr/local/bin/freenux-xtest --tree 2>&1
  diag_stuck
  wait
}

while [ -e /tmp/.X11-unix/X0 ]; do
  eval "$(dbus-launch --sh-syntax)"
  export DBUS_SESSION_BUS_ADDRESS
  xfce4-session 2>&1 | tee /tmp/xfce.log | log xfce &
  SESS=$!
  # Αν σε 15'' δεν έχει ανοίξει το xfwm4, η συνεδρία κόλλησε
  for i in $(seq 1 15); do pgrep -x xfwm4 >/dev/null && break; sleep 1; done
  if pgrep -x xfwm4 >/dev/null; then
    echo ">>> XFCE ξεκίνησε ✓"
    sleep 5; python3 /usr/local/bin/freenux-xtest --tree 2>&1
    wait $SESS
  else
    echo ">>> Το xfce4-session κόλλησε – εναλλακτική εκκίνηση"
    pkill -x xfce4-session 2>/dev/null
    manual_xfce
  fi
  kill "$DBUS_SESSION_BUS_PID" 2>/dev/null
  echo ">>> Η συνεδρία XFCE έκλεισε – επανεκκίνηση"
  sleep 1
done
