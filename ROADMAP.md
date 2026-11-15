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
- Android runtime decompression uses the pure-Java `com.qyntrax:unzstd:0.1.0` decoder, while edited-component compression is provided by the packaged Android `zstd-jni` runtime.
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
- Sebelum ekspor, seluruh komponen sumber didekompresi dan divalidasi. Setelah penyalinan, SHA-256 sumber dan file tujuan dibandingkan secara streaming dengan buffer 64 KiB.
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


## Analisis dua save Bolton lintas musim — 15 November 2026
- Menganalisis dua snapshot karier dengan nama save dan database 21.4.0-0 yang sama; keduanya dinyatakan pengguna berjarak satu musim.
- 852/852 komponen berhasil diekstrak dari masing-masing file. 688 komponen identik dan 164 berubah; tidak ada komponen yang hilang atau bertambah.
- Seluruh 17.486 UID name-index sama. 15.235 UID terhubung mempertahankan entity ID walaupun offset bergeser.
- Dua record person terpetakan berubah pada byte relatif 69: Bozhidar Iskrenov dan Luke Duzel (`FF` pada backup menjadi `64` pada file pertama). Arti byte belum diketahui.
- `finance_manager/.dat` identik. Pasangan ini memperkuat pengujian join lintas-save, tetapi bukan fixture satu-field dan tidak cukup untuk memberi label CA/PA/atribut/keuangan.
- Laporan: `docs/CROSS_SEASON_FIXTURE_ANALYSIS.md`. Kedua file asli tidak dimasukkan ke Git.

## Diagnostik person lintas-save dan penghematan memori — 15 November 2026

- `PersonRecordMapper` kini menerima `ByteBuffer` dan file melalui memory mapping read-only. Tab Orang tidak lagi membuat salinan heap tambahan dari `game_db/.dat`.
- `PersonRecordDiffAnalyzer` membandingkan snapshot melalui dua memory map, menyelaraskan record berdasarkan UID dan entity ID, serta tetap melaporkan byte berubah sebagai data mentah.
- Analisis person lintas-save hanya diaktifkan jika versi database kedua save sama dan diketahui.
- UID duplikat/ambigu tidak digabung diam-diam. Ringkasan menampilkan UID ambigu, UID baru/hilang, record berubah/tetap, serta record yang panjangnya belum dapat ditentukan.
- Tab Perbandingan dapat mengekspor seluruh hasil perubahan record person ke CSV.
- Tes regresi ditambahkan untuk buffer read-only, posisi buffer, file memory-mapped, file kosong, offset record bergeser, dan UID duplikat.
- README diperbarui.
- CI head `acc6a8678ada818ca813226b3b13878c92575be5` masih gagal sebelum runner dialokasikan: kedua job memiliki `runner_id: 0`, nama runner kosong, dan daftar step kosong. Build Kotlin/Gradle dan emulator belum terbukti berjalan. Label runner dikembalikan ke `ubuntu-latest`; penggantian label OS tidak mengatasi kegagalan alokasi runner.
- Tidak ada field CA/PA, atribut, keuangan, transfer budget, prize money, atau reputasi yang dibuka untuk edit. Aplikasi tetap read-only sampai decoding semantik dan uji save di dalam game terbukti.


## UI stability and export responsiveness — 15 November 2026
- People lazy-list keys now include row identity as well as UID, avoiding duplicate-key crashes when multiple name-index rows reference the same UID.
- Export file copying, FMV re-open validation, and SHA-256 verification run on the IO dispatcher instead of blocking the Compose main thread.
- These changes are source updates only; they still require a passing Android build, unit tests, and API 26 emulator run.


## Export picker lifecycle — 15 November 2026
- Operasi ekspor mempertahankan status sibuk sampai Android Storage Access Framework mengembalikan hasil atau pembatalan.
- Ini mencegah ekspor kedua menimpa referensi file sementara ketika dialog pemilihan lokasi pertama masih terbuka.
- Kegagalan menyiapkan file tetap mengembalikan UI ke status siap.


## Person mapper ambiguity guard — 15 November 2026
- Pemetaan panjang record kini hanya menggunakan entity ID yang unik lintas UID. Jika beberapa UID mengarah ke entity ID yang sama, panjang record tidak diinferensikan untuk ID tersebut.
- Tes regresi menambahkan tiga baris sintetis dengan dua UID yang berbagi entity ID, memastikan panjang record keduanya tetap null.


