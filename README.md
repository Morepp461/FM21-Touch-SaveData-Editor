# FM21 Touch SaveData Editor

Aplikasi Android untuk memeriksa dan mengembangkan editor database pada file save Football Manager Touch 2021 (`*.fmv`).

> **Status saat ini: masih hanya-baca (read-only).** Aplikasi belum mengizinkan pengubahan data save. Fitur edit baru akan diaktifkan setelah struktur data, aturan validasi, proses penyimpanan ulang, dan hasil pemuatan di dalam game terbukti aman.

## Versi dan dukungan Android

- Versi aplikasi: **0.8.0-alpha**
- Android minimum: **Android 8.0 (API 26)**
- Target SDK: **36**
- Teknologi: Kotlin + Jetpack Compose
- Kompresi Zstandard: decoder Java murni dan runtime kompresi Android zstd-jni
- Referensi metadata database aktif: **FM21 Touch 21.4.0** (`2140_fmc.xml`) saja

## Fitur yang sudah tersedia

- Memilih file save melalui Android Storage Access Framework dan mengekspor komponen.
- Memeriksa validitas header dan footer-manifest FMF/FMV pada format save FM21 Touch 21.4.0-0 yang sudah diamati.
- Menampilkan daftar komponen utama dan komponen di dalam arsip.
- Mengekstrak komponen Zstandard multi-frame dengan pemeriksaan panjang dan batas data.
- Menjelajahi komponen dengan pencarian, filter kategori, dan pemuatan bertahap.
- Menjelajahi indeks orang dengan pencarian nama/UID/entity ID serta filter entri terhubung dan belum terpetakan.
- Membandingkan dua save melalui hash SHA-256, jumlah byte yang berubah, dan posisi perbedaan pertama.
- Memetakan UID pada indeks nama ke entity record person secara hanya-baca.
- Memuat `game_db/.dat` melalui memory mapping agar indeks person dan perbandingan dua snapshot tidak perlu menyalin seluruh database ke heap aplikasi.
- Membatasi pemetaan person dan diff record ke versi save yang sudah diamati, yaitu `21.4.0-0`, serta menolak perbandingan jika versi kedua save tidak cocok.
- Membandingkan record person lintas-save berdasarkan UID/entity ID, termasuk ketika offset byte bergeser, serta menampilkan byte yang berubah sebagai bukti mentah tanpa menebak arti field.
- Mengekspor seluruh hasil perubahan record person ke CSV dari tab Perbandingan.
- Memvalidasi seluruh komponen save dengan dekompresi penuh sebelum membuat salinan FMV tanpa perubahan.
- Mengekspor salinan FMV yang seluruh komponennya telah divalidasi ke lokasi pilihan pengguna melalui Android Storage Access Framework, lalu membandingkan SHA-256 sumber dan hasil ekspor secara streaming tanpa memuat ulang seluruh save ke heap.
- Parser metadata FMC internal yang memvalidasi target database 21.4.0 dan menolak pemrosesan DTD/entity eksternal. Parser ini tidak memetakan struktur biner `.dat`.
- Antarmuka gelap bergaya **Football Management Data Studio**: nuansa lapangan hijau yang kalem, panel data yang rapi, hierarki informasi jelas, dan tab Ringkasan, Orang, Perbandingan, serta Berkas.
- Ikon aplikasi adaptif dan kebijakan privasi yang mengutamakan penggunaan offline.
- CI Android membangun APK debug/release dan AAB, menjalankan unit test, memeriksa metadata kompatibilitas paket, serta menjalankan UI smoke test pada emulator Android API 26.
- Penandatanganan release opsional melalui GitHub Actions secrets pribadi. Tanpa keystore pribadi, hasil CI tetap berupa file unsigned (belum ditandatangani).

## Fitur yang belum tersedia

Aplikasi masih **read-only**. Hal-hal berikut belum siap diedit:

- Klasifikasi person sebagai pemain, staf, pemain bebas, atau tipe lainnya.
- Current Ability (CA) dan Potential Ability (PA).
- Atribut pemain yang terlihat maupun tersembunyi.
- Tanggal lahir, kewarganegaraan, tempat lahir, dan identitas personal lainnya.
- Atribut dan kualifikasi staf.
- Data semua klub, bukan hanya klub yang sedang dimainkan.
- Keuangan klub, saldo, utang, anggaran, dan transfer budget.
- Hadiah uang kompetisi (prize money).
- Reputasi liga, klub, negara, dan kompetisi.
- Pengeditan field database yang sudah dipetakan secara semantik.
- Pembangunan ulang container FMV untuk perubahan data dan proses penyimpanan save hasil edit yang tervalidasi. Salinan tanpa perubahan sudah memiliki jalur validasi tersendiri.

**Target produk adalah seluruh database di dalam save.** Pemain dan staf dari klub mana pun, termasuk klub yang tidak sedang dimainkan, harus bisa dicari dan dikelola setelah struktur datanya berhasil dipetakan. Konteks klub hanya boleh menjadi filter pencarian, bukan pembatas akses.

