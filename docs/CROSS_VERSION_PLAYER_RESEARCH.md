# Cross-version player-record research notes

## Target boundary

This project targets Football Manager Touch 2021 database build `21.4.0-0`. Public reverse-engineering projects for FM24, FM26 and FM27 are useful for generating hypotheses, but their byte offsets and field ownership rules are not automatically valid for FM21 Touch.

Reference projects reviewed:

- FM24 SaveLens format notes and parser: https://github.com/MSalman5230/FM-SaveLens-24
- FM26 Gilet save-format notes: https://github.com/fifteen15-labs/gilet/blob/main/docs/SAVE_FORMAT.md
- FM26/FM27 fmsave reader: https://github.com/rhiever/fmsave

These references are not dependencies of this Android app. No code or offsets from them have been copied into the FM21 parser.

## FM21 observations from the supplied Bolton save

- `game_db/.dat` contains 17,486 accepted name-index entries.
- 15,235 unique IDs link to a duplicated-UID person entity row.
- 2,251 entries remain unresolved.
- Example identity rows for Bob Fullam, Derek Tracey and Eoin Doyle have sequential entity IDs and offsets separated by 86 bytes in this sample.
- Their observed four-byte raw record tag is `0a 80 00 f4`.
- These are person-entity observations only. They do not classify the record as a player or staff member and do not identify CA, PA or attribute fields.

## Rejected cross-version shortcut

The FM24 reader documents a candidate ability-block shape with 15 position values, a 54-byte attribute region and nearby CA/PA values. A read-only exploratory scan applied that shape to the FM21 `game_db/.dat` payload:

- 96 byte regions matched the broad value-range pattern.
- None of those candidates' proposed owner IDs matched the current FM21 person entity IDs.

This is not evidence that FM21 has no ability blocks. It is evidence that the FM24 offsets/ownership rule cannot be copied directly into this FM21 parser. Those candidates are not exposed in the app and must not be edited.

## Required FM21 proof before implementation

1. Find the FM21 player-specific table and its actual join key to the person identity.
2. Verify at least three known players from the supplied save using independent in-game or version-matched reference values.
3. Identify CA and PA with raw bytes, endianness, range and record ownership.
4. Identify each visible/hidden attribute and its raw/display scale.
5. Repeat the checks on a second save and reject ambiguous ownership.
6. Preserve unknown bytes and prove no-op and controlled-edit round trips in FM21 Touch 2021.

## Decision

Keep the current mapper read-only. Do not import FM24/26 offsets or present the 86-byte adjacency and raw tag as a complete record schema.
