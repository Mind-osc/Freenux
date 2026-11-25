# Freenux

Debian + XFCE στο Android (Galaxy S24 Ultra). Συνδέεις εξωτερική οθόνη → ανοίγει μόνο του το Linux desktop.

## Τι έχει
- Debian (trixie, arm64) μέσω proot — χωρίς root
- XFCE desktop, Firefox ESR, ίντερνετ κανονικά
- Κουμπί για **Claude Code** (επίσημος installer) + συντόμευση claude.ai στο μενού
- Αρχεία κινητού μέσα στο Linux στο `/sdcard`
- Αυτόματο άνοιγμα σε εξωτερική οθόνη / DeX, ανάλυση = ανάλυση οθόνης

## Χρήση
1. Κατέβασε το APK από **Actions → Build APK → Freenux-apk**.
2. Άνοιξε → «Άδειες» → δώσε όλες.
3. «① Εγκατάσταση» (Wi-Fi, ~1–1.5 GB, 10–20 λεπτά).
4. (Προαιρετικά) «Εγκατάσταση Claude Code» → στο desktop: τερματικό → `claude`.
5. Σύνδεσε οθόνη + πληκτρολόγιο/ποντίκι.

## Όρια v1
- Η εικόνα περνάει μέσω VNC (noVNC): ~30–60 fps, όχι για παιχνίδια/βίντεο.
- proot: χωρίς systemd/Docker.
- v2: native X11 + GPU (Turnip/Zink), touchpad από το κινητό.
