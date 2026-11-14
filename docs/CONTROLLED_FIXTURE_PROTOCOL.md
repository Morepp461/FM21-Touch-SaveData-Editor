# Protokol fixture terkontrol untuk reverse engineering FM21 Touch

Dokumen ini menjelaskan bukti minimum yang dibutuhkan sebelum satu field database boleh dinyatakan dapat diedit. Semua eksperimen dilakukan pada salinan save; file asli tidak boleh ditimpa.

## Prinsip
- Gunakan save dari satu karier dan satu versi database yang sama.
- Ubah tepat satu nilai yang diketahui melalui mekanisme game yang sah pada setiap eksperimen.
- Catat nilai sebelum dan sesudah persis seperti yang ditampilkan game.
- Jangan menganggap perubahan byte sebagai CA, PA, atribut, saldo, atau anggaran hanya karena nilainya terlihat masuk akal.
- Cocokkan entitas dengan UID/entity ID yang stabil, bukan offset byte yang dapat bergeser.
- Jangan memasukkan save privat ke repository.

## Catatan untuk setiap eksperimen

| Kolom | Isi |
|---|---|
| Fixture ID | ID lokal, misalnya `player-attribute-01` |
| Game version | Versi FM21 Touch lengkap |
| Database version | Contoh `21.4.0-0` |
| Save before | Nama file lokal sebelum perubahan |
| SHA-256 before | Hash file sebelum perubahan |
| Save after | Nama file lokal sesudah perubahan |
| SHA-256 after | Hash file sesudah perubahan |
| Entity | Nama yang tampil, klub/negara, UID dan entity ID jika terpetakan |
| Field | Satu nilai yang sengaja diubah |
| Old value | Nilai sebelum perubahan di dalam game |
| New value | Nilai sesudah perubahan di dalam game |
| Exact action | Langkah dalam game yang menghasilkan perubahan |
| Observations | Perubahan lain yang diketahui, jika ada |

## Eksperimen yang dibutuhkan

| ID | Target | Kriteria fixture |
|---|---|---|
| `player-ca-01` | Current Ability | Satu pemain teridentifikasi, nilai CA lama/baru diketahui |
| `player-pa-01` | Potential Ability | Hanya jika ada cara terkontrol untuk mengubah PA |
| `player-attribute-01` | Satu atribut pemain | Ubah satu atribut saja; jangan sekaligus mengubah banyak atribut |
| `club-balance-01` | Saldo klub | Catat saldo sebelum/sesudah, mata uang dan klub |
| `transfer-budget-01` | Anggaran transfer | Pisahkan dari saldo klub |
| `prize-money-01` | Hadiah kompetisi | Catat kompetisi, posisi dan nilai hadiah |
| `league-reputation-01` | Reputasi liga | Catat liga dan nilai sebelum/sesudah |

## Analisis dan keputusan
1. Validasi kedua container dan ekstrak komponen relevan.
2. Bandingkan record memakai stable UID/entity ID; jangan memakai offset lintas-save sebagai identitas.
3. Catat seluruh byte yang berubah beserta rentang record dan komponen asal.
4. Ulangi eksperimen dengan nilai berbeda untuk membedakan field, encoding, skala, endianness, dan batas rentang.
5. Tandai field `VERIFIED` hanya setelah perubahan terkontrol berulang menghasilkan pola konsisten dan tidak ada field lain yang ikut berubah tanpa penjelasan.
6. Implementasi edit harus memiliki guard byte asli, validasi rentang, preview diff, undo/redo, backup, rebuild, dan pemeriksaan semua komponen.
7. Uji save hasil edit dengan memuatnya di FM21 Touch. Tanpa keberhasilan load dan pemeriksaan nilai di dalam game, status tetap `NOT VERIFIED`.

## Status bukti saat ini

- Container FMV 21.4.0-0: struktur dan ekstraksi 852 komponen sudah diverifikasi pada fixture nyata.
- Python container no-op round-trip: semua plaintext komponen cocok byte-per-byte setelah dibuka kembali.
- Kotlin FMV rebuilder: tes sintetis tersedia; validasi rebuild terhadap fixture nyata dan pemuatan di game masih menjadi gerbang.
- UID ke entity ID: diagnostik person terhubung telah direproduksi; klasifikasi pemain/staf belum terbukti.
- CA, PA, atribut, finansial, hadiah kompetisi, dan reputasi: belum ada field semantik yang diverifikasi.