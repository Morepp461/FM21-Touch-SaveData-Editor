# Analisis dua fixture satu karier — Bolton

Tanggal analisis: 15 November 2026

## Identitas fixture

Kedua file diberikan sebagai dua snapshot dari karier yang sama dengan jarak satu musim. Pengguna mengonfirmasi bahwa `Coach Morepp - Bolton.fmv` adalah snapshot yang lebih baru (satu musim setelah backup). Dengan demikian, arah perbandingan pada tabel dan diff di bawah adalah **backup lama → file utama terbaru**. Parser sendiri memverifikasi identitas save, versi database, struktur container, dan perbedaan byte.

| Properti | `Coach Morepp - Bolton.fmv` | `Coach Morepp - Bolton backup.fmv` |
|---|---:|---:|
| SHA-256 | `abd98f78ff741279f237ce18c064fb919709f03c596289a3975087e152b2124e` | `955e1f209acb98a7e3534fdc81dae57864704dafd6638da0e51e372da00c0e8f` |
| Ukuran file | 21,629,895 byte | 21,401,187 byte |
| Nama save di manifest | Coach Morepp - Bolton | Coach Morepp - Bolton |
| Database | 21.4.0-0 | 21.4.0-0 |
| Komponen top-level | 68 | 68 |
| Komponen nested | 784 | 784 |
| Total komponen | 852 | 852 |
| Frame Zstandard terurai | 1,610 | 1,605 |
| Total plaintext | 107,929,383 byte | 107,314,935 byte |
| Kesalahan ekstraksi | 0 | 0 |

## Perbandingan komponen

- 688 dari 852 komponen identik byte-per-byte setelah dekompresi.
- 164 komponen memiliki payload berbeda.
- Tidak ada komponen yang hanya muncul pada salah satu save.
- `game_db/.dat` berukuran 69,387,047 byte pada file pertama dan 69,212,563 byte pada backup.
- `finance_manager/.dat` identik byte-per-byte pada kedua snapshot.
- Perbedaan pada komponen kompetisi, transfer, manager, sejarah, dan database umum mencerminkan banyak perubahan lintas musim. Tanpa decoder record dan eksperimen terkontrol, perubahan tersebut tidak boleh langsung diberi label sebagai field tertentu.

## Konsistensi identitas person lintas-save

| Pemeriksaan | Hasil |
|---|---:|
| Entri name-index pada masing-masing save | 17,486 |
| UID yang sama pada kedua save | 17,486 |
| UID terhubung ke entity row pada masing-masing save | 15,235 |
| UID terhubung yang mempertahankan entity ID | 15,235 |
| UID belum terhubung | 2,251 pada masing-masing save |

Semua 15,235 UID terhubung mempertahankan entity ID yang sama meskipun offset record bergeser karena ukuran dan susunan payload database berubah. Ini memperkuat UID/entity ID sebagai join key lintas-save untuk person index yang diamati. Indeks tetap merupakan indeks person, bukan daftar pemain saja.

## Perubahan pada record person yang terpetakan

Dalam batas record person yang dapat ditentukan secara konservatif dari entity ID berurutan, hanya dua record terpetakan yang berbeda:

| Nama pada indeks | UID | Entity ID | Offset relatif yang berubah | Backup → file pertama |
|---|---:|---:|---:|---|
| Bozhidar Iskrenov | 2000085731 | 35441 | 69 | `FF → 64` |
| Luke Duzel | 2000102865 | 52575 | 69 | `FF → 64` |

Kedua record memiliki panjang observasi 86 byte pada kedua save dan tag record yang sama. Arti byte relatif 69 belum diketahui. Jangan menyebutnya CA, PA, atribut, status kontrak, atau field lainnya sebelum dibuktikan dengan fixture terkontrol.

## Kesimpulan dan batas bukti

Pasangan ini sangat berguna untuk menguji kestabilan identitas person lintas musim, perpindahan offset, dan perubahan struktur database yang nyata. Pasangan ini **bukan** eksperimen satu-field: satu musim dapat mengubah banyak kompetisi, statistik, transfer, kontrak, dan data lain sekaligus.

Langkah berikutnya:
1. Gunakan UID/entity ID yang sama untuk membangun diff record lintas-save, jangan mencocokkan offset mentah.
2. Telusuri dua perubahan byte person sebagai kandidat observasi saja; belum boleh ditulis.
3. Pisahkan komponen yang berubah karena jadwal, hasil, statistik, transfer, kontrak, dan perubahan database sebelum menguji field semantik.
4. Tetap minta pasangan fixture terkontrol satu nilai untuk CA, PA, atribut, saldo, budget, prize money, dan reputasi sebelum membuka editor.

File save asli tidak dimasukkan ke repository. Hanya hash, ringkasan, dan hasil analisis yang dicatat di dokumen ini.


## Audit kestabilan record person lanjutan — 15 November 2026

Kedua payload `game_db/.dat` dipindai ulang dengan parser name-index dan join UID/entity yang sama:

- Kedua snapshot menghasilkan 17.486 entri name-index yang sama persis.
- Semua 15.235 UID yang terhubung menunjuk ke entity ID yang sama pada kedua snapshot; tidak ada UID ambigu pada hasil pemindaian ini.
- Offset seluruh 15.235 entity record pada snapshot yang lebih baru bergeser tepat `+1.116` byte dibanding snapshot backup. Ini merupakan pergeseran global, bukan perubahan offset individual.
- Keempat byte raw tag pada semua 15.235 record tetap identik lintas snapshot.
- Distribusi panjang record hasil inferensi juga identik: 13.427 record berjarak 86 byte, 1.517 berjarak 135 byte, 80 memiliki jarak lain yang masih lolos batas diagnostik, dan 211 tidak memiliki batas berikutnya yang cukup untuk inferensi.

Temuan ini memperkuat stable-UID join dan menunjukkan bahwa offset mentah tidak boleh dipakai sebagai identitas. Raw tag dan panjang record tetap belum diklasifikasikan sebagai pemain, staf, CA, PA, atau atribut; tidak ada field edit yang dapat dibuka dari hasil ini.
