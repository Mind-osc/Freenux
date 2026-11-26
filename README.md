# Freenux

**Ολόκληρο Linux (Debian 13 + XFCE) στο κινητό.** Συνδέεις εξωτερική οθόνη και γίνεται PC.
Φτιαγμένο για Galaxy S24 Ultra (Snapdragon 8 Gen 3 / Adreno 750), χωρίς root.

**⬇ Κατέβασμα:** [Freenux.apk (τελευταία έκδοση)](https://github.com/Mind-osc/Freenux/releases/latest/download/Freenux.apk) · [Τι άλλαξε](CHANGELOG.md)

## Τι έχει

| | |
|---|---|
| 🐧 **Linux** | Debian 13 (trixie) arm64 μέσω proot, ίντερνετ κανονικά |
| 🖥️ **Desktop** | XFCE με εμφάνιση Linux Mint (Mint-Y), φόντο Freenux, panel κάτω |
| ⚡ **Οθόνη** | Native X11 μέσω [Termux:X11](https://github.com/termux/termux-x11) έως **120Hz** (ή VNC ως εναλλακτική) |
| 🎮 **GPU** | Adreno μέσω Turnip/freedreno πάνω σε KGSL ([lfdevs/mesa-for-android-container](https://github.com/lfdevs/mesa-for-android-container)), με αυτόματη επιστροφή σε Zink ή CPU |
| 🔊 **Ήχος** | PulseAudio → ηχεία/ακουστικά κινητού, ένταση στο panel |
| ⌨️ **Πληκτρολόγιο** | EN/ΕΛ με Alt+Shift |
| 🤖 **Claude** | Εφαρμογή Claude σε δικό της παράθυρο + Claude Code |
| 🎬 **Βίντεο** | mpv με GPU, κουμπί «Άνοιγμα στο mpv» στον Firefox, Stremio |
| 📦 **Εφαρμογές** | Κατάστημα (Synaptic) + πακέτα με ένα κλικ: Γραφείο, Γραφίστας, Προγραμματιστής (VS Code), VLC |
| 🔔 **Ειδοποιήσεις** | Οι ειδοποιήσεις του κινητού εμφανίζονται μέσα στο XFCE |
| 📁 **Αρχεία** | Λήψεις, Εικόνες, Έγγραφα του κινητού ως φάκελοι στο Linux |
| 💾 **Backup** | Όλο το Linux στο `/sdcard/Freenux`, επαναφορά, αυτόματο εβδομαδιαίο |
| 🔐 **SSH** | Σύνδεση από άλλο υπολογιστή στη θύρα 8022 |
| 📐 **Κλίμακα** | Αυτόματη ανά οθόνη (κινητό/4K ×2, μόνιτορ ×1) |
| 🧩 **Widget** | Desktop / Stop / Ρυθμίσεις από την αρχική οθόνη |
| 🖱️ **Touchpad** | Το κινητό ως ποντίκι και πληκτρολόγιο όταν το desktop είναι σε μόνιτορ |
| 🎙️ **Μικρόφωνο** | Το μικρόφωνο του κινητού ως είσοδος ήχου στο Linux |
| 🪟 **Windows** | Εφαρμογές .exe μέσω Box64 + Wine (πειραματικό) |

## Πρώτη εγκατάσταση

1. Εγκατάστησε το **Freenux.apk** και το **[Termux:X11](https://github.com/termux/termux-x11/releases/download/nightly/termux-x11-universal-release.apk)** (κουμπί μέσα στην εφαρμογή).
2. Άνοιξε το Freenux → **Άδειες** → δώσε όλες.
3. **① Εγκατάσταση / Ενημέρωση** (Wi-Fi, ~2 GB, 15–30 λεπτά).
4. Πάτα το εικονίδιο του Freenux → ανοίγει το desktop. Ή σύνδεσε οθόνη.

**Ρυθμίσεις:** παρατεταμένο πάτημα στο εικονίδιο → «Ρυθμίσεις», ή πάτημα στην ειδοποίηση.
**Ενημερώσεις:** νέο APK πάνω από το παλιό (κρατάει το Linux) και μετά **①**.

## Πώς δουλεύει

- **proot** (από το Termux, ως `libproot.so`) τρέχει το Debian χωρίς root.
- Ο **X server** του Termux:X11 τρέχει μέσα στη διεργασία του Freenux (`app_process` + `CmdEntryPoint`), με socket στο `/tmp` του Debian.
- Ο **ήχος** πάει PulseAudio → TCP 4656 → `AudioTrack` στο Android.
- Το **backup** γίνεται από τη μεριά του Android (commons-compress), ώστε να διαβάζονται και τα αρχεία `.l2s` του proot.
- Το APK χτίζεται στο GitHub Actions· το κλειδί υπογραφής είναι κρυπτογραφημένο και ο κωδικός υπάρχει μόνο στα Secrets.

## Όρια

- Χωρίς root: όχι systemd, Docker, Snap/Flatpak, δικό του kernel, άμεσο USB/Bluetooth.
- Το proot κάνει τις εγκαταστάσεις και τη μεταγλώττιση 2–5× πιο αργές από κανονικό PC.
- Ο Firefox/Chromium: το sandbox δοκιμάζεται αυτόματα και ανοίγει αν δουλεύει· αλλιώς χρησιμοποίησε το «Browser κινητού (ασφαλής)» για ευαίσθητα sites. uBlock Origin από προεπιλογή.
- Ήχος, μικρόφωνο, touchpad: μόνο μέσα από τον ιδιωτικό φάκελο της εφαρμογής (unix sockets)· VNC με κωδικό.
- Η GPU (patched Mesa) είναι κοινοτικός driver· αν κολλήσει, γυρίζει μόνο του σε Zink/CPU.

## Επόμενα

- Πολλά distros (Ubuntu, Arch, Fedora)
- Παιχνίδια Windows με DXVK/GPU
