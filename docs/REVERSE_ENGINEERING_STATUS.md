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
