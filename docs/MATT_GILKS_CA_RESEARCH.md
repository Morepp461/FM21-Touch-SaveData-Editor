# Riset identitas Matt Gilks dan CA/PA

## Referensi dari game

Empat screenshot yang sudah diberikan pengguna mencakup daftar pemain Bolton (gambar 1–2), daftar staf Bolton (gambar 3), dan profil Matt Gilks (gambar 4). Screenshot daftar pemain/staf diperlakukan sebagai daftar identitas untuk klasifikasi, sedangkan screenshot profil menjadi referensi nilai individual.

Screenshot profil Bolton yang diberikan menunjukkan:
- Nama yang tampil di game: **Matt Gilks**
- Current Ability (CA): **81**
- Recommended Current Ability: **80**
- Posisi utama: goalkeeper.

CA 81 dan Recommended CA 80 adalah dua nilai UI yang berbeda. Nilai Recommended CA tidak boleh dianggap sebagai PA.

## Hasil pencarian pada save

Pencarian dilakukan pada komponen `game_db/.dat` dari `Coach Morepp - Bolton.fmv` dengan parser name-index yang sudah divalidasi.

- Entri nama persis **Matt Gilks** tidak ditemukan pada name-index.
- Ada satu entri bernama **Michael Gilkes**, UID **2000086420**, name-index ID **10972**.
- UID tersebut terhubung secara unik ke entity ID **36130**, dengan offset record pada snapshot ini **29,449,099**.
- Ejaan dan nama depannya berbeda dari nama yang tampil pada screenshot. Karena itu, **Michael Gilkes hanya kandidat untuk diperiksa, bukan identitas Matt Gilks yang sudah terkonfirmasi**.
- String `Gilks` juga muncul di tabel nama keluarga, tetapi tabel tersebut sendiri tidak cukup untuk menghubungkan nama tampilan dengan person UID.
- Nama manager `Morepp` muncul pada data nama terpisah, tetapi pencarian ini belum membuktikan relasi dengan person record tertentu.

Offset di atas hanya berlaku untuk snapshot save ini. Jangan gunakan offset mentah sebagai identitas lintas-save.

## Status pemetaan CA/PA

**Belum ditemukan.** Screenshot hanya memberi nilai ground-truth di UI, bukan lokasi penyimpanan nilai di database biner. Belum ada bukti yang menghubungkan CA 81 ke byte, field, atau encoding tertentu. PA juga belum diketahui.

Tidak boleh:
- Menganggap byte tertentu sebagai CA/PA karena nilainya kebetulan 81 atau 80.
- Menganggap Recommended Current Ability sebagai PA.
- Mengubah record kandidat Michael Gilkes sebelum identitasnya diverifikasi.
- Mengaktifkan kontrol edit CA/PA sebelum tes simpan dan pemuatan ulang di game berhasil.

## Eksperimen yang diperlukan

1. Gunakan daftar pemain dan staf dari screenshot 1–3 sebagai label ground-truth untuk klasifikasi orang di save. Jangan meminta ulang screenshot yang sudah tersedia.
2. Cocokkan nama lengkap, alias, tanggal lahir, kewarganegaraan, dan klub dari profil pemain/staf dengan entri database sebelum menetapkan UID.
3. Jangan menyamakan Michael Gilkes dengan Matt Gilks hanya berdasarkan kemiripan ejaan. Perlu satu identitas tambahan yang cocok atau referensi database FM21 yang tepat.
4. Buat salinan save sebelum eksperimen.
5. Ubah satu nilai yang bisa diubah secara sah di dalam game, simpan, lalu buat pasangan save sebelum/sesudah. Untuk CA/PA, jika game tidak menyediakan cara mengubah nilai tersebut, dibutuhkan fixture terkontrol lain yang memang mengubah nilai itu.
6. Bandingkan record berdasarkan UID/entity ID stabil, bukan offset.
7. Ulangi dengan nilai lain dan pastikan perubahan yang terdeteksi konsisten.
8. Baru setelah encoding dan rentang nilai terbukti, buat implementasi edit dengan preview diff, backup, validasi, dan uji load di FM21 Touch.

## Kesimpulan

Screenshot sudah memastikan nilai CA 81 untuk pemain yang tampil sebagai Matt Gilks. Namun save yang dianalisis belum memberi hubungan identitas yang cukup kuat untuk mengaitkannya dengan person record tertentu. Karena itu, riset CA/PA tetap terbuka dan fitur edit tetap read-only.
