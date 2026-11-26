#!/bin/bash
# Freenux: φάκελοι του κινητού στο Linux (Αρχική + σελιδοδείκτες του Thunar). Γίνεται source.
if [ -d /sdcard ] && [ -r /sdcard ]; then
  B=/root/.config/gtk-3.0/bookmarks
  mkdir -p "$(dirname "$B")"; touch "$B"
  for pair in "Κινητό:/sdcard" "Λήψεις:/sdcard/Download" "Εικόνες:/sdcard/DCIM" \
              "Φωτογραφίες οθόνης:/sdcard/Pictures/Screenshots" "Έγγραφα:/sdcard/Documents" "Μουσική:/sdcard/Music"; do
    n=${pair%%:*}; t=${pair#*:}
    [ -e "$t" ] || continue
    [ -e "/root/$n" ] || [ -L "/root/$n" ] || ln -s "$t" "/root/$n"
    grep -q "^file://$t " "$B" || echo "file://$t $n" >> "$B"
  done
fi

# Thunar: κρύβουμε την μπάρα «you are using the root account» (ο root εδώ είναι ψεύτικος, του proot)
CSS=/root/.config/gtk-3.0/gtk.css
mkdir -p "$(dirname "$CSS")"; touch "$CSS"
grep -q "freenux-root-warning" "$CSS" || cat >> "$CSS" <<'EOF'
/* freenux-root-warning */
.thunar infobar.warning, .thunar .warning { font-size: 0; min-height: 0; margin: -30px 0 0 0; padding: 0; opacity: 0; }
EOF

# Chromium: ως root (ψεύτικος, του proot) δεν ξεκινάει χωρίς --no-sandbox
if [ -d /etc/chromium.d ]; then
  cat > /etc/chromium.d/freenux <<'EOF'
# Freenux: ο root εδώ είναι του proot· το Chromium αρνείται να τρέξει ως root με sandbox
export CHROMIUM_FLAGS="$CHROMIUM_FLAGS --no-sandbox --test-type --password-store=basic"
EOF
fi
