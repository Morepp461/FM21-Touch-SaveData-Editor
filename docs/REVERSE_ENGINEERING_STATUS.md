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

There is no verified FM21 Touch 21.4.0 save rebuilder yet. The current Android app is deliberately read-only. Writing guessed offsets, guessed field scales, or guessed checksums would risk corrupting the career save.


## Cross-save person identity evidence (Bolton fixtures)

The two supplied saves contain the same 17,486 accepted name-index entries. The 15,235 UIDs that currently resolve to entity rows resolve to the same entity IDs in both saves, although their byte offsets move. This supports using the UID/entity ID as a cross-save join key; it does not prove player-vs-staff classification.

For entity IDs whose next ID is contiguous, the observed adjacent record spans are predominantly 86 bytes (13,427 observations) and 135 bytes (1,517 observations). These are diagnostic observations, not confirmed record types.

Only two mapped person records changed byte-for-byte after the match: Bozhidar Iskrenov and Luke Duzel, each at relative byte offset 69 (hex FF to hex 64). That byte's meaning remains unknown. It is not labelled as CA, PA, or an attribute.

A separate FM26 parser's person/ability layout was investigated as a hypothesis but was not accepted for FM21 Touch: the tested FM26 54-byte attribute-block signature did not produce candidates in the supplied FM21 game_db payload. FM21 field offsets must be established from FM21-specific evidence.
