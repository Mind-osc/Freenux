#!/bin/bash
# Freenux: δοκιμή αν Firefox / Chromium τρέχουν ΜΕ sandbox μέσα στο proot.
# Μία φορά ανά έκδοση browser. Αν περάσει, το sandbox μένει ανοιχτό από την επόμενη εκκίνηση.
D=/root/.freenux; mkdir -p $D
note() {
  echo "$1" >> /tmp/freenux-session.log
  printf '>>> %s\n' "$1" >> /tmp/freenux-log
  command -v notify-send >/dev/null && notify-send -a Freenux -i security-high "Freenux" "$1"
}
printf '>>> Δοκιμή sandbox browsers… (1–2 λεπτά)\n' >> /tmp/freenux-log

# --- Firefox
FFV=$(firefox-esr --version 2>/dev/null)
if [ -n "$FFV" ] && [ "$(cat $D/ff-sandbox.tested 2>/dev/null)" != "$FFV" ]; then
  T=$(mktemp -d)
  env -u MOZ_FAKE_NO_SANDBOX -u MOZ_DISABLE_CONTENT_SANDBOX -u MOZ_DISABLE_GMP_SANDBOX MOZ_HEADLESS=1 \
    timeout 150 firefox-esr --headless --no-remote --profile "$T" --screenshot "$T/s.png" \
    'data:text/html,<h1>Freenux</h1>' >"$T/log" 2>&1
  if [ -s "$T/s.png" ] && ! grep -qi "sandbox.*\(fail\|error\|violation\)\|crash" "$T/log"; then
    touch $D/ff-sandbox.ok; note "Firefox: sandbox ΕΝΕΡΓΟ ✓ – κάνε «Επανεκκίνηση desktop» για να ισχύσει"
  else
    rm -f $D/ff-sandbox.ok; note "Firefox: το sandbox δεν δουλεύει μέσα στο proot – μένει κλειστό (χρησιμοποίησε «Browser κινητού» για ευαίσθητα sites)"
    tail -5 "$T/log" >> /tmp/freenux-session.log
    grep -v '^\s*$' "$T/log" | tail -4 | sed 's/^/   [firefox] /' >> /tmp/freenux-log
  fi
  echo "$FFV" > $D/ff-sandbox.tested
  rm -rf "$T"
fi

# --- Chromium: ως root (proot) αρνείται το sandbox εκ κατασκευής· μένει --no-sandbox
