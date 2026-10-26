#!/usr/bin/env python3
"""
Buat "paket legacy" (zip) dari salinan Half-Life 2 versi LAMA milikmu sendiri
(Steam > Half-Life 2 > Properties > Betas > steam_legacy), untuk dipakai di launcher Android.

Skrip ini membaca file VPK (*_dir.vpk, v1/v2) dan file lepas, lalu menyalin hanya file yang
awalan path-nya cocok (bawaan: shaders/) ke satu zip. File Valve TIDAK ikut tersimpan di repo ini.

Contoh:
  python3 make_legacy_pack.py "D:/Steam/steamapps/common/Half-Life 2" -o legacy-pack.zip
  python3 make_legacy_pack.py "<folder game>" --list                  # hanya lihat isi, tanpa membuat zip
  python3 make_legacy_pack.py "<folder game>" -o pack.zip --include shaders/ --include scripts/ChapterBackgrounds.txt

Setelah jadi, skrip mencetak SHA-256 zip. Taruh zip di tempat yang HANYA kamu akses
(penyimpanan pribadi, rilis privat). Menaruhnya di tempat publik berarti menyebarkan file Valve.
"""
import argparse
import hashlib
import os
import struct
import sys
import zipfile

VPK_SIG = 0x55AA1234
IN_DIR = 0x7FFF


class Vpk:
    """Pembaca VPK versi 1 dan 2 (hanya baca)."""

    def __init__(self, dir_path):
        self.dir_path = dir_path
        self.entries = {}  # path(huruf kecil) -> (archive_idx, offset, length, preload, path_asli)
        with open(dir_path, "rb") as f:
            hdr = f.read(12)
            sig, ver, tree_size = struct.unpack("<III", hdr)
            if sig != VPK_SIG:
                raise ValueError("bukan VPK: " + dir_path)
            if ver == 2:
                f.read(16)  # FileDataSectionSize, ArchiveMD5, OtherMD5, Signature
                self.header_size = 28
            elif ver == 1:
                self.header_size = 12
            else:
                raise ValueError("versi VPK %d tidak didukung: %s" % (ver, dir_path))
            tree = f.read(tree_size)
        self._parse_tree(tree)
        base = dir_path[:-8] if dir_path.lower().endswith("_dir.vpk") else dir_path[:-4]
        self.base = base

    def _parse_tree(self, t):
        pos = 0

        def cstr():
            nonlocal pos
            end = t.index(b"\0", pos)
            s = t[pos:end].decode("utf-8", "replace")
            pos = end + 1
            return s

        while True:
            ext = cstr()
            if ext == "":
                break
            while True:
                d = cstr()
                if d == "":
                    break
                while True:
                    name = cstr()
                    if name == "":
                        break
                    crc, preload, arch, off, length, term = struct.unpack_from("<IHHIIH", t, pos)
                    pos += 18
                    if term != 0xFFFF:
                        raise ValueError("tree VPK rusak")
                    pre = t[pos:pos + preload]
                    pos += preload
                    dd = "" if d == " " else d
                    ee = "" if ext == " " else ext
                    p = (dd + "/" if dd else "") + name + ("." + ee if ee else "")
                    self.entries[p.lower().replace("\\", "/")] = (arch, off, length, pre, p)

    def read(self, path):
        arch, off, length, pre, _orig = self.entries[path]
        if length == 0:
            return pre
        if arch == IN_DIR:
            fn, base_off = self.dir_path, self.header_size + self._tree_size() + off
        else:
            fn, base_off = "%s_%03d.vpk" % (self.base, arch), off
        with open(fn, "rb") as f:
            f.seek(base_off)
            data = f.read(length)
        if len(data) != length:
            raise IOError("data terpotong: %s (%s)" % (path, fn))
        return pre + data

    def _tree_size(self):
        with open(self.dir_path, "rb") as f:
            return struct.unpack("<III", f.read(12))[2]


def find_sources(root):
    """Urutan prioritas: file lepas dulu, lalu VPK (urut nama)."""
    loose, vpks = [], []
    for dp, dn, fn in os.walk(root):
        for n in fn:
            full = os.path.join(dp, n)
            if n.lower().endswith("_dir.vpk"):
                vpks.append(full)
    return sorted(vpks)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("gamedir", help="folder instalasi game LAMA (mis. .../Half-Life 2)")
    ap.add_argument("-o", "--output", default="legacy-pack.zip")
    ap.add_argument("--include", action="append",
                    help="awalan path di dalam game yang disalin (boleh berulang; bawaan: shaders/)")
    ap.add_argument("--list", action="store_true", help="hanya tampilkan ringkasan, jangan buat zip")
    a = ap.parse_args()

    prefixes = [p.lower().replace("\\", "/") for p in (a.include or ["shaders/"])]
    root = os.path.abspath(a.gamedir)
    if not os.path.isdir(root):
        sys.exit("Folder tidak ada: " + root)

    chosen = {}  # path huruf kecil -> ("vpk", Vpk, path_asli) | ("file", fullpath, path_asli)

    # file lepas (mis. hl2/shaders/...) lebih diprioritaskan daripada isi VPK
    for dp, dn, fn in os.walk(root):
        for n in fn:
            full = os.path.join(dp, n)
            rel = os.path.relpath(full, root).replace("\\", "/")
            # buang segmen pertama (nama folder game: hl2, platform, ...)
            parts = rel.split("/", 1)
            sub = parts[1] if len(parts) == 2 else rel
            if any(sub.lower().startswith(p) for p in prefixes):
                chosen.setdefault(sub.lower(), ("file", full, sub))

    for vp in find_sources(root):
        try:
            v = Vpk(vp)
        except (ValueError, OSError) as e:
            print("lewati %s: %s" % (vp, e), file=sys.stderr)
            continue
        hit = 0
        for path in v.entries:
            if any(path.startswith(p) for p in prefixes) and path not in chosen:
                chosen[path] = ("vpk", v, v.entries[path][4])
                hit += 1
        print("%-60s %6d file cocok" % (os.path.relpath(vp, root), hit))

    vcs = [p for p in chosen if p.startswith("shaders/fxc/") and p.endswith(".vcs")]
    print("\nTotal: %d file, di antaranya %d shader (.vcs)." % (len(chosen), len(vcs)))
    if not vcs and "shaders/" in prefixes:
        sys.exit("Tidak ada shaders/fxc/*.vcs ditemukan. Pastikan folder ini instalasi HL2 (bukan folder kosong), "
                 "dan bahwa shader-nya memang ada di VPK/folder game.")
    if a.list:
        return

    with zipfile.ZipFile(a.output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in sorted(chosen):
            kind, src, orig = chosen[path]
            if kind == "file":
                z.write(src, orig)
            else:
                z.writestr(orig, src.read(path))
    h = hashlib.sha256()
    with open(a.output, "rb") as f:
        for blk in iter(lambda: f.read(1 << 20), b""):
            h.update(blk)
    print("\nDitulis: %s (%.1f MB)" % (a.output, os.path.getsize(a.output) / 1048576.0))
    print("SHA-256: " + h.hexdigest())
    print("Isi SHA-256 itu di launcher supaya unduhan diverifikasi.")


if __name__ == "__main__":
    main()
