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

for p in proot libtalloc libandroid-shmem; do
  f=$(pkgfile "$p"); [ -n "$f" ] || { echo "Δεν βρέθηκε πακέτο $p"; exit 1; }
  echo "→ $p: $f"
  curl -fsSL "$REPO/$f" -o "$WORK/$p.deb"
  dpkg-deb -x "$WORK/$p.deb" "$WORK/root"
done

P="$WORK/root/data/data/com.termux/files/usr"
mkdir -p "$OUT"
cp -L "$P/bin/proot" "$OUT/libproot.so"
cp -L "$P/libexec/proot/loader" "$OUT/libproot-loader.so"
# Termux libs που χρειάζεται το proot → lib<όνομα>.so με σωστό soname
for lib in talloc android-shmem; do
  src=$(ls "$P"/lib/lib$lib.so* | head -1)
  cp -L "$src" "$OUT/lib$lib.so"
  patchelf --set-soname "lib$lib.so" "$OUT/lib$lib.so"
  patchelf --remove-rpath "$OUT/lib$lib.so" || true
  for n in $(patchelf --print-needed "$OUT/libproot.so" | grep -F "lib$lib.so"); do
    [ "$n" = "lib$lib.so" ] || patchelf --replace-needed "$n" "lib$lib.so" "$OUT/libproot.so"
  done
done
patchelf --remove-rpath "$OUT/libproot.so" || true
chmod 755 "$OUT"/*.so

echo "NEEDED του proot:"; patchelf --print-needed "$OUT/libproot.so"
ls -la "$OUT"
