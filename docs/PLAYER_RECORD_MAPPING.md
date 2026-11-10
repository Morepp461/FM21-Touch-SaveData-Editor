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
