# FM21 Touch SaveData Editor — Peta Jalan Pengembangan

Target: Android / Football Manager Touch 2021 (`.fmv`), versi basis data 21.4.0-0.

## Aturan penetapan status
Tahap hanya boleh dinyatakan selesai setelah diuji pada file save FM21 Touch asli, termasuk uji simpan-buka tanpa perubahan dan uji pemuatan save yang sudah diedit di dalam game. Tampilan UI atau fitur contoh bukan bukti penyelesaian.

| Stage | Status | Acceptance criteria |
|---|---|---|
| FMU Container Parser | Implemented; real-save and CI tests pass; Android 8 device coverage pending | Validate header/footer, decode manifest and enumerate all 852 members |
| Component Extractor | Implemented; synthetic multi-frame and real-save extraction checks pass; Android 8 device coverage pending | Stream Zstandard members to a new file and verify frame prefixes, plaintext lengths and boundaries |
| Player Record Mapping | Name/UID-to-person entity mapping implemented; ambiguity QA added; player classification pending | Map name index entries to entity IDs; classify players vs staff and verify against in-game data |
| CA / PA Editor | Not implemented | Edit correct player records and persist values through a verified rebuild |
| Player Attributes Editor | Not implemented | Map all target attributes, scales and ownership; preserve unrelated bytes |
| Club Finance Editor | Not implemented | Verify club balance and transfer-budget field semantics |
| Prize Money Editor | Not implemented | Verify competition, position and prize amount mapping |
| League Reputation Editor | Not implemented | Verify league IDs and reputation scale |
| FMV Rebuilder | Initial experimental implementation; synthetic tests added; real-game validation pending | Rebuild valid container, preserve unknown sections and pass round-trip and game-load tests |

## Kemajuan persiapan rilis Android
- App version code/name advanced to 8 / 0.8.0-alpha.
- CI builds debug and release APK/AAB variants; release signing is enabled only when private keystore secrets are configured.
- Added an Android 8.0 / API 26 emulator launch smoke test using an instrumentation test. Latest verified API 26 emulator smoke test passed on commit `b27ff756` (workflow run `42806431538`).
- The current CI release APK/AAB are unsigned because no private release keystore is configured; publication signing must use a private keystore outside the repository.
- These checks improve Android compatibility confidence but do not replace real-device testing on Android 8 and newer versions.

## Kemajuan antarmuka dan pengalaman pengguna
- Replaced the long single-page component dump with Overview, People, Compare and Files tabs.
- Added a save summary dashboard, status/progress feedback, and dedicated tool cards.
- Component browser now has search, horizontally scrollable category filters and incremental loading (40 at a time).
- People and comparison workflows have separate screens; the app remains read-only.
- Compact-screen layout uses scrollable filters and cards with clearer hierarchy.
- Visual language corrected to match a football save-data editor: calm pitch-green accents, dark data panels, and dashboard-first information hierarchy.

## Status aplikasi dan antarmuka
- App version 0.8.0-alpha, minimum Android API 26, target SDK 36 (Android 16).
- Overview, People, Compare and Files navigation tabs are implemented.
- Component browser includes category filters, search and incremental loading.
- Dark Material 3 theme is implemented; the visual direction is football-management/data-studio (graphite panels with restrained pitch-green accents), not motorsport.
- Current release remains read-only. A successful CI build is not the same as device QA on Android 8 or an in-game save round-trip.

## Temuan pemetaan pemain dan staf
- `game_db/.dat` name index yielded 17,486 entries in the supplied save.
- 15,235 unique IDs resolved to a person entity row; 2,251 remain unresolved.
- The Android mapper is read-only and leaves ambiguous/unresolved links blank.
- Record tags and adjacent-ID length observations are exploratory metadata only; they are not player classification or proof of a writable record schema.
- This is a person mapping, not yet a verified player-only index. CA/PA and attribute offsets are not mapped.
- See `docs/PLAYER_RECORD_MAPPING.md` for observed record layouts and examples.

