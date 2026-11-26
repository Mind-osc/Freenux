#!/bin/bash
# Freenux ενημερώσεις: v2 Claude/θέματα/φόντα, v3 ήχος, v4 πληκτρολόγιο/κατάστημα, v5 driver GPU, v6 patched Mesa, v7 ειδοποιήσεις/SSH, v8 βίντεο/εμφάνιση
set -e
V=/root/.freenux/update.v8
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

if [ ! -f /root/.freenux/update.v3 ]; then
echo ">>> Ενημέρωση: ήχος (PulseAudio + ένταση στο panel)"
apt-get update
apt-get install -y pulseaudio pulseaudio-utils pavucontrol xfce4-pulseaudio-plugin
touch /root/.freenux/update.v3
fi

if [ ! -f /root/.freenux/update.v4 ]; then
echo ">>> Ενημέρωση: ελληνικό πληκτρολόγιο, κατάστημα εφαρμογών, backup, GPU"
apt-get update
apt-get install -y x11-xkb-utils xfce4-xkb-plugin synaptic pigz
for p in mesa-vulkan-drivers vulkan-tools mesa-utils; do apt-get install -y "$p" || echo "(παράλειψη $p)"; done

# Πληκτρολόγιο EN/ΕΛ με Alt+Shift (μόνο αν δεν το έχεις ρυθμίσει ήδη)
X=/root/.config/xfce4/xfconf/xfce-perchannel-xml
mkdir -p "$X"
[ -f "$X/keyboard-layout.xml" ] || cat > "$X/keyboard-layout.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="keyboard-layout" version="1.0">
  <property name="Default" type="empty">
    <property name="XkbDisable" type="bool" value="false"/>
    <property name="XkbLayout" type="string" value="us,gr"/>
    <property name="XkbVariant" type="string" value=","/>
    <property name="XkbOptions" type="empty">
      <property name="Group" type="string" value="grp:alt_shift_toggle"/>
    </property>
  </property>
</channel>
EOF
mkdir -p /root/.config/autostart
cat > /root/.config/autostart/freenux-keyboard.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Freenux πληκτρολόγιο
Exec=sh -c "setxkbmap -layout us,gr -option grp:alt_shift_toggle; sleep 8; [ -f /root/.freenux/xkb-panel ] || { xfce4-panel --add=xkb && touch /root/.freenux/xkb-panel; }"
NoDisplay=true
EOF

# Κατάστημα: Synaptic χωρίς pkexec (είμαστε ήδη root μέσα στο Linux)
cat > /usr/share/applications/freenux-store.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Κατάστημα εφαρμογών
Comment=Εγκατάσταση προγραμμάτων (Synaptic)
Exec=synaptic
Icon=synaptic
Categories=System;Settings;
EOF
mkdir -p /root/.local/share/applications
printf '[Desktop Entry]\nType=Application\nName=Synaptic\nNoDisplay=true\n' > /root/.local/share/applications/synaptic.desktop
touch /root/.freenux/update.v4
fi

if [ ! -f /root/.freenux/update.v5 ]; then
echo ">>> Ενημέρωση: driver GPU για Adreno (Turnip με KGSL, lfdevs/mesa-for-android-container)"
TD=$(mktemp -d)
DEB="$TD/mesa-vulkan-drivers_25.0.7-2+deb13u1_arm64_unpatched.deb"
URL="https://github.com/lfdevs/mesa-for-android-container/releases/download/debian%2F25.0.7-2-turnip/mesa-vulkan-drivers_25.0.7-2%2Bdeb13u1_arm64_unpatched.deb"
SUM=6ef4b243e2e253a294bf8a8f8857e7b13d6595a8e12662e1d7a7ef5fd873ec96
if curl -fL --retry 3 "$URL" -o "$DEB" && echo "$SUM  $DEB" | sha256sum -c - >/dev/null; then
  if apt-get install -y --allow-downgrades --no-remove "$DEB"; then
    apt-mark hold mesa-vulkan-drivers
    echo ">>> ✓ Driver GPU εγκαταστάθηκε (άνοιξε τον διακόπτη GPU και κάνε επανεκκίνηση desktop)"
  else
    echo "(ο driver GPU δεν μπήκε – συνεχίζουμε χωρίς)"
  fi
else
  echo "(ο driver GPU δεν κατέβηκε ή απέτυχε ο έλεγχος checksum – συνεχίζουμε χωρίς)"
fi
rm -rf "$TD"
touch /root/.freenux/update.v5
fi

if [ ! -f /root/.freenux/update.v6 ]; then
echo ">>> Ενημέρωση: patched Mesa (εικόνα GPU κατευθείαν στο Termux:X11)"
python3 /usr/local/bin/freenux-gpu-install
touch /root/.freenux/update.v6
fi

if [ ! -f /root/.freenux/update.v7 ]; then
echo ">>> Ενημέρωση: ειδοποιήσεις κινητού, SSH, κλίμακα οθόνης"
apt-get update
apt-get install -y libnotify-bin dropbear-bin x11-xserver-utils
touch /root/.freenux/update.v7
fi

echo ">>> Ενημέρωση: βίντεο με GPU (mpv) και εμφάνιση Freenux"
apt-get install -y mpv yt-dlp || apt-get install -y mpv
mkdir -p /root/.config/mpv
[ -f /root/.config/mpv/mpv.conf ] || cat > /root/.config/mpv/mpv.conf <<'EOF'
# Freenux: η εικόνα ζωγραφίζεται από την GPU, η αποκωδικοποίηση από τον επεξεργαστή
vo=gpu
gpu-context=x11
hwdec=no
profile=fast
ytdl-format=bestvideo[height<=?1080]+bestaudio/best
EOF
for t in video/mp4 video/x-matroska video/webm video/quicktime video/x-msvideo video/mpeg; do
  xdg-mime default mpv.desktop $t 2>/dev/null || true
done

apt-get clean
touch "$V"
echo ">>> ✓ Ενημέρωση ολοκληρώθηκε"
