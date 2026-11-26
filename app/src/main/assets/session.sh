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

wait
