#!/bin/bash
# Freenux: backup όλου του Linux στο /sdcard/Freenux (επιβιώνει από απεγκατάσταση)
D=/sdcard/Freenux
F=$D/freenux-backup.tar.gz
if ! mkdir -p "$D" 2>/dev/null || ! touch "$D/.test" 2>/dev/null; then
  echo "✗ Δεν έχω πρόσβαση στα αρχεία του κινητού – πάτα «Άδειες» και δώσε πρόσβαση στα αρχεία"
  exit 2
fi
rm -f "$D/.test"
apt-get clean 2>/dev/null
Z="gzip -1"; command -v pigz >/dev/null && Z="pigz -1"
echo ">>> Backup σε /sdcard/Freenux/freenux-backup.tar.gz (10–20 λεπτά, άφησε την εφαρμογή ανοιχτή)"
cd /
tar -c -I "$Z" -f "$F.part" \
  --exclude=./proc --exclude=./sys --exclude=./dev --exclude=./tmp \
  --exclude=./sdcard --exclude=./storage --exclude=./.freenux-proc \
  --exclude=./root/.cache --exclude=./var/cache/apt/archives \
  --warning=no-file-changed --warning=no-file-removed --ignore-failed-read .
rc=$?
if [ $rc -le 1 ]; then
  [ -f "$F" ] && mv -f "$F" "$D/freenux-backup.old.tar.gz"
  mv -f "$F.part" "$F"
  echo ">>> ✓ Backup έτοιμο: $(du -h "$F" | cut -f1)"
else
  rm -f "$F.part"
  echo "✗ Το backup απέτυχε (κωδικός $rc)"
  exit $rc
fi
