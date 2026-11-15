# FM21 Touch 21.4.0 reverse-engineering status

## Confirmed

- Target save version string: `21.4.0-0`.
- Outer header begins with `02 01 fmf.`.
- The file has a footer manifest compressed with Zstandard.
- The manifest has 68 top-level entries and 784 nested entries in both supplied Bolton saves.
- Member offsets are relative to byte 30.
- Members can contain concatenated Zstandard frames. A four-byte little-endian compressed-frame length precedes each subsequent frame.
- The final member's stored boundary includes the first four bytes of the footer marker.
- A read-only Python validator successfully extracted all 852 members from both supplied saves.

## Candidate data areas

| Requested editor | Relevant component candidates | Current conclusion |
|---|---|---|
| Player Record Mapping | `game_db/.dat`, `person_record_manager/.dat`, `person_db_changes/.cmt` | Player identity and record schema not proven |
| CA / PA | `game_db/.dat` | Exact field offsets and ownership not proven |
| Player Attributes | `game_db/.dat` | Attribute ordering/scales not proven |
| Club Finance | `finance_manager/.dat`, `game_db/.dat`, `starting_club_debt_db/.cmt` | Balance and budget fields not proven |
| Prize Money | `game_db/.dat`, `comp_man/.dat`, `comp_*.dat`, `comp_history_dt/.cmt` | Competition/position/amount records not proven |
| League Reputation | `game_db/.dat`, `comp_man/.dat` | League IDs and reputation scale not proven |

The component names indicate likely areas to investigate; they do not prove the requested values are stored there.

## Required controlled evidence

A before/after match pair is not sufficient to identify a specific editable field. The next useful fixtures are independent save copies where one known in-game value changes at a time and the changed value can be verified on-screen. Record the exact player/club/competition, old value, new value, and save file hash for each experiment.

## Writer status

The container layout is sufficiently understood to extract all observed members, and the repository documents an experimental Kotlin rebuilder plus an independent Python structural round-trip. This is not yet a production-ready Android writer: Kotlin writer validation against original real-save fixtures and a successful in-game load of a rebuilt FM21 Touch save remain pending. Semantic record edits are not authorized. The current Android app therefore remains read-only for game data. Writing guessed offsets, guessed field scales, or guessed checksums would risk corrupting the career save.


## Cross-save person identity evidence (Bolton fixtures)

The two supplied saves contain the same 17,486 accepted name-index entries. The 15,235 UIDs that currently resolve to entity rows resolve to the same entity IDs in both saves, although their byte offsets move. This supports using the UID/entity ID as a cross-save join key; it does not prove player-vs-staff classification.

For entity IDs whose next ID is contiguous, the observed adjacent record spans are predominantly 86 bytes (13,427 observations) and 135 bytes (1,517 observations). These are diagnostic observations, not confirmed record types.

Only two mapped person records changed byte-for-byte after the match: Bozhidar Iskrenov and Luke Duzel, each at relative byte offset 69 (hex FF to hex 64). That byte's meaning remains unknown. It is not labelled as CA, PA, or an attribute.

A separate FM26 parser's person/ability layout was investigated as a hypothesis but was not accepted for FM21 Touch: the tested FM26 54-byte attribute-block signature did not produce candidates in the supplied FM21 game_db payload. FM21 field offsets must be established from FM21-specific evidence.


## Uploaded fixture revalidation (2026-11-14)

The uploaded after-match fixture was validated with the repository's read-only container parser:

- File SHA-256: `abd98f78ff741279f237ce18c064fb919709f03c596289a3975087e152b2124e`.
- 68 top-level and 784 nested members (852 total).
- All 852 members extracted successfully across 1,610 Zstandard frames.
- Extracted payload total: 107,929,383 bytes; no declared-length or member-boundary failures.
- `game_db/.dat`: 69,387,047 bytes; SHA-256 `65aa092a01c2b390a78d194952308bf3e5e17a81d658d10792c0771b40271f64`.
- Person index diagnostic: 17,486 accepted names, 15,235 resolved UID/entity joins, zero ambiguous joins, and 2,251 unresolved entries.

This revalidation confirms container extraction and the existing person-index join. It does not establish player/staff classification, CA/PA, attribute fields, finance fields, or a safe semantic write path.


## Dua snapshot karier Bolton lintas musim (15 November 2026)

- Pengguna memberikan dua save dengan nama manifest `Coach Morepp - Bolton`, versi database `21.4.0-0`, dan menyatakan keduanya berjarak satu musim.
- Kedua file berhasil diekstrak penuh: 852/852 komponen masing-masing; 1.610 dan 1.605 frame Zstandard; tidak ada error panjang atau batas komponen.
- Sebanyak 688 komponen identik dan 164 berbeda; tidak ada komponen yang hanya ada pada salah satu file.
- 17.486 UID name-index sama pada kedua file. Sebanyak 15.235 UID terhubung mempertahankan entity ID yang sama, walaupun offset record bergeser.
- Dalam batas record person yang terpetakan secara konservatif, dua record berubah: Bozhidar Iskrenov dan Luke Duzel, masing-masing byte relatif 69 berubah dari `FF` pada backup menjadi `64` pada file pertama. Arti field tetap tidak diketahui.
- `finance_manager/.dat` identik. Perbedaan pada komponen kompetisi, transfer, manager, sejarah, dan `game_db/.dat` tidak boleh dianggap sebagai field semantik tertentu tanpa decoding atau fixture terkontrol.
- Pasangan lintas musim ini berguna untuk stable-UID joins dan diff lintas-save, tetapi bukan eksperimen satu-field. File asli tetap privat dan tidak masuk repository.
- Laporan lengkap: `docs/CROSS_SEASON_FIXTURE_ANALYSIS.md`.
