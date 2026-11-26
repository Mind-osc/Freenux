#!/bin/bash
# Freenux ήχος: PulseAudio μέσα στο Linux → unix socket στον ιδιωτικό φάκελο → εφαρμογή Android (AudioTrack)
# Γίνεται source από τα scripts του desktop (εξάγει PULSE_SERVER).
command -v pulseaudio >/dev/null || return 0 2>/dev/null || exit 0

mkdir -p /etc/freenux /etc/pulse/client.conf.d
cat > /etc/freenux/pulse.pa <<'EOF'
load-module module-native-protocol-unix auth-anonymous=1 socket=/tmp/pulse-socket
load-module module-null-sink sink_name=freenux rate=48000 channels=2 sink_properties=device.description=Ηχεία_κινητού
set-default-sink freenux
load-module module-simple-protocol-unix source=freenux.monitor record=true socket=/tmp/freenux-audio.sock rate=48000 format=s16le channels=2
load-module module-null-sink sink_name=phonemic rate=48000 channels=1 sink_properties=device.description=Είσοδος_μικροφώνου
load-module module-simple-protocol-unix sink=phonemic playback=true socket=/tmp/freenux-mic.sock rate=48000 format=s16le channels=1
load-module module-remap-source master=phonemic.monitor source_name=phone_mic source_properties=device.description=Μικρόφωνο_κινητού
set-default-source phone_mic
set-default-sink freenux
EOF
cat > /etc/pulse/client.conf.d/freenux.conf <<'EOF'
default-server = unix:/tmp/pulse-socket
autospawn = no
EOF

pkill -x pulseaudio 2>/dev/null; sleep 0.3; pkill -9 -x pulseaudio 2>/dev/null
# Παλιά αρχεία από προηγούμενη εκκίνηση κάνουν τον PulseAudio να νομίζει ότι τρέχει ήδη
rm -rf /tmp/pulse-socket /tmp/pulse-* /tmp/freenux-audio.sock /tmp/freenux-mic.sock "${XDG_RUNTIME_DIR:-/tmp/runtime-root}/pulse" /root/.config/pulse/*-runtime /root/.config/pulse/*.pid 2>/dev/null
pulseaudio -n -F /etc/freenux/pulse.pa --exit-idle-time=-1 --disallow-exit=yes --use-pid-file=no \
  --disable-shm=yes --realtime=no --high-priority=no --daemonize=yes \
  --log-target=stderr >/tmp/pulse.log 2>&1
export PULSE_SERVER=unix:/tmp/pulse-socket
for i in $(seq 1 25); do [ -e /tmp/pulse-socket ] && break; sleep 0.2; done
if [ -e /tmp/pulse-socket ]; then echo ">>> Ήχος ✓"
else echo ">>> Ο ήχος δεν ξεκίνησε:"; grep -v "not intended to be run as root" /tmp/pulse.log | tail -10 | sed 's/^/[ήχος] /'; fi

# Ρύθμιση έντασης στο panel (μία φορά)
if [ ! -f /root/.freenux/audio-panel ] && command -v xfce4-panel >/dev/null; then
  mkdir -p /root/.config/autostart
  cat > /root/.config/autostart/freenux-audio-panel.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Freenux ένταση στο panel
Exec=sh -c "sleep 8; [ -f /root/.freenux/audio-panel ] || { xfce4-panel --add=pulseaudio && touch /root/.freenux/audio-panel; }"
NoDisplay=true
EOF
fi
