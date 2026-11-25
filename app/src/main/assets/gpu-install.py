#!/usr/bin/env python3
"""Freenux: εγκατάσταση patched Mesa + Turnip για Adreno (lfdevs/mesa-for-android-container).

Βρίσκει την πιο πρόσφατη έκδοση Mesa για Debian 13 και το Turnip της ίδιας έκδοσης,
ελέγχει τα SHA-256 από τις σημειώσεις των releases, και τα αποσυμπιέζει στο /.
"""
import hashlib, json, os, re, subprocess, sys, tempfile, urllib.request

API = "https://api.github.com/repos/lfdevs/mesa-for-android-container/releases?per_page=40"
MARK = "/root/.freenux/mesa-patched"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Freenux", "Accept": "application/vnd.github+json"})
    return urllib.request.urlopen(req, timeout=60)


def sha_from_body(body, name):
    m = re.search(r"([0-9a-f]{64})\s+" + re.escape(name), body or "")
    return m.group(1) if m else None


def download(url, path, want):
    h = hashlib.sha256()
    with get(url) as r, open(path, "wb") as f:
        while True:
            b = r.read(1 << 16)
            if not b:
                break
            h.update(b)
            f.write(b)
    if want and h.hexdigest() != want:
        raise RuntimeError("λάθος checksum για " + os.path.basename(path))


def main():
    rels = json.load(get(API))
    mesa = turnip = None
    for r in rels:
        tag = r.get("tag_name", "")
        if mesa is None and tag.startswith("mesa-"):
            for a in r["assets"]:
                if a["name"].startswith("mesa-for-android-container_") and a["name"].endswith("_debian_trixie_arm64.tar.gz"):
                    mesa = (r, a)
                    break
        if mesa:
            break
    if not mesa:
        print("(δεν βρέθηκε patched Mesa για Debian 13)")
        return 1
    ver = mesa[1]["name"][len("mesa-for-android-container_"):-len("_debian_trixie_arm64.tar.gz")]
    for r in rels:
        for a in r["assets"]:
            if a["name"] == "turnip_%s_debian_trixie_arm64.tar.gz" % ver:
                turnip = (r, a)
    if os.path.exists(MARK) and open(MARK).read().strip() == ver:
        print(">>> Patched Mesa %s ήδη εγκατεστημένο" % ver)
        return 0

    print(">>> Patched Mesa %s%s" % (ver, " + Turnip" if turnip else ""))
    tmp = tempfile.mkdtemp()
    parts = [mesa] + ([turnip] if turnip else [])
    files = []
    for rel, asset in parts:
        want = sha_from_body(rel.get("body"), asset["name"])
        path = os.path.join(tmp, asset["name"])
        print("   λήψη %s (%d MB)%s" % (asset["name"], asset["size"] >> 20, "" if want else " – χωρίς checksum"))
        download(asset["browser_download_url"], path, want)
        files.append(path)

    # Να μην τα πατήσει το apt σε επόμενη αναβάθμιση
    pk = subprocess.run("dpkg-query -W -f='${Package}\\n' | grep -E '^(mesa-|libgl1-mesa|libglx-mesa|libegl-mesa|libgbm|libosmesa|libgles2-mesa|libd3dadapter)'",
                        shell=True, capture_output=True, text=True).stdout.split()
    if pk:
        subprocess.run(["apt-mark", "hold"] + pk, capture_output=True)

    for f in files:
        subprocess.run(["tar", "-xzf", f, "-C", "/"], check=True)
    subprocess.run(["ldconfig"])
    os.makedirs(os.path.dirname(MARK), exist_ok=True)
    open(MARK, "w").write(ver)
    subprocess.run(["rm", "-rf", tmp])
    print(">>> ✓ Patched Mesa εγκαταστάθηκε")
    return 0


try:
    sys.exit(main())
except Exception as e:
    print("(patched Mesa δεν μπήκε: %s – μένει ο driver που δουλεύει)" % e)
    sys.exit(0)