Indeks person tidak sama dengan daftar pemain. Indeks tersebut dapat memuat staf dan jenis person lainnya. Entity ID, tag record mentah, serta perkiraan panjang record saat ini hanya digunakan untuk diagnostik, bukan sebagai dasar untuk mengubah byte.

## Arah desain UI/UX

Aplikasi ini adalah alat pengelola dan editor database save game sepak bola, bukan aplikasi motorsport. Karena itu, identitas visualnya mengikuti konteks **football management**: tampilan dashboard manajerial, nuansa hijau lapangan yang tidak mencolok, latar gelap untuk kenyamanan membaca data, serta tabel dan panel informasi yang mengutamakan keterbacaan. UI harus terasa seperti perangkat analisis dan administrasi klub sepak bola, bukan tema balap atau siaran motorsport.

Prioritas UX:
- Pencarian global untuk pemain, staf, klub, liga, dan kompetisi di seluruh database save.
- Profil pemain/staf dengan pengelompokan atribut yang mudah dipindai.
- Filter klub, negara, posisi, dan jenis entitas tanpa membatasi cakupan database.
- Form edit dengan validasi nilai, indikator perubahan, dan konfirmasi sebelum menulis save.
- Navigasi satu tangan, kontrol yang nyaman di layar ponsel, serta status proses yang jelas.

## Navigasi aplikasi

- **Ringkasan**: identitas save, versi database, jumlah komponen, dan alat yang tersedia.
- **Orang**: membangun dan mencari indeks person. Hasil tetap ditandai belum terklasifikasi sampai struktur pemain dan staf terbukti.
- **Perbandingan**: membandingkan save kedua, idealnya dari karier yang sama.
- **Berkas**: memfilter komponen berdasarkan kategori, mencari jalur arsip, dan mengekspor komponen satu per satu.

## Alat riset read-only

Direktori `tools/` berisi pemeriksa file FMV dan alat diagnostik untuk membandingkan record person antar-save.

Simpan file fixture `.fmv` pribadi di luar repository, lalu jalankan:

```bash
python tools/analyze_person_records.py sebelum.fmv sesudah.fmv --json laporan-person.json
cd tools && python -m unittest -v
```

Pasang dependensi Python terlebih dahulu:

```bash
python -m pip install -r tools/requirements.txt
```

Alat ini membantu memeriksa hubungan UID person dengan entity ID yang stabil serta perbedaan byte antar-save. Alat ini **belum** dapat memastikan apakah sebuah record adalah pemain atau staf, dan belum mengidentifikasi field yang aman untuk diedit.

Kebutuhan alat:
- Python 3.10 atau lebih baru.
- NumPy sesuai versi pada `tools/requirements.txt`.
- Library Zstandard sistem (`libzstd`).
- File helper `tools/fm21_touch_container.py`.

## Build aplikasi secara lokal

Kebutuhan:
- JDK 17.
- Android SDK 36.
- Android Gradle Plugin 8.9.1.
- Gradle 8.11.1.

Jalankan:

```bash
gradle :app:assembleDebug :app:testDebugUnitTest
```

Hasil APK debug berada di:

`app/build/outputs/apk/debug/app-debug.apk`

### Penandatanganan release

Untuk publikasi melalui Play Store, atur GitHub Actions secrets repository berikut menggunakan upload/release key yang dibuat dan disimpan secara pribadi:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Jangan pernah memasukkan keystore atau password ke repository. Tanpa secrets tersebut, CI hanya menghasilkan file unsigned.

## Keamanan dan validasi

- File save asli tidak pernah ditimpa.
- Aplikasi tidak boleh menulis menggunakan offset atau nilai yang masih berupa dugaan.
- Gerbang penulisan harus menolak container yang belum lolos validasi penulisan.
- Simpan fixture save pribadi di luar commit publik.
- File hasil ekspor harus dibuka dan diperiksa ulang sebelum dinyatakan berhasil.
- Suatu tahap baru boleh dinyatakan selesai setelah tes sintetis, validasi save asli, round-trip tanpa perubahan, round-trip dengan perubahan terkontrol, dan uji pemuatan di dalam game berhasil.

## Dokumen proyek

- `ROADMAP.md`: tahapan pengembangan dan status pekerjaan.
- `docs/DATABASE_METADATA.md`: referensi metadata database aktif 21.4.0.
- `docs/GLOBAL_EDITOR_SCOPE.md`: cakupan editor untuk seluruh database dalam save.
- `docs/CROSS_SAVE_PERSON_RECORD_ANALYSIS.md`: hasil analisis person antar-save.
- `docs/CROSS_SEASON_FIXTURE_ANALYSIS.md`: perbandingan dua snapshot karier Bolton yang berjarak satu musim.
- `docs/MATT_GILKS_CA_RESEARCH.md`: hasil pencarian identitas Matt Gilks dan status pemetaan CA/PA.
- `docs/PLAYER_RECORD_MAPPING.md`: riset pemetaan record pemain/person.
- `docs/VALIDATION.md`: aturan validasi.
- `docs/CONTROLLED_FIXTURE_PROTOCOL.md`: format eksperimen terkontrol untuk membuktikan field database sebelum diaktifkan untuk edit.
- `docs/REVERSE_ENGINEERING_STATUS.md`: status riset format save.

