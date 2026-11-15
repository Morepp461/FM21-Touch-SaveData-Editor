# FM21 Touch 21.4.0-0 — Person Record Mapping

## Current result

The supplied `Coach Morepp - Bolton` save was analysed read-only. Its `game_db/.dat` payload contains a name index whose entries can be linked to person entity rows through a duplicated unique ID.

| Measurement | Result |
|---|---:|
| Name-index entries detected | 17,486 |
| Entries linked to a person entity row | 15,235 |
| Entries without a matching entity row | 2,251 |

The Android `PersonRecordMapper` implements this mapping and deliberately leaves unresolved entries blank.

## Observed name-index entry

The following byte sequence was observed repeatedly in the FM21 Touch 21.4.0-0 game database:

`u32 name_byte_length`
`UTF-8 name bytes`
`u32 flags`
`u32 name_index_id`
`u32 unique_id`
`u32 unique_id (duplicate)`
`u32 entry_kind = 5`
`u8 terminator = 0`

The parser validates the duplicate UID, entry kind and terminator before accepting a name. It does not treat a name string alone as proof of a player record.

## Person entity link

A separate region contains rows beginning with:

`u32 entity_record_id`
`u32 unique_id`
`u32 unique_id (duplicate)`
`opaque person fields ...`

The mapper joins the two structures on the duplicated unique ID. It rejects conflicting entity-row matches rather than selecting one arbitrarily.

## Confirmed examples from the supplied save

| Name | Name-index ID | Unique ID | Entity record ID |
|---|---:|---:|---:|
| Bob Fullam | 26630 | 2000102078 | 51788 |
| Derek Tracey | 26631 | 2000102079 | 51789 |
| Eoin Doyle | 26632 | 2000102080 | 51790 |

These examples demonstrate the name-to-person-record join. The names are not hard-coded in the application.

## Important limitations

- The name index is a person index, not yet a proven player-only list. Staff and other person types may also be present.
- The entity record IDs are internal to this save and must not be assumed stable across saves or database versions.
- The 2,251 unresolved name entries remain unresolved; the application does not invent an entity ID for them.
- This mapping does not yet identify the CA, PA or player-attribute fields.
- It does not establish field scales, byte offsets or writable record boundaries.

## Next reverse-engineering stage

1. Identify which person records are player records versus staff/other person types.
2. Locate the player-specific record/table and establish its join key.
3. Use independently verifiable player profiles to identify CA and PA.
4. Map visible and hidden attribute fields, scales and ordering.
5. Confirm each field against more than one player and a second save before enabling edits.

No save-writing code should use this mapping until the player classification and player-specific record layout are independently verified.


## Additional record-shape observations

The Android mapper now records the four-byte tag immediately following the duplicated UID pair. It also reports a record length only when the next mapped entity ID is exactly the current entity ID + 1 and the next mapped row offset is greater. The length is therefore a local adjacency observation, not a global fixed-size assumption.

Initial analysis of the supplied save found two common distances between consecutively mapped entity IDs: 86 bytes and 135 bytes. The app exposes these as observations only. The tags and lengths have not yet been assigned semantic names such as player, staff, contract, CA or PA.


## Daftar acuan dari screenshot Bolton (19 September 2020)

Screenshot yang sudah diberikan pengguna dipakai sebagai daftar acuan, bukan diminta ulang.

### Pemain yang terlihat

| Nama di game | Posisi yang terlihat |
|---|---|
| Matt Gilks | GK |
| Lukas Jensen | GK |
| Billy Crellin | GK |
| Shay Facey | D (R) |
| Jak Hickman | D (R) |
| Alex Baptiste | D (C) |
| Liam Edwards | D (C) |
| Ricardo Almeida Santos | D (C) |
| Harry Brockbank | D (C) |
| Ryan Delaney | D (C) |
| Gethin Jones | D/WB (R) |
| M J Williams | D (C), DM |
| Declan John | D/WB (L) |
| Brandon Comley | DM, M (C) |
| Andrew Tutte | M (C) |
| Kieran Lee | M (C) |
| George Thomason | M (C) |
| Antoni Sarcevic | M (C) |
| Ben Jackson | M (L) |
| Lloyd Isgrove | M/AM (R) |
| Ronan Darcy | AM (C) |
| Ali Crawford | AM (C) |
| Marcus Maddison | AM (RLC) |
| Zack Elbouzedi | AM (L) |
| Dennis Politic | AM (RLC) |
| Finlay Hurford-Lockett | AM (RL) |
| Arthur Gnahoua | AM (L), ST (C) |
| Nathan Delfouneso | ST (C) |
| Eoin Doyle | ST (C) |
| Oladapo Afolayan | ST (C) |

