# Cross-save person-record analysis — Bolton career

## Fixtures
Two user-supplied saves from the same career were analysed read-only:

- Before match: `Coach Morepp - Bolton backup.fmv` — 21,401,187 bytes.
- After match: `Coach Morepp - Bolton.fmv` — 21,629,895 bytes.
- Both report database version `21.4.0-0`, 68 top-level members and 784 nested members.

Original files were not modified.

## Stable identity join
The `game_db/.dat` name index contains 17,486 accepted entries in each save. All 17,486 unique IDs and names match across the two snapshots.

The current UID-to-entity scan resolves 15,235 unique IDs in each save. Every resolved UID maps to the same entity-record ID in both snapshots. The entity-record byte offsets differ because the database payload changes size and records move; offsets must therefore never be persisted as cross-save identity.

This is stronger evidence for the UID/entity-ID join than a single-save scan. It still does not classify each entity as a player, staff member, or another person subtype.

## Candidate record spans
For mapped entity IDs where the next entity ID is exactly ID + 1 and both offsets are known, the distance between consecutive records is usually:

| Observed span | Count |
|---:|---:|
| 86 bytes | 13,427 |
| 135 bytes | 1,517 |

These are observed adjacent-ID spans, not a guarantee that every record of a given type has a fixed length. The application reports them as diagnostics only.

The four bytes at record offset +12 form a recurring tag. The tag distribution is highly varied. No tag has yet been proven to mean "player", "staff", "CA", "PA", or an attribute block.

## Person rows changed after the match
Only two mapped person entity records differed byte-for-byte between the two saves after aligning them by stable entity ID and comparing their inferred record spans:

| Entity ID | Name-index name | Relative byte offset | Before | After |
|---:|---|---:|---:|---:|
| 35,441 | Bozhidar Iskrenov | 69 | `FF` | `64` |
| 52,575 | Luke Duzel | 69 | `FF` | `64` |

The meaning of byte 69 is unknown. It must not be labelled as CA, PA, morale, match rating or any other semantic field without an independent controlled fixture. The match progression could have updated an unrelated person-state field.

## Why this does not yet unlock player editing
- The person-name index includes more than confirmed first-team players.
- Stable person UIDs and entity IDs do not reveal which rows contain player-specific ability fields.
- The 86/135-byte spans and tags are not enough to infer a typed schema.
- The two-save experiment changed many components and did not alter a single known player ability or finance value.
- No write/rebuild operation has been validated against a save loaded by the game.

## Next controlled data experiments
Use a separate save copy for each experiment and change exactly one known in-game value where the game exposes it. Record the player's name, the old/new value shown in-game, and the save pair. A separate known-value fixture is needed for CA/PA, an individual attribute, club balance, transfer budget, prize money and league reputation.

Until these fixtures exist, the Android app remains read-only for these fields. This is deliberate: a false edit that corrupts a career is worse than a disabled control.


## Reproducible diagnostic tool

A read-only Python diagnostic is included under `tools/`:

```bash
python tools/analyze_person_records.py before.fmv after.fmv --json person-record-report.json
cd tools && python -m unittest -v
```

The tool validates and extracts `game_db/.dat`, repeats the name/UID/entity join, reports tag and adjacent-ID span distributions, and compares mapped records by stable UID/entity ID. It never writes to either input save. It requires Python 3.10+, the system Zstandard library (`libzstd`), and the sibling `fm21_touch_container.py` helper.

The diagnostic was reproduced against both supplied private fixtures: 17,486 matching name/UID entries, 15,235 resolved entity joins in each save, all 15,235 entity IDs stable across the pair, zero ambiguous UIDs, and two changed mapped rows. Three synthetic unit tests pass.

This improves reproducibility, not semantic certainty: player/staff classification and all editable fields remain unproven.
