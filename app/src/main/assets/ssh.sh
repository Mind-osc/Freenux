#!/bin/bash
# Freenux: πρόσβαση από άλλο υπολογιστή με SSH (dropbear, θύρα 8022). Γίνεται source.
pkill -x dropbear 2>/dev/null
if [ -f /root/.freenux/ssh.on ]; then
  command -v dropbear >/dev/null || DEBIAN_FRONTEND=noninteractive apt-get install -y -q dropbear-bin >/dev/null 2>&1
  if [ -f /root/.freenux/ssh.pass ]; then
    echo "root:$(cat /root/.freenux/ssh.pass)" | chpasswd && echo ">>> SSH: ο κωδικός ορίστηκε"
    rm -f /root/.freenux/ssh.pass
  fi
  mkdir -p /etc/dropbear
  if dropbear -R -E -p 8022 -P /tmp/dropbear.pid 2>/tmp/dropbear.log; then
    echo ">>> SSH ✓ στη θύρα 8022 (χρήστης root)"
  else
    echo ">>> SSH δεν ξεκίνησε:"; tail -3 /tmp/dropbear.log
  fi
fi
