# launcher-extras

Tambahan untuk launcher `nillerusr/srceng-android` (branch `android-fixes`), diterapkan otomatis
oleh workflow lewat `python3 launcher-extras/apply_patch.py srceng-launcher`.

## Isi
| File | Fungsi |
|---|---|
| `apply_patch.py` | font bawaan, salin kelas baru, sisipkan 2 baris di `LauncherActivity`, daftarkan `ControlsActivity` di manifest. Idempoten; berhenti dengan error kalau titik sisip berubah di upstream. |
| `src/me/nillerusr/LauncherExtras.java` | bagian "Shader" dan "Kontrol sentuh" di layar launcher + `applyToIntent()` yang mengisi extra `vpk` dan `argv`. |
| `src/me/nillerusr/TouchProfile.java` | model layout tombol, simpan/baca, dan penulis `touch.cfg` (format sama dengan `CTouchControls::WriteConfig`). |
| `src/me/nillerusr/ControlsEditorView.java` | kanvas editor (geser, ubah ukuran, magnet grid, kunci rasio). |
| `src/me/nillerusr/ControlsActivity.java` | layar editor: tambah/duplikat/hapus, properti, pengaturan sensitivitas, reset, simpan. |

## Cara kerja
- **Shader lama**: path yang kamu isi dikirim sebagai extra `vpk` -> `EXTRAS_VPK_PATH` -> engine menambahkannya
  sebagai search path GAME *sebelum* path dari `gameinfo.txt`, jadi `shaders/fxc/*.vcs` dari situ dipakai lebih dulu.
  Tidak ada file Valve di repo/APK; path menunjuk salinan milikmu sendiri.
- **Kontrol sentuh**: layout disimpan di `files/touch_profile.txt`, lalu setiap Launch ditulis ke
  `files/overlay/cfg/touch.cfg` dan engine diberi `-insert_search_path <files/overlay>`.
  Engine menjalankan `exec touch.cfg` saat start, sehingga tombol bawaan diganti layout-mu.
  Tombol "edit" di dalam game tidak ada di layout ini.