## Fakta struktur file yang sudah diverifikasi
- Outer header starts with bytes `02 01 fmf.`; first data region starts at byte 30.
- Database version string: `21.4.0-0`.
- Footer contains a nested FMF/Zstandard manifest.
- Manifest contains 68 top-level members and 784 nested members.
- Member offsets are relative to byte 30.
- Members may contain multiple concatenated Zstandard frames.
- Android runtime decompression uses the pure-Java `com.qyntrax:unzstd:0.1.0` decoder; `zstd-jni` is test-only.
- Four-byte prefixes before subsequent frames match the compressed size of the following frame in both supplied saves.
- The final member's stored boundary includes the first four bytes of the footer marker.
- All 852 members were decompressed and length-checked in both supplied saves using the read-only Python validator.

## Persyaratan keamanan
- Never overwrite the source save; export to a new file.
- Keep byte-for-byte copies of unmodified components.
- Reject unknown container versions and invalid offsets or lengths.
- Do not write guessed offsets, field scales or compression settings.
- Re-open and verify every exported file before calling it successful.

## File uji privat yang dibutuhkan
1. A pre-match save.
2. A post-match save from the same career.
3. A save with a known player edit, if the game exposes a reliable test path.
4. A save with a known club-finance change.

Keep private save files out of public commits. Record file hashes and in-game observations for each fixture.

## Syarat kelulusan akhir
The editor is not complete until every requested field has a verified record owner, value encoding, range, and stable join key; edited saves rebuild successfully; untouched data remains preserved; and each test save opens in FM21 Touch. The current project has no validated write/rebuild path, so the CA/PA, attributes, finance, prize money, league reputation and FMV Rebuilder stages remain blocked rather than guessed.


## Lokalisasi Bahasa Indonesia
- README utama pada branch pengembangan sudah diterjemahkan ke Bahasa Indonesia.
- Label navigasi dan teks utama pada layar Overview, People, Compare, dan Files sedang dialihkan ke Bahasa Indonesia.
- Nama teknis format, nama komponen asli, UID, Entity ID, CSV, CA, dan PA tetap memakai istilah teknis agar tidak mengubah maknanya.
- Sebelum rilis, seluruh pesan kesalahan, aksesibilitas, dialog sistem, dan teks pada layar kecil harus diperiksa kembali.

## Gerbang publikasi
Aplikasi belum siap dipublikasikan sebagai editor save penuh. Daftar penghalang rilis:
1. Pemetaan pemain dan staf yang terverifikasi, termasuk entitas di luar klub pengguna.
2. Pemetaan CA, PA, seluruh atribut, tanggal lahir, kewarganegaraan, dan field orang lainnya.
3. Pemetaan klub, kompetisi, finansial, hadiah, reputasi, serta relasi antarentitas.
4. Rebuild FMV yang diverifikasi pada file save asli dan berhasil dimuat di game.
5. Uji tanpa perubahan, uji edit terkontrol, dan pemeriksaan bahwa data yang tidak disentuh tetap utuh.
6. Uji perangkat fisik Android 8+ dan berbagai ukuran layar.
7. Penandatanganan rilis dengan kunci privat milik pemilik aplikasi, kebijakan privasi, halaman dukungan, ikon, tangkapan layar, dan deskripsi toko aplikasi.

Jangan mempublikasikan versi yang mengaku sebagai editor penuh sebelum seluruh gerbang tersebut terpenuhi. Versi uji terbatas boleh dibagikan dengan label yang jelas bahwa fiturnya masih hanya-baca.


