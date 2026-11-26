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