Proyek ini merupakan proyek komunitas independen dan tidak berafiliasi dengan Sports Interactive maupun SEGA.


### UI responsiveness and list stability
- People list rows use unique row-scoped keys so duplicate UID aliases do not crash lazy rendering.
- Export copying and FMV integrity revalidation execute off the UI thread to keep large file operations responsive.

- Aksi ekspor tetap terkunci sampai pengguna menyelesaikan atau membatalkan pemilihan lokasi dokumen, mencegah dua dialog ekspor saling menimpa file sementara.

- Inferensi panjang record person dibatalkan jika beberapa UID berbeda menunjuk entity ID yang sama; panjang record tetap tidak diketahui daripada memilih offset secara arbitrer.

- Ekspor CSV menetralkan awalan formula spreadsheet dari konten save yang tidak tepercaya dan tetap melakukan escaping tanda kutip.


## Batas pemrosesan file dan pembacaan aman — 15 November 2026
- Pembacaan save dibatasi hingga 128 MiB karena parser container saat ini masih bekerja dengan representasi ByteArray di memori.
- Pemeriksaan batas dilakukan sebelum setiap blok ditambahkan ke buffer, sehingga file yang melewati batas ditolak tanpa terus memperbesar alokasi.
- Pesan kegagalan menampilkan batas aktual yang dikonfigurasi, bukan angka tetap yang bisa menjadi keliru jika konfigurasi berubah.
- Tes unit ditambahkan untuk ukuran tepat pada batas, ukuran yang melampaui batas, penutupan stream setelah sukses/gagal, dan batas negatif.
- File save yang lebih besar dari 128 MiB belum didukung. Batas ini harus ditinjau kembali setelah parser beralih ke pembacaan berbasis file/stream.
- Tes repository dan build Android belum dapat diklaim lulus sampai workflow CI benar-benar menjalankan job dan menghasilkan log/hasil tes.

- Pemeriksaan helper pembaca berbatas secara lokal dengan Kotlin 1.9 lulus untuk batas tepat, penolakan input berlebih, penutupan stream setelah sukses/gagal, dan penutupan stream saat konfigurasi batas tidak valid. Ini bukan pengganti Gradle/unit test proyek Android.

### Pengendalian memori saat membuka save — 15 November 2026
- Save kini dialirkan lebih dahulu ke file sementara di direktori cache aplikasi dengan buffer 64 KiB dan batas ukuran 128 MiB.
- Setelah penulisan selesai, file sementara dibaca menjadi satu ByteArray untuk parser saat ini, lalu file sementara dihapus pada jalur sukses maupun gagal.
- Pendekatan ini menghindari pertumbuhan ByteArrayOutputStream dan salinan penuh tambahan yang sebelumnya terjadi saat memanggil toByteArray(). Parser tetap menyimpan satu ByteArray penuh, sehingga batas 128 MiB masih berlaku.
- Tes regresi memeriksa batas tepat, penolakan input berlebih, penutupan stream, dan pembersihan file sementara.
- Smoke test helper Kotlin 1.9 lulus. Ini belum membuktikan build Android/Gradle atau uji perangkat.

- Uji tambahan mencakup kegagalan baca I/O di tengah stream; file sementara tetap dibersihkan dan input stream tetap ditutup. Batas 128 MiB juga ditampilkan di layar awal sebelum pengguna memilih save.

- GitHub Actions run 282 pada head `123f06f5e538e754ecfea3af6e73f9c7f7c76438` kembali gagal sebelum langkah job tersedia; kedua job tidak memberikan step summary atau log. Status build Android tetap belum terverifikasi.

### Ketahanan ekspor saat perubahan konfigurasi perangkat — 15 November 2026
- Referensi file sementara ekspor kini disimpan sebagai path yang dapat dipulihkan melalui saved state Compose, bukan hanya objek File dalam memori.
- Jika Android membuat ulang Activity ketika dialog penyimpanan terbuka, callback dapat memulihkan file sementara. Jika file sudah tidak tersedia, aplikasi menampilkan status kegagalan yang jelas dan tidak menganggap ekspor berhasil.

- Lifecycle ekspor kini menyimpan path payload dengan rememberSaveable dan memeriksa keberadaan file sebelum menyalin, agar Activity recreation tidak membuat ekspor palsu atau kehilangan referensi payload. Run CI 288 masih gagal tanpa step summary/log; build Android belum terverifikasi.

- Android instrumentation coverage dirapikan: pemeriksaan layar awal, minimum Android 8/API 26, batas impor 128 MiB, dan runtime Zstandard dipusatkan pada satu test activity; test layar awal duplikat dihapus.
