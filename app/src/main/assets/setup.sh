#!/bin/bash
# Freenux: πρώτη ρύθμιση Debian + XFCE (τρέχει μέσα στο proot)
set -e
export DEBIAN_FRONTEND=noninteractive
export LANG=C.UTF-8
mkdir -p /etc/apt/apt.conf.d /root/.freenux

cat > /etc/apt/apt.conf.d/99freenux <<'EOF'
APT::Sandbox::User "root";
Dpkg::Use-Pty "0";
APT::Install-Recommends "false";
Acquire::Retries "3";
EOF

echo ">>> apt update"
apt-get update
echo ">>> Αναβάθμιση βασικού συστήματος"
apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold full-upgrade

CORE="xfce4 xfce4-terminal xfce4-whiskermenu-plugin xfce4-notifyd thunar mousepad dbus-x11 xfonts-base \
tigervnc-standalone-server novnc python3-websockify firefox-esr fonts-noto-core fonts-noto-color-emoji \
locales sudo curl wget git nano ca-certificates xdg-utils procps"
echo ">>> Εγκατάσταση desktop (αργεί λίγο)"
apt-get install -y $CORE

for p in xfce4-taskmanager ristretto htop file-roller unzip elementary-xfce-icon-theme; do
  apt-get install -y "$p" || echo "(παράλειψη $p)"
done

sed -i -E 's/^# *(en_US.UTF-8 UTF-8)/\1/; s/^# *(el_GR.UTF-8 UTF-8)/\1/' /etc/locale.gen || true
locale-gen || true

cat > /etc/profile.d/freenux.sh <<'EOF'
export MOZ_FAKE_NO_SANDBOX=1
export MOZ_DISABLE_CONTENT_SANDBOX=1
export MOZ_DISABLE_GMP_SANDBOX=1
export PATH="$HOME/.local/bin:$PATH"
EOF

update-alternatives --set x-www-browser /usr/bin/firefox-esr 2>/dev/null || true

cat > /usr/share/applications/claude-web.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Claude (web)
Exec=firefox-esr https://claude.ai
Icon=firefox-esr
Categories=Network;
EOF

# Χωρίς compositing = πιο γρήγορο πάνω από VNC
X=/root/.config/xfce4/xfconf/xfce-perchannel-xml
mkdir -p "$X"
[ -f "$X/xfwm4.xml" ] || cat > "$X/xfwm4.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
  </property>
</channel>
EOF

apt-get clean
touch /root/.freenux/setup.done
echo ">>> ΕΤΟΙΜΟ. Πάτα «Εκκίνηση desktop» ή σύνδεσε οθόνη."