## CSV export safety — 15 November 2026
- `csvEscape` sekarang menambahkan awalan teks untuk nilai yang dapat ditafsirkan sebagai formula spreadsheet, termasuk setelah whitespace/BOM, dan tetap meng-escape tanda kutip CSV.
- Unit test mencakup nilai biasa, koma, kutip, formula `=`, `+`, `-`, `@`, tab, dan BOM.

## Pengurangan penggunaan memori saat ekspor — 15 November 2026
- Verifikasi hash ekspor FMV tidak lagi membaca seluruh file tujuan ke ByteArray; kedua stream dihitung dengan buffer tetap 64 KiB.
- Tes unit ditambahkan untuk vektor SHA-256 yang diketahui, pembacaan stream pendek, dan penutupan stream.
- Smoke test Kotlin terisolasi lulus. Build Gradle, unit test repository, dan emulator API 26 tetap menunggu runner CI yang dapat berjalan.


## Pembacaan save berbatas — 15 November 2026
- Fungsi readSaveBytes kini memeriksa sisa kapasitas sebelum menambah blok data ke buffer, dengan aritmetika yang tidak bergantung pada penjumlahan ukuran yang berpotensi overflow.
- Pesan batas ukuran dibuat dinamis mengikuti nilai konfigurasi. Batas default tetap 128 MiB karena parser masih memerlukan seluruh container di memori.
- Menambahkan SaveReadLimitTest untuk batas tepat, input terlalu besar, stream ditutup setelah sukses/gagal, serta konfigurasi batas negatif.
- Belum ada klaim build/test lulus: workflow CI perlu menjalankan job sampai tahap Gradle agar perubahan ini tervalidasi pada proyek Android lengkap.

- Smoke test lokal Kotlin 1.9 untuk helper pembaca berbatas lulus: batas tepat, input berlebih ditolak, dan stream ditutup pada jalur sukses maupun kegagalan termasuk konfigurasi negatif. Pemeriksaan ini tidak mengompilasi seluruh aplikasi atau menjalankan unit test Gradle.

## Pengurangan puncak memori saat membuka save — 15 November 2026
- Pembacaan save dan save pembanding kini dialirkan ke file sementara di cache menggunakan buffer tetap 64 KiB, lalu dimuat ke ByteArray setelah selesai.
- File sementara dihapus dalam blok finally, termasuk ketika input melewati batas atau terjadi kegagalan I/O.
- Ini menghindari ByteArrayOutputStream yang berkembang bertahap dan salinan buffer penuh tambahan dari toByteArray(). Parser FMV masih memerlukan satu ByteArray penuh; batas input 128 MiB tetap dipertahankan.
- SaveReadLimitTest diperluas untuk memastikan file sementara dibersihkan pada sukses, input berlebih, dan konfigurasi batas negatif.
- Smoke test Kotlin 1.9 terisolasi lulus untuk pembacaan, batas, cleanup, dan penutupan stream. Gradle, unit test repository, dan API 26 belum tervalidasi.

- Menambahkan tes untuk kegagalan I/O di tengah proses baca, memastikan file sementara dibersihkan dan input stream ditutup. Batas 128 MiB ditampilkan di layar awal agar batas dukungan tidak tersembunyi.
- Smoke test Kotlin 1.9 diperluas dan lulus untuk batas tepat, input berlebih, konfigurasi negatif, kegagalan I/O, cleanup file sementara, dan penutupan stream.

## Status runner CI terbaru — 15 November 2026
- Actions run 282 pada head `123f06f5e538e754ecfea3af6e73f9c7f7c76438` gagal pada kedua job. API job mengembalikan `steps: []` dan `logs_url: null`; tidak ada bukti Gradle atau emulator mulai berjalan.
- Perubahan pembacaan save berbasis file sementara tetap tercatat sebagai smoke-tested secara terisolasi, bukan lulus build Android.

## Pemulihan ekspor setelah Activity dibuat ulang — 15 November 2026
- Path file sementara yang menunggu dialog Storage Access Framework kini memakai rememberSaveable, sehingga dapat dipulihkan setelah perubahan konfigurasi atau pembuatan ulang Activity.
- Callback memeriksa file masih tersedia sebelum menyalin. Jika file hilang, ekspor gagal secara eksplisit dan UI tidak mengklaim sukses.
- Ini memperbaiki celah lifecycle yang dapat membuat callback menerima URI tanpa referensi payload setelah Activity recreation. Validasi runtime Android masih menunggu emulator/perangkat.

## CI terbaru dan lifecycle ekspor — 15 November 2026
- Jalur ekspor kini menyimpan path payload sementara dengan rememberSaveable dan memvalidasi file sebelum menyalin setelah callback Android.
- Actions run 288 (43675661930) untuk head a6897490a1414194dcec6b0a0ecc136e2ccb9c17 gagal pada kedua job tanpa langkah maupun log. Belum ada bukti Gradle atau emulator dijalankan.

