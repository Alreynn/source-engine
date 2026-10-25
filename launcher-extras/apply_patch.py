#!/usr/bin/env python3
"""
Terapkan tambahan ke launcher nillerusr/srceng-android (branch android-fixes):
  1. font bawaan (ExtractAssets.extractFonts + panggilan di ValveActivity2)
  2. editor kontrol sentuh (TouchProfile, ControlsEditorView, ControlsActivity)
  3. overlay shader lama + argv/vpk (LauncherExtras) dan dua baris di LauncherActivity
  4. registrasi ControlsActivity di AndroidManifest.xml

Pemakaian:  python3 launcher-extras/apply_patch.py <folder-launcher>
Aman dijalankan ulang (setiap langkah dilewati kalau sudah diterapkan).
Gagal keras (exit 1) kalau titik penempelan tidak ketemu persis satu kali,
supaya perubahan di upstream tidak diam-diam merusak hasilnya.
"""
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LAUNCHER = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.getcwd()


def rd(rel):
    with open(os.path.join(LAUNCHER, rel), encoding="utf-8") as f:
        return f.read()


def wr(rel, s):
    with open(os.path.join(LAUNCHER, rel), "w", encoding="utf-8", newline="") as f:
        f.write(s)


def replace_once(s, old, new, what):
    n = s.count(old)
    if n != 1:
        sys.exit("GAGAL: '%s' ditemukan %d kali (harus 1): %s" % (what, n, old.strip()[:70]))
    return s.replace(old, new)


# ---------------------------------------------------------------- 1. font
FONT_METHOD = '''
    // Engine mencari font di <dataDir>/files/ (vgui_surfacelib/linuxfont.cpp)
    public static void extractFonts(Context context)
    {
        try {
            String[] list = context.getAssets().list("fonts");
            if( list == null )
                return;

            for( String name : list ) {
                File out = new File( context.getFilesDir(), name );
                if( out.exists() && out.length() > 0 )
                    continue;

                InputStream is = context.getAssets().open("fonts/" + name);
                FileOutputStream os = new FileOutputStream( out );
                byte[] buffer = new byte[8192];
                int length;
                while( (length = is.read(buffer)) > 0 )
                    os.write(buffer, 0, length);
                os.close();
                is.close();
                chmod( out.getPath(), 0644 );
            }
        }
        catch (Exception e) {
            Log.e("SRCAPK", "Failed to extract fonts:" + e.toString());
        }
    }
'''


def patch_fonts():
    p = "src/me/nillerusr/ExtractAssets.java"
    s = rd(p)
    if "extractFonts" not in s:
        i = s.rstrip().rfind("}")
        if i < 0:
            sys.exit("GAGAL: ExtractAssets.java tidak berakhir dengan }")
        wr(p, s[:i] + FONT_METHOD + "}\n")
        print("ok  ExtractAssets.extractFonts")
    else:
        print("lewati  ExtractAssets.extractFonts (sudah ada)")

    p = "src/com/valvesoftware/ValveActivity2.java"
    s = rd(p)
    if "ExtractAssets.extractFonts(context);" not in s:
        old = "ExtractAssets.extractVPK(context, false);"
        s = replace_once(s, old, old + "\n        ExtractAssets.extractFonts(context);", "extractVPK call")
        wr(p, s)
        print("ok  ValveActivity2 memanggil extractFonts")
    else:
        print("lewati  ValveActivity2 (sudah memanggil extractFonts)")


# ---------------------------------------------------------------- 2+3. kelas baru
def copy_sources():
    srcdir = os.path.join(HERE, "src", "me", "nillerusr")
    dst = os.path.join(LAUNCHER, "src", "me", "nillerusr")
    os.makedirs(dst, exist_ok=True)
    for name in ("TouchProfile.java", "ControlsEditorView.java",
                 "ControlsActivity.java", "LauncherExtras.java"):
        shutil.copyfile(os.path.join(srcdir, name), os.path.join(dst, name))
        print("ok  salin", name)


def patch_launcher_activity():
    p = "src/me/nillerusr/LauncherActivity.java"
    s = rd(p)

    if "LauncherExtras.addViews" not in s:
        old = "changeButtonsStyle((ViewGroup)this.getWindow().getDecorView());"
        new = "LauncherExtras.addViews(this, body, mPref);\n\n\t\t" + old
        s = replace_once(s, old, new, "changeButtonsStyle(decorView)")
        print("ok  LauncherActivity.onCreate -> LauncherExtras.addViews")
    else:
        print("lewati  addViews (sudah ada)")

    if "LauncherExtras.applyToIntent" not in s:
        old = "Intent intent = new Intent(LauncherActivity.this, SDLActivity.class);"
        new = old + "\n\t\tLauncherExtras.applyToIntent(this, intent, cmdArgs.getText().toString());"
        # blok lama yang dikomentari memakai "new Intent(this, SDLActivity.class)" -> tidak ikut cocok
        s = replace_once(s, old, new, "Intent ke SDLActivity di startSource")
        print("ok  LauncherActivity.startSource -> LauncherExtras.applyToIntent")
    else:
        print("lewati  applyToIntent (sudah ada)")
    wr(p, s)


# ---------------------------------------------------------------- 4. manifest
ACTIVITY_XML = (
    '        <activity android:label="Kontrol sentuh" android:name="me.nillerusr.ControlsActivity"'
    ' android:screenOrientation="sensorLandscape"'
    ' android:configChanges="orientation|screenSize|keyboardHidden"'
    ' android:theme="@android:style/Theme.NoTitleBar.Fullscreen"/>\n'
)


def patch_manifest():
    p = "AndroidManifest.xml"
    s = rd(p)
    if "me.nillerusr.ControlsActivity" in s:
        print("lewati  manifest (sudah ada)")
        return
    anchor = '        <service android:name="me.nillerusr.UpdateService"'
    s = replace_once(s, anchor, ACTIVITY_XML + anchor, "tag <service> UpdateService")
    wr(p, s)
    print("ok  manifest: ControlsActivity terdaftar")


if __name__ == "__main__":
    if not os.path.isfile(os.path.join(LAUNCHER, "AndroidManifest.xml")):
        sys.exit("Bukan folder launcher (AndroidManifest.xml tidak ada): " + LAUNCHER)
    patch_fonts()
    copy_sources()
    patch_launcher_activity()
    patch_manifest()
    print("Selesai.")