## Kemajuan terbaru: jalur salinan save aman
- Menambahkan pemeriksaan dekompresi penuh seluruh komponen sebelum mengekspor salinan save yang tidak diubah.
- Menambahkan ekspor salinan FMV melalui Android Storage Access Framework; file sumber tidak ditimpa.
- Setelah ekspor, aplikasi membuka ulang file tujuan, memvalidasi manifest dan komponen, lalu membandingkan SHA-256 dengan salinan sementara.
- Menambahkan tes bahwa salinan tanpa perubahan identik byte per byte dengan file sumber.
- Ini bukan editor field database dan tidak boleh dipasarkan sebagai fitur edit pemain/klub. Pemetaan field semantik dan validasi save hasil edit di game tetap menjadi gerbang terpisah.
- Workflow CI diubah agar setiap commit memiliki grup concurrency sendiri sehingga pengujian tidak saling membatalkan ketika beberapa commit dikirim berurutan.


## Referensi fitur editor penuh
- Blueprint parity FMRTE dan Pre-Game Editor: `docs/FMRTE_PRE_GAME_EDITOR_PARITY.md`.
- Cakupan target meliputi pemain, staf, kontrak, klub, keuangan, kompetisi, negara, stadion, pencarian global, Mass Edit, Presets, Compare, Edit History/Undo, backup dan validasi.
- Save Editor dan Pre-Game Database Editor dipisahkan sebagai dua mode produk karena format sumber dan jalur validasinya berbeda.
- Blueprint tersebut adalah spesifikasi target, bukan klaim bahwa field sudah berhasil dipetakan atau dapat ditulis. Hanya field berstatus VERIFIED yang boleh dibuka untuk diedit.


## Validasi fixture FMV nyata — 15 November 2026
- Fixture privat `Coach Morepp - Bolton.fmv` (FM21 Touch 21.4.0-0) berhasil diekstrak ulang: 852/852 komponen, 1.610 frame Zstandard, total plaintext 107.929.383 byte, tanpa kegagalan batas atau panjang.
- `game_db/.dat` berukuran 69.387.047 byte. Diagnostik indeks nama menghasilkan 17.486 entri, 15.235 UID terhubung unik, nol UID ambigu, dan 2.251 entri belum terhubung.
- Uji rebuild struktural Python independen menghasilkan file 21.629.879 byte; seluruh 852 komponen dapat dibuka kembali dan plaintext setiap komponen identik dengan sumber.
- Uji tersebut hanya membuktikan ekstraksi dan round-trip struktur container tanpa perubahan nilai game. Ini bukan bukti bahwa Kotlin rebuilder sudah lulus pada fixture nyata, bukan bukti save dapat dimuat di FM21 Touch, dan bukan izin untuk mengubah field database.
- File fixture asli tetap privat dan tidak dimasukkan ke Git. Detail dan hash tercatat di `docs/REVERSE_ENGINEERING_STATUS.md` serta `docs/VALIDATION.md`.

### Gerbang teknis yang masih wajib
1. Jalankan Kotlin `FmvRebuilder` terhadap fixture FMV nyata, verifikasi semua komponen dan stream yang tidak disentuh, lalu bandingkan hasil dengan validator independen.
2. Uji salinan no-op hasil Kotlin di FM21 Touch 2021. Catat perangkat, versi game, hasil load, dan hash file.
3. Siapkan pasangan save terkontrol yang mengubah tepat satu nilai yang diketahui. Tanpa fixture ini, klasifikasi pemain/staf dan offset CA/PA/atribut/finansial tidak boleh ditebak.
4. Setelah field terbukti, implementasikan editor dengan preview perubahan, batas nilai, undo, backup, rebuild, dan validasi ulang di game.

## Uji regresi writer multi-frame — 15 November 2026
- Memperbaiki tes sintetis Kotlin yang sebelumnya memakai payload pengganti hanya 3 KiB, sehingga sebenarnya tidak melewati batas pemecahan frame writer 8 MiB.
- Fixture kini berukuran 8 MiB + 4 KiB dan tes memeriksa bahwa hasil rebuild berisi tepat dua frame Zstandard.
- Commit: `045fe30cb89a2d9dce53ff8fd8a90936d71f8a64`.
- CI untuk head tersebut masih gagal pada kedua job; log tidak dapat diambil melalui konektor, sehingga penyebab belum diketahui. Jangan tandai validasi build atau API 26 selesai.
