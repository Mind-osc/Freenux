#!/bin/bash
# Freenux: XFCE πάνω στον native X server του Termux:X11 (DISPLAY=:0), χωρίς VNC
export DISPLAY=:0 HOME=/root USER=root LANG=C.UTF-8
export MOZ_FAKE_NO_SANDBOX=1 MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1
export PATH="/root/.local/bin:$PATH"
export XDG_RUNTIME_DIR=/tmp/runtime-root
export XDG_CURRENT_DESKTOP=XFCE XDG_SESSION_TYPE=x11 XDG_SESSION_DESKTOP=xfce
mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"

bash /usr/local/bin/freenux-mint || echo "(η εμφάνιση Mint θα ξαναδοκιμαστεί στην επόμενη εκκίνηση)"
pkill -f xfce4-session 2>/dev/null

for i in $(seq 1 150); do [ -e /tmp/.X11-unix/X0 ] && break; sleep 0.2; done
[ -e /tmp/.X11-unix/X0 ] && echo ">>> X socket OK" || echo ">>> ΣΦΑΛΜΑ: δεν βρέθηκε /tmp/.X11-unix/X0"
ls -la /tmp/.X11-unix/ 2>&1
echo ">>> XFCE στο Termux:X11"

# Διάγνωση: τι τρέχει μετά από 10 δευτερόλεπτα
(
  sleep 10
  echo ">>> [διάγνωση] διεργασίες XFCE:"
  ps -eo pid,args | grep -E "xfce4-session|xfwm4|xfdesktop|xfce4-panel|xfsettingsd|dbus-daemon" | grep -v grep | sed 's/^/   /'
  [ -f /root/.xsession-errors ] && { echo ">>> [διάγνωση] .xsession-errors:"; tail -n 15 /root/.xsession-errors; }
) &

# Αν κάνεις Log Out ή κρασάρει, ξεκινάει πάλι μόνο του
while [ -e /tmp/.X11-unix/X0 ]; do
  dbus-launch --exit-with-session xfce4-session 2>&1 | tee /tmp/xfce.log | sed -u 's/^/[xfce] /' 
  echo ">>> Η συνεδρία XFCE έκλεισε – επανεκκίνηση"
  sleep 1
done
