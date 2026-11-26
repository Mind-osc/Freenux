#!/bin/bash
# Freenux: πακέτα εφαρμογών με ένα κλικ. Χρήση: freenux-bundles office graphics dev media
export DEBIAN_FRONTEND=noninteractive
apt-get update
ok=0
for b in "$@"; do
  case "$b" in
    office)
      echo ">>> Γραφείο: LibreOffice (με ελληνικά)"
      apt-get install -y libreoffice libreoffice-gtk3 libreoffice-l10n-el hunspell-el && ok=$((ok+1)) ;;
    graphics)
      echo ">>> Γραφίστας: GIMP, Inkscape, Krita"
      apt-get install -y gimp inkscape krita && ok=$((ok+1)) ;;
    dev)
      echo ">>> Προγραμματιστής: Git, Python, Node, εργαλεία μεταγλώττισης, VS Code"
      apt-get install -y git python3 python3-pip python3-venv nodejs npm build-essential
      if curl -fL --retry 3 "https://code.visualstudio.com/sha/download?build=stable&os=linux-deb-arm64" -o /tmp/code.deb \
         && apt-get install -y /tmp/code.deb; then
        # Μέσα στο proot: χωρίς sandbox και με φάκελο δεδομένων για root
        for f in /usr/share/applications/code*.desktop; do
          sed -i 's|^Exec=/usr/share/code/code|Exec=/usr/share/code/code --no-sandbox --user-data-dir=/root/.vscode-root|' "$f"
        done
        ok=$((ok+1))
      else
        echo "(το VS Code δεν μπήκε)"
      fi
      rm -f /tmp/code.deb ;;
    windows)
      bash /usr/local/bin/freenux-windows && ok=$((ok+1)) ;;
    media)
      echo ">>> Ψυχαγωγία: VLC"
      apt-get install -y vlc && ok=$((ok+1)) ;;
  esac
done
apt-get clean
echo ">>> ✓ Πακέτα: $ok από $# εγκαταστάθηκαν"
