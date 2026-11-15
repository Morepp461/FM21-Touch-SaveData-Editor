# FM21 Touch 21.4.0 database person-index research

Date: 15 November 2026

## Inputs inspected

The Library contains the selected 21.4.0 FMC metadata and these extracted database tables. Raw files were materialized for local, read-only analysis; no database payloads or player names are committed to this repository.

| File | Bytes | SHA-256 |
|---|---:|---|
| `basic_people_db.dat` | 3,996,524 | `a8401f25fc7d5a23e8e28343c4dc8a76fe2696c8432e312014efd65c5e7aeed1` |
| `lang_db.dat` | 54,830,174 | `1c69360c618156504f7fb9386c42a0d711c28dd48040e55d258c0a82eb9cdc23` |
| `non_pl_hist_dt.dat` | 26,769,548 | `edbf896b6c022cfbdb1001b5f1481398e8e6fb90017f8c2bdcf43195d4931572` |
| `non_pl_hist_id.dat` | 2,390,712 | `ba28537a42df289181621ff6411ede7217344bd1c80285dfdd174b28ceb8db86` |
| `non_pl_hist_index.dat` | 2,420,084 | `73c8a96d26f60acb245604dd85bac5bd861dc7b57e66d10375e1ea7584926189` |
| `people_db.dat` | 20,281,092 | `2b00a68c6af56c35ea6d4e4dc1adb3d7476fcd0b1f50a723755fee6654bd382b` |
| `pl_hist_dt.dat` | 67,466,368 | `76ac0b9658cb1ec86f7502d3e6222f70d982fbdf1cd1c4756aa9789722402ff7` |
| `pl_hist_id.dat` | 1,398,472 | `46a4d803d5ea560b6fcd9b79f3db5979694c89a73afac282a260bd5c85a0905d` |
| `pl_hist_index.dat` | 1,398,476 | `ea1a17cfd4cc8f2a33b3be64757e3a85d31e184243121bd3c2b903d6a30f3880` |

The metadata file identifies database version 21.4.0, required game version 21.3.1, FMC enabled, and FMM disabled.

## Structural observations

- `basic_people_db.dat` and `people_db.dat` use the `03 01 tad.` wrapper. The two person tables contain concatenated Zstandard frames; subsequent frames are preceded by a four-byte frame-length value.
- The decompressed `people_db.dat` contains a repeatable name-index pattern: a type marker, zero/reserved bytes, UTF-8 byte length, name bytes, a terminator, a 32-bit index value, and a duplicated 32-bit UID.
- A conservative structural scan identified 1,569 name/UID entries. This is a pattern-based extraction, not a claim that every person in the full database was enumerated.
- A matching scan of `basic_people_db.dat` identified 1,722 entries using the corresponding duplicated-UID index pattern. All 1,569 extracted name UIDs were present in that index.
- 1,567 of the 1,569 extracted UIDs also occur in the supplied Bolton career save's `game_db/.dat` person-name index. This gives a useful cross-reference between the database fixture and the save.
- The separate `Morepp 1 - Vreeo.id.fmv` sample also parses through the existing FMV reader (838 manifest entries; 17,772 person-name index entries; 15,082 resolved person entities). It is a different career and is not treated as a controlled before/after pair.

## What this establishes

The database fixture provides an additional identity cross-reference by UID. It can help distinguish database-known people from save-only/generated entries and can support later player/staff research.

## What it does not establish

- The person-name index is not the player attribute record.
- The duplicated UID index does not reveal the offset or type of a CA, PA, or individual attribute field.
- The structural scan does not classify every person as player or staff.
- No field order, scale, signedness, or write semantics are inferred from the FMRTE property list.
- No save write is authorized by this research.

The production writer must remain fail-closed for non-empty edits until a field is independently mapped, read back, and validated by loading the edited save in FM21 Touch.


## Reproducibility

The structural counts can be regenerated locally with:

```bash
python tools/fm21_database_person_index.py /path/to/extracted-2140-database \
  --game-db /path/to/extracted/game_db.dat \
  --json database-person-index-report.json
python -m unittest discover -s tools -p 'test_*.py' -v
```

The parser emits hashes and aggregate counts only. It does not export names or UIDs, does not modify source files, and deliberately leaves all player-field semantics unresolved.
