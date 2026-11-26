#!/bin/bash
# Freenux: εργασίες μέσα στη συνεδρία XFCE (έχουν DISPLAY και DBUS).
# Τρέχει από το autostart του XFCE.

# 1) Ειδοποιήσεις του Android → φούσκες του XFCE
(
  F=/tmp/freenux-notify
  touch "$F"
  tail -n0 -F "$F" 2>/dev/null | while IFS=$'\t' read -r app title text; do
    command -v notify-send >/dev/null || continue
    notify-send -a "$app" -i smartphone "$app: $title" "$text"
  done
) &

# 2) Κλίμακα ανάλογα με την οθόνη: κινητό ή 4K → διπλάσια, μόνιτορ 1080p/1440p → κανονική
(
  [ -f /root/.freenux/noscale ] && exit 0
  last=""
  while sleep 3; do
    cur=$(xrandr --current 2>/dev/null | sed -n '1s/.*current \([0-9]*\) x \([0-9]*\).*/\1 \2/p')
    [ -z "$cur" ] && continue
    [ "$cur" = "$last" ] && continue
    last="$cur"
    set -- $cur; w=$1; h=$2
    max=$(( w > h ? w : h )); min=$(( w > h ? h : w ))
    if [ "$min" -ge 2000 ] || { [ "$max" -ge 2400 ] && [ $(( max * 10 / min )) -ge 17 ]; }; then f=2; else f=1; fi
    xfconf-query -c xsettings -p /Gdk/WindowScalingFactor -n -t int -s $f
    if [ $f -eq 2 ]; then xfconf-query -c xfwm4 -p /general/theme -n -t string -s Default-xhdpi
    else xfconf-query -c xfwm4 -p /general/theme -n -t string -s Default; fi
    echo "Freenux: οθόνη ${w}x${h} → κλίμακα x$f" >> /tmp/freenux-session.log
  done
) &

# 3) Εμφάνιση Freenux (μία φορά): φόντο σε όλες τις οθόνες + εικονίδιο στο μενού
if [ ! -f /root/.freenux/brand.v1 ] && [ -f /usr/share/backgrounds/freenux/freenux.jpg ]; then
  sleep 6
  for mon in $(xrandr --listmonitors 2>/dev/null | awk 'NR>1{print $NF}'); do
    b=/backdrop/screen0/monitor$mon/workspace0
    xfconf-query -c xfce4-desktop -p $b/last-image -n -t string -s /usr/share/backgrounds/freenux/freenux.jpg
    xfconf-query -c xfce4-desktop -p $b/image-style -n -t int -s 5
  done
  W=/root/.config/xfce4/panel/whiskermenu-1.rc
  mkdir -p "$(dirname "$W")"; touch "$W"
  if grep -q '^button-icon=' "$W"; then sed -i 's|^button-icon=.*|button-icon=freenux|' "$W"; else echo 'button-icon=freenux' >> "$W"; fi
  touch /root/.freenux/brand.v1
  xfce4-panel -r >/dev/null 2>&1 &
fi

wait
