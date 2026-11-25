#!/bin/bash
# Freenux ενημέρωση v2: εφαρμογή Claude, θέματα, εικονίδια, φόντα, native X11
set -e
V=/root/.freenux/update.v3
[ -f "$V" ] && { echo ">>> Όλα ενημερωμένα"; exit 0; }
export DEBIAN_FRONTEND=noninteractive
mkdir -p /root/.freenux

if [ ! -f /root/.freenux/update.v2 ]; then

echo ">>> Ενημέρωση: πακέτα"
apt-get update
apt-get install -y xkb-data x11-xserver-utils x11-utils
for p in chromium arc-theme papirus-icon-theme numix-gtk-theme numix-icon-theme \
         gnome-backgrounds desktop-base xfce4-screenshooter plank; do
  apt-get install -y "$p" || echo "(παράλειψη $p)"
done

echo ">>> Όλα τα χρώματα εικονιδίων Mint-Y"
T=$(mktemp -d)
curl -fL --retry 3 https://codeload.github.com/linuxmint/mint-y-icons/tar.gz/refs/heads/master -o "$T/icons.tgz"
tar xzf "$T/icons.tgz" -C "$T" --wildcards '*/usr/share/icons/Mint-Y*'
cp -a "$T"/mint-y-icons-master/usr/share/icons/Mint-Y* /usr/share/icons/
rm -rf "$T"
for d in /usr/share/icons/Mint-Y*; do gtk-update-icon-cache -f "$d" 2>/dev/null || true; done

echo ">>> Εφαρμογή Claude (παράθυρο εφαρμογής, όχι καρτέλα browser)"
cat > /usr/local/bin/claude-app <<'EOF'
#!/bin/bash
exec chromium --no-sandbox --test-type --password-store=basic \
  --user-data-dir="$HOME/.config/claude-app" --class=ClaudeApp \
  --app=https://claude.ai "$@"
EOF
chmod 755 /usr/local/bin/claude-app

mkdir -p /usr/share/icons/hicolor/scalable/apps
cat > /usr/share/icons/hicolor/scalable/apps/freenux-claude.svg <<'EOF'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <rect x="2" y="2" width="60" height="60" rx="14" fill="#D97757"/>
  <path d="M16 20h32a4 4 0 0 1 4 4v14a4 4 0 0 1-4 4H30l-9 8v-8h-5a4 4 0 0 1-4-4V24a4 4 0 0 1 4-4z" fill="#FFF7EF"/>
</svg>
EOF
gtk-update-icon-cache -f /usr/share/icons/hicolor 2>/dev/null || true

cat > /usr/share/applications/claude-app.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Claude
Comment=Claude σε δικό του παράθυρο
Exec=claude-app
Icon=freenux-claude
StartupWMClass=ClaudeApp
Categories=Network;Office;
EOF

# Αγαπημένα στο μενού (μόνο αν δεν τα έχεις αλλάξει)
W=/root/.config/xfce4/panel/whiskermenu-1.rc
mkdir -p "$(dirname "$W")"
[ -f "$W" ] || cat > "$W" <<'EOF'
favorites=claude-app.desktop,claude-code.desktop,firefox-esr.desktop,xfce4-terminal.desktop,thunar.desktop,xfce-settings-manager.desktop
EOF

echo ">>> Φόντα"
cat > /usr/local/bin/freenux-wallpaper <<'EOF'
#!/bin/bash
# Βάζει φόντο την πρώτη φορά σε κάθε οθόνη (μετά αλλάζεις ελεύθερα)
sleep 4
IMG=/usr/share/images/desktop-base/default
[ -e "$IMG" ] || IMG=$(ls /usr/share/backgrounds/gnome/*.{jpg,png,jxl} 2>/dev/null | head -1)
[ -n "$IMG" ] || exit 0
for mon in $(xrandr --listmonitors 2>/dev/null | awk 'NR>1{print $NF}'); do
  M=/root/.freenux/wallpaper.$mon
  [ -f "$M" ] && continue
  b=/backdrop/screen0/monitor$mon/workspace0
  xfconf-query -c xfce4-desktop -p $b/last-image -n -t string -s "$IMG"
  xfconf-query -c xfce4-desktop -p $b/image-style -n -t int -s 5
  touch "$M"
done
EOF
chmod 755 /usr/local/bin/freenux-wallpaper
mkdir -p /root/.config/autostart
cat > /root/.config/autostart/freenux-wallpaper.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Freenux φόντο
Exec=freenux-wallpaper
NoDisplay=true
EOF

touch /root/.freenux/update.v2
fi

echo ">>> Ενημέρωση: ήχος (PulseAudio + ένταση στο panel)"
apt-get update
apt-get install -y pulseaudio pulseaudio-utils pavucontrol xfce4-pulseaudio-plugin

apt-get clean
touch "$V"
echo ">>> ✓ Ενημέρωση ολοκληρώθηκε"
