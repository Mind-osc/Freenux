#!/usr/bin/env bash
# Κατεβάζει το proot (aarch64) από το Termux και το βάζει στα jniLibs ως lib*.so
set -euo pipefail
REPO=https://packages.termux.dev/apt/termux-main
OUT=app/src/main/jniLibs/arm64-v8a
WORK=$(mktemp -d)

IDX="$REPO/dists/stable/main/binary-aarch64"
if ! curl -fsSL "$IDX/Packages" -o "$WORK/Packages"; then
  curl -fsSL "$IDX/Packages.gz" | gunzip > "$WORK/Packages" || curl -fsSL "$IDX/Packages.xz" | xz -d > "$WORK/Packages"
fi

pkgfile() { awk -v p="$1" '$1=="Package:"{cur=$2} $1=="Filename:" && cur==p {print $2; exit}' "$WORK/Packages"; }

for p in proot libtalloc; do
  f=$(pkgfile "$p"); [ -n "$f" ] || { echo "Δεν βρέθηκε πακέτο $p"; exit 1; }
  echo "→ $p: $f"
  curl -fsSL "$REPO/$f" -o "$WORK/$p.deb"
  dpkg-deb -x "$WORK/$p.deb" "$WORK/root"
done

P="$WORK/root/data/data/com.termux/files/usr"
mkdir -p "$OUT"
cp -L "$P/bin/proot" "$OUT/libproot.so"
cp -L "$P/libexec/proot/loader" "$OUT/libproot-loader.so"
cp -L "$(ls "$P"/lib/libtalloc.so* | head -1)" "$OUT/libtalloc.so"

patchelf --set-soname libtalloc.so "$OUT/libtalloc.so"
for n in $(patchelf --print-needed "$OUT/libproot.so" | grep talloc); do
  patchelf --replace-needed "$n" libtalloc.so "$OUT/libproot.so"
done
patchelf --remove-rpath "$OUT/libproot.so" || true
chmod 755 "$OUT"/*.so

echo "NEEDED του proot:"; patchelf --print-needed "$OUT/libproot.so"
ls -la "$OUT"