## Perapian Android instrumentation test — 15 November 2026
- MainActivityLaunchTest kini memverifikasi judul aplikasi, aksi pemilih save, mode hanya-baca, minimum Android 8/API 26, dan batas impor 128 MiB.
- Pemeriksaan runtime native Zstandard tetap berada di test yang sama. MainActivitySmokeTest yang menduplikasi pemeriksaan layar awal dihapus agar tidak menjalankan launch test yang sama dua kali.
- Perubahan belum diuji pada emulator karena runner Actions masih gagal sebelum langkah berjalan.

## Keamanan repository untuk persiapan release — 15 November 2026
- Menambahkan aturan `.gitignore` untuk `.gradle`, seluruh direktori build Android, `local.properties`, folder IDE, keystore/signing key, serta file environment lokal.
- Aturan ini mencegah penambahan tidak sengaja terhadap kredensial signing dan artefak build. Tetap periksa status Git sebelum commit; `.gitignore` tidak menghapus file rahasia yang sudah pernah masuk riwayat.

## Provenance dan checksum artefak — 15 November 2026
- Menambahkan langkah CI setelah pemeriksaan paket untuk menghasilkan SHA-256 bagi APK debug, APK release (signed jika tersedia, unsigned jika tidak), dan AAB.
- BUILD-METADATA.txt merekam commit SHA, version name/code, min SDK 26, dan target SDK 36. Keduanya diunggah bersama artefak APK/AAB.
- Workflow belum berhasil mencapai runner pada percobaan terakhir, jadi checksum dan metadata belum pernah dihasilkan oleh CI.

## Penanganan kegagalan pemilih file — 15 November 2026
- Peluncuran Android OpenDocument untuk save utama dan save pembanding kini dibungkus penanganan exception. Kegagalan dialog menghasilkan pesan error dan tidak mengubah status busy.
- Ini melengkapi penanganan kegagalan peluncuran CreateDocument pada jalur ekspor. Runtime SAF masih harus diuji pada API 26 dan perangkat Android terbaru.

## Hasil CI terakhir — 15 November 2026
- Run 311 (`43676030826`) pada head `ce2ff55bdc1ef564e109106418b66d57000284e5` gagal untuk job build dan API 26 smoke.
- Kedua job tidak mengembalikan step summary maupun URL log (`steps: []`, `logs_url: null`). Tidak ada bukti source build, unit test, atau emulator mulai berjalan.

- Smoke test shell untuk langkah checksum/provenance berhasil pada tiga file dummy. Pemeriksaan `test -n` ditambahkan agar metadata versi tidak diam-diam kosong jika format `app/build.gradle.kts` berubah.

- Menambahkan dependency Compose `runtime-saveable` secara eksplisit untuk fitur pemulihan path payload ekspor. Kompilasi tetap harus dibuktikan oleh Gradle CI.

## CI terbaru — 15 November 2026
- Actions run 322 (`43676170041`) pada head `aa43f3fc5c1e663e4a1b6949324ea0d9c93ac2f5` gagal pada kedua job.
- Job API kembali memberikan `steps: []` dan `logs_url: null`. Tidak ada bukti bahwa Gradle, Python diagnostics, atau Android emulator mulai berjalan.

## Validasi header/footer container — 15 November 2026
- `FmvContainer` kini memvalidasi field offset footer pada header (offset byte 9, little-endian) terhadap posisi footer manifest yang ditemukan.
- Menambahkan tes regresi yang merusak pointer header dan memastikan parser menolak file tersebut. Fixture sintetis valid diperbarui untuk menulis pointer yang benar.
- Dua fixture FMV nyata yang dianalisis sebelumnya memiliki pointer `footerOffset - 9` yang cocok. Uji Kotlin penuh masih menunggu runner CI.

- Parser sekarang memakai pointer header untuk menentukan lokasi footer, lalu memverifikasi marker FMF tepat pada posisi tersebut. Ini lebih deterministik daripada mencari kemunculan marker terakhir di seluruh file, yang berpotensi salah jika byte marker muncul di payload.
- Tes tambahan memastikan pointer yang berada di dalam file tetapi menunjuk ke data non-footer juga ditolak.

- Harness Kotlin 1.9 lokal menjalankan empat metode SaveReadLimitTest dengan assertion yang aktif: semua lulus. Karena memakai shim JUnit minimal dan bukan Gradle/JUnit proyek, hasilnya hanya validasi helper/test logic terisolasi.

