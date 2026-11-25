#!/bin/bash
# Freenux: εμφάνιση Linux Mint σε XFCE (θέματα + εικονίδια Mint-Y, κάτω panel με μενού)
# Τρέχει μία φορά (marker), και σε παλιές εγκαταστάσεις.
set -e
M=/root/.freenux/mint.done
[ -f "$M" ] && exit 0
export DEBIAN_FRONTEND=noninteractive
echo ">>> Εμφάνιση Mint: κατέβασμα θεμάτων/εικονιδίων"

apt-get install -y gtk2-engines-murrine gtk2-engines-pixbuf fonts-noto-ui-core 2>/dev/null || true

T=$(mktemp -d)
curl -fL --retry 3 https://codeload.github.com/linuxmint/mint-themes/tar.gz/refs/heads/master -o "$T/themes.tgz"
tar xzf "$T/themes.tgz" -C "$T" --wildcards '*/files/usr/share/themes/Mint-Y*'
cp -a "$T"/mint-themes-master/files/usr/share/themes/Mint-Y* /usr/share/themes/

curl -fL --retry 3 https://codeload.github.com/linuxmint/mint-y-icons/tar.gz/refs/heads/master -o "$T/icons.tgz"
tar xzf "$T/icons.tgz" -C "$T" --wildcards '*/usr/share/icons/Mint-Y/*' '*/usr/share/icons/Mint-Y-Aqua/*'
cp -a "$T"/mint-y-icons-master/usr/share/icons/Mint-Y "$T"/mint-y-icons-master/usr/share/icons/Mint-Y-Aqua /usr/share/icons/
rm -rf "$T"
gtk-update-icon-cache -f /usr/share/icons/Mint-Y 2>/dev/null || true
gtk-update-icon-cache -f /usr/share/icons/Mint-Y-Aqua 2>/dev/null || true

X=/root/.config/xfce4/xfconf/xfce-perchannel-xml
mkdir -p "$X"

cat > "$X/xsettings.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="Mint-Y-Dark-Aqua"/>
    <property name="IconThemeName" type="string" value="Mint-Y-Aqua"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="FontName" type="string" value="Noto Sans 10"/>
    <property name="MonospaceFontName" type="string" value="Noto Sans Mono 10"/>
  </property>
  <property name="Xft" type="empty">
    <property name="Antialias" type="int" value="1"/>
    <property name="HintStyle" type="string" value="hintslight"/>
    <property name="RGBA" type="string" value="rgb"/>
  </property>
</channel>
EOF

cat > "$X/xfwm4.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
    <property name="theme" type="string" value="Default"/>
    <property name="title_font" type="string" value="Noto Sans Bold 10"/>
    <property name="button_layout" type="string" value="O|HMC"/>
  </property>
</channel>
EOF

# Ένα panel κάτω όπως στο Mint: μενού | παράθυρα | tray | ώρα | έξοδος
cat > "$X/xfce4-panel.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-panel" version="1.0">
  <property name="configver" type="int" value="2"/>
  <property name="panels" type="array">
    <value type="int" value="1"/>
    <property name="dark-mode" type="bool" value="true"/>
    <property name="panel-1" type="empty">
      <property name="position" type="string" value="p=8;x=0;y=0"/>
      <property name="length" type="uint" value="100"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="size" type="uint" value="40"/>
      <property name="icon-size" type="uint" value="24"/>
      <property name="plugin-ids" type="array">
        <value type="int" value="1"/>
        <value type="int" value="2"/>
        <value type="int" value="3"/>
        <value type="int" value="4"/>
        <value type="int" value="5"/>
        <value type="int" value="6"/>
        <value type="int" value="7"/>
      </property>
    </property>
  </property>
  <property name="plugins" type="empty">
    <property name="plugin-1" type="string" value="whiskermenu"/>
    <property name="plugin-2" type="string" value="separator"/>
    <property name="plugin-3" type="string" value="tasklist">
      <property name="flat-buttons" type="bool" value="true"/>
    </property>
    <property name="plugin-4" type="string" value="separator">
      <property name="expand" type="bool" value="true"/>
      <property name="style" type="uint" value="0"/>
    </property>
    <property name="plugin-5" type="string" value="systray"/>
    <property name="plugin-6" type="string" value="clock">
      <property name="digital-format" type="string" value="%H:%M"/>
    </property>
    <property name="plugin-7" type="string" value="actions"/>
  </property>
</channel>
EOF

# Φόντο: σκούρο Mint χρώμα (χωρίς βαριά εικόνα για ταχύτητα)
cat > "$X/xfce4-desktop.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-desktop" version="1.0">
  <property name="backdrop" type="empty">
    <property name="screen0" type="empty">
      <property name="monitorVNC-0" type="empty">
        <property name="workspace0" type="empty">
          <property name="image-style" type="int" value="0"/>
          <property name="color-style" type="int" value="0"/>
          <property name="rgba1" type="array">
            <value type="double" value="0.168627"/>
            <value type="double" value="0.243137"/>
            <value type="double" value="0.235294"/>
            <value type="double" value="1.000000"/>
          </property>
        </property>
      </property>
    </property>
  </property>
</channel>
EOF

mkdir -p /root/.freenux && touch "$M"
echo ">>> Εμφάνιση Mint εφαρμόστηκε"
