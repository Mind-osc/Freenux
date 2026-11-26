#!/bin/bash
# Freenux: εφαρμογές Windows = Box64 (x86_64 → ARM64) + Wine (Kron4ek, amd64-wow64)
export DEBIAN_FRONTEND=noninteractive

echo ">>> Windows: Box64"
if ! command -v box64 >/dev/null; then
  # Έκδοση για Android (-DBAD_SIGNAL=ON) από το αποθετήριο Pi-Apps-Coders, αλλιώς του Debian
  apt-get install -y gpg ca-certificates >/dev/null
  if curl -fsSL https://pi-apps-coders.github.io/box64-debs/KEY.gpg | gpg --dearmor -o /usr/share/keyrings/box64-archive-keyring.gpg; then
    cat > /etc/apt/sources.list.d/box64.sources <<'EOF'
Types: deb
URIs: https://Pi-Apps-Coders.github.io/box64-debs/debian
Suites: ./
Signed-By: /usr/share/keyrings/box64-archive-keyring.gpg
EOF
    apt-get update && apt-get install -y box64-android
  fi
  command -v box64 >/dev/null || apt-get install -y box64
fi
command -v box64 >/dev/null || { echo "✗ Το Box64 δεν μπήκε"; exit 1; }
box64 --version 2>/dev/null | head -1

echo ">>> Windows: Wine (τελευταία έκδοση amd64-wow64)"
python3 - <<'PY' || { echo "✗ Το Wine δεν κατέβηκε"; exit 1; }
import hashlib, json, os, re, subprocess, urllib.request
req = urllib.request.Request("https://api.github.com/repos/Kron4ek/Wine-Builds/releases?per_page=10",
                             headers={"User-Agent": "Freenux"})
for rel in json.load(urllib.request.urlopen(req, timeout=60)):
    for a in rel["assets"]:
        n = a["name"]
        if re.fullmatch(r"wine-[0-9.]+-amd64-wow64\.tar\.xz", n):
            if os.path.exists("/opt/wine/.version") and open("/opt/wine/.version").read().strip() == n:
                print("   ήδη εγκατεστημένο:", n); raise SystemExit(0)
            m = re.search(r"([0-9a-f]{64})\s+" + re.escape(n), rel.get("body") or "")
            print("   λήψη", n, "(%d MB)" % (a["size"] >> 20))
            path = "/tmp/" + n
            h = hashlib.sha256()
            with urllib.request.urlopen(urllib.request.Request(a["browser_download_url"], headers={"User-Agent": "Freenux"}), timeout=120) as r, open(path, "wb") as f:
                while True:
                    b = r.read(1 << 16)
                    if not b: break
                    h.update(b); f.write(b)
            if m and h.hexdigest() != m.group(1):
                raise SystemExit("λάθος checksum")
            subprocess.run(["rm", "-rf", "/opt/wine.new"], check=True)
            os.makedirs("/opt/wine.new")
            subprocess.run(["tar", "-xJf", path, "-C", "/opt/wine.new", "--strip-components=1"], check=True)
            subprocess.run(["rm", "-rf", "/opt/wine", path])
            os.rename("/opt/wine.new", "/opt/wine")
            open("/opt/wine/.version", "w").write(n)
            print("   ✓", n); raise SystemExit(0)
raise SystemExit("δεν βρέθηκε έκδοση")
PY

# Εντολές: wine, winecfg, wineserver (μέσω Box64)
for c in wine wineserver winecfg wineboot; do
  case $c in
    winecfg|wineboot) target="/opt/wine/bin/wine $c" ;;
    *) target="/opt/wine/bin/$c" ;;
  esac
  cat > /usr/local/bin/$c <<EOF
#!/bin/bash
export WINEPREFIX="\${WINEPREFIX:-/root/.wine}" WINEDEBUG="\${WINEDEBUG:--all}" BOX64_LOG=0 BOX64_NOBANNER=1
exec box64 $target "\$@"
EOF
  chmod 755 /usr/local/bin/$c
done

# Εικονίδια και άνοιγμα .exe/.msi με διπλό κλικ
cat > /usr/share/applications/freenux-wine-run.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Εφαρμογή Windows (Wine)
Comment=Άνοιγμα αρχείων .exe και .msi
Exec=wine start /unix %f
Icon=wine
NoDisplay=true
MimeType=application/x-ms-dos-executable;application/x-msdownload;application/x-msi;application/x-ms-shortcut;application/vnd.microsoft.portable-executable;
EOF
cat > /usr/share/applications/freenux-wine-explorer.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Windows: Εξερευνητής
Comment=Αρχεία του «υπολογιστή Windows» (C:)
Exec=wine explorer
Icon=wine
Categories=System;
EOF
cat > /usr/share/applications/freenux-winecfg.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Windows: Ρυθμίσεις (winecfg)
Exec=winecfg
Icon=wine
Categories=System;Settings;
EOF
for t in application/x-ms-dos-executable application/x-msdownload application/x-msi application/vnd.microsoft.portable-executable; do
  xdg-mime default freenux-wine-run.desktop $t 2>/dev/null || true
done

echo ">>> Windows: πρώτη ρύθμιση (C:, μητρώο – λίγα λεπτά)"
DISPLAY=${DISPLAY:-:0} timeout 900 wineboot -i >/dev/null 2>&1 && echo "   ✓ έτοιμο" || echo "   (θα ολοκληρωθεί στο πρώτο άνοιγμα)"
echo ">>> ✓ Windows: διπλό κλικ σε .exe για να ανοίξει, ή Μενού → Windows: Εξερευνητής"