## Validasi parser dan CI — 15 November 2026
- Pointer footer header dipakai langsung untuk mencari manifest; marker diverifikasi tepat di offset yang ditunjuk. Dua kasus pointer rusak ditambahkan ke tes parser.
- Run 336 (`43676489758`) gagal pada build dan API 26 smoke tanpa langkah/log. Belum ada hasil Gradle atau emulator.

## Runner CI terbaru — 15 November 2026
- Run 340 (`43676528691`) untuk head `3ea07f39ed0aaabb1026e886fb67d69af19206fe` gagal pada kedua job tanpa step summary/log. Belum ada bukti Gradle atau emulator mengeksekusi perubahan pointer-footer.

## Gerbang privasi paket Android — 15 November 2026
- Workflow memeriksa `android:allowBackup="false"` pada manifest sumber dan memeriksa permission hasil merge pada APK debug/release untuk memastikan tidak ada `android.permission.INTERNET`.
- Ini menambahkan pemeriksaan otomatis atas klaim offline-first dan mencegah permission jaringan muncul diam-diam melalui manifest dependency. Validasi menunggu runner CI.

## Akurasi kebijakan retensi file — 15 November 2026
- Privacy policy kini menjelaskan kapan file staging impor dan payload ekspor dihapus, serta kondisi ketika proses terhenti sebelum cleanup sehingga cache mungkin masih berisi file sementara.

## CI terbaru — 15 November 2026
- Run 350 (`43676715152`) gagal pada job build dan API 26 smoke tanpa step summary atau log. Pemeriksaan offline-first manifest, checksum artefak, dan parser footer belum dijalankan oleh runner.

## Cleanup ekspor saat coroutine dibatalkan — 15 November 2026
- File payload ekspor dihapus pada blok `finally` yang memakai `NonCancellable + Dispatchers.IO`, lalu completion handler job menjadi lapisan cleanup tambahan jika job dibatalkan sebelum body mulai.
- Ini menutup celah file sementara yang dapat tertinggal ketika Activity recreation membatalkan coroutine ekspor. Uji runtime Android masih diperlukan.

## Persiapan informasi publik aplikasi — 15 November 2026
- Privacy policy tidak lagi mengarahkan pengguna ke repository privat sebagai kontak. Draft kini meminta alamat dukungan publik sebelum rilis.
- Checklist publikasi menambahkan hosting privacy policy yang dapat dibuka tanpa login dan kontak dukungan publik yang valid.

## Audit lintas snapshot person record — 15 November 2026
- Pemindaian ulang mengonfirmasi 17.486 entri name-index identik dan 15.235 UID terhubung dengan entity ID/raw tag identik pada kedua snapshot.
- Offset seluruh record yang terhubung bergeser konstan +1.116 byte. Distribusi inferensi panjang identik: 13.427 × 86 byte, 1.517 × 135 byte, 80 ukuran lain, 211 tanpa batas yang dapat dipastikan.
- Hasil tetap struktural, bukan klasifikasi pemain/staf atau pemetaan CA/PA/atribut. Lihat `docs/CROSS_SEASON_FIXTURE_ANALYSIS.md`.

## Status runner setelah audit person lintas snapshot — 15 November 2026
- Run 366 (`43677123810`) gagal pada kedua job tanpa step summary/log. Audit struktural UID/tag/offset sudah dicatat, tetapi build dan tes Android tetap belum dijalankan oleh CI.

## Pembersihan cache sementara saat startup — 15 November 2026
- Menambahkan cleanup pada startup untuk file sementara aplikasi yang lebih tua dari 24 jam. Cleanup dibatasi pada prefix file impor/ekspor yang diketahui dan direktori kerja internal aplikasi; file lain di cache tidak disentuh.
- Menambahkan dua tes untuk penghapusan file stale, retensi file fresh/tidak terkait, dan validasi umur negatif. Harness Kotlin 1.9 lokal dengan assertion shim lulus untuk kedua kasus; Gradle belum berjalan.

## Pembatasan versi writer FMV — 15 November 2026
- `FmvRebuilder.rebuild` menolak perubahan komponen jika versi database bukan `21.4.0-0` atau tidak terdeteksi. Rebuild tanpa perubahan tetap menggunakan jalur salinan identik yang divalidasi.
- Fixture tes writer kini memuat `game_info/.dat`; tes baru memastikan versi `22.0.0-0` ditolak. Writer masih eksperimental dan belum diuji pada file nyata/in-game.

- Tes versi writer diperluas untuk mencakup database version yang tidak diketahui selain versi `22.0.0-0`. Edit ditolak pada kedua kondisi; no-op copy tetap tidak menulis ulang data.