### Staf yang terlihat

| Nama di game | Jabatan |
|---|---|
| Sharon Brittan | Chairman |
| Andrew Caton | Director of Football |
| Coach Morepp | Manager |
| Peter Atherton | Assistant Manager |
| Mark Litherland | Head of Youth Development |
| Matt Barrass | Head Physio |
| Gary Clayton | Chief Scout |

### Profil terperinci yang terlihat

Screenshot profil Matt Gilks menunjukkan:
- Current Ability: **81**
- Recommended Current Ability: **80** (nilai terpisah, bukan PA)
- Posisi: Goalkeeper
- Usia: 38
- Tinggi/berat: 185 cm / 80 kg
- Tanggal lahir tampil sebagai `4/6/1982` (format tampilan game; belum dinormalisasi)
- Atribut terlihat pada screenshot: Aerial Reach 10, Command Of Area 10, Communication 11, Eccentricity 3, First Touch 10, Handling 12, Kicking 10, One On Ones 11, Passing 9, Punching (Tendency) 7, Reflexes 10; Aggression 5, Anticipation 13, Bravery 14, Composure 10, Concentration 11, Decisions 10, Determination 15, Flair 3, Leadership 10, Off The Ball 3, Positioning 10; Acceleration 7, Agility 10, Balance 12, Jumping Reach 14, Natural Fitness 12, Pace 7, Stamina 11, Strength 9; Free Kick Taking 5, Penalty Taking 4.
- Kewarganegaraan yang terlihat: Scotland dan England.

### Hasil pencocokan nama dengan indeks save

Nama pada layar game tidak selalu sama dengan string yang tersimpan di name-index. Dalam pencarian eksak pada name-index FM21, hanya **Eoin Doyle** dari daftar screenshot yang ditemukan dengan ejaan persis. Matt Gilks tidak ditemukan dengan ejaan persis; kandidat **Michael Gilkes** tetap belum terkonfirmasi dan tidak boleh disamakan otomatis.

Karena itu, daftar screenshot sudah menjadi ground-truth untuk label manusia, tetapi pemetaan UID masih harus menangani alias, nama terpisah, nama keluarga, dan transliterasi. Jangan menandai semua orang yang tidak ditemukan sebagai data yang hilang; bisa jadi format name-index menyimpan nama berbeda.


## Stabilitas mapping lintas dua snapshot

Pemindaian ulang kedua `game_db/.dat` menunjukkan bahwa seluruh 15.235 UID terhubung mempertahankan entity ID dan empat byte raw tag yang sama. Offset record pada snapshot yang lebih baru bergeser konsisten `+1.116` byte. Distribusi panjang observasi identik: 13.427 record 86 byte, 1.517 record 135 byte, 80 record dengan jarak lain, dan 211 record tanpa batas berikutnya yang dapat dipastikan.

Contoh yang bisa dipastikan dari name-index:
- Eoin Doyle: UID `2000102080`, entity ID `51790`, raw tag `0a8000f4`, panjang observasi `86` byte.
- Michael Gilkes: UID `2000086420`, entity ID `36130`, raw tag `0a800068`, panjang observasi `86` byte. Hubungan nama ini dengan Matt Gilks pada screenshot tetap belum terkonfirmasi.

Raw tag dan panjang tersebut hanya ciri struktural. Tidak boleh dipakai untuk klasifikasi pemain/staf atau penentuan field CA/PA/atribut.
