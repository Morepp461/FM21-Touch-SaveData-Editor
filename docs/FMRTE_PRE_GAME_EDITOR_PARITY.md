# FMRTE / Pre-Game Editor-Inspired Feature Blueprint

Status: product specification and parity map only. This document does not claim that the corresponding FM21 Touch fields have been reverse-engineered or are writable.

## Reference products and adaptation

The product direction is informed by publicly documented FMRTE capabilities and the Football Manager Pre-Game Editor concept. The app must implement its own Android-first workflows and data model. Do not copy proprietary source code, artwork, UI assets, or branding.

- FMRTE documents save-time editing across players, clubs, nations, stadiums, competitions, staff, contracts, finances, facilities, reputation, presets, search, mass edit, and edit history.
- The Pre-Game Editor model is a separate workflow for changing the base database before starting a new career.
- FM21 Touch Android save editing is not equivalent to editing a desktop game's live process or editing an FMF pre-game database. The implementation must target the actual FMV save structure and prove every field mapping independently.

## Product modes

### 1. Save Editor (existing career)

Open an FM21 Touch .fmv file, inspect the whole career database, stage edits, review a diff, export a new save, then validate it. Never overwrite the source.

### 2. Database Editor (new career)

A future separate mode for supported pre-game database files only. It must not be exposed as available until the FM21 Touch database format, file dependencies, and game import path are independently verified. A save-file editor cannot silently pretend to be a pre-game editor.

## Feature parity map

| Area | Target capabilities | Data domains that must be proven |
|---|---|---|
| Player editor | Identity, CA, PA, attributes, positions, preferred moves, personality, reputation, fitness, morale, injury, bans | Person identity, player extension, attribute encoding and scales, status records |
| Staff editor | Staff attributes, qualifications, roles, reputation, preferred tactics | Person identity, staff extension, job/club links |
| Contract editor | Wage, expiry, bonuses, clauses, playing time, happiness | Contract records, currency units, dates, relationship keys |
| Club editor | Name, reputation, facilities, attendance, stadium, kits, colours, affiliations | Club entity, facility fields, stadium and affiliation links |
| Club finance | Balance, transfer budget, wage budget, debt, loans, sponsorship, income/expenses | Finance records, signedness, currency scale, season/time semantics |
| Competition editor | Name, reputation, rules, standings, points, fixtures, prize money | Competition entity, stage/round IDs, table rows, prize schedules |
| Nation editor | Nationality, nation reputation, rules, transfer windows, eligibility | Nation entity and rule tables |
| Stadium editor | Name, capacity, stands, pitch and facilities | Stadium entity and club links |
| Search & filters | Search all entities, filter by role, age, nationality, club, competition, attributes and contracts | Stable entity index and normalized searchable fields |
| Mass Edit | Apply a reviewed change to selected rows, squad, club or search result | Bulk transaction model, per-record validation and partial-failure handling |
| Presets | Reusable named groups of changes, preview before apply | Versioned preset schema and compatibility checks |
| Edit history / undo | Per-field before/after values, batch grouping, undo when source value has not changed | Durable edit journal and conflict detection |
| Compare | Side-by-side players, clubs and competitions | Typed comparable fields and normalized display |
| Import/export | CSV for supported fields, backup, restore, copy/paste profiles | Stable IDs, schema version and validation |
| Safety & recovery | Source immutable, backup, preview, validation, rollback, post-export re-open | Hashes, rebuild integrity and tested recovery |

## Navigation and interaction model

1. Home: loaded save summary, database version, entity counts, validation status, recent edits.
2. People: Players / Staff / Unassigned people, with global search and filters.
3. Clubs: all clubs, squads, staff, finances, facilities, stadium and affiliations.
4. Competitions: leagues, cups, rules, standings, reputation and prize money.
5. World: nations, cities, stadiums and other verified entity families.
6. Tools: Mass Edit, Presets, Compare, Import/Export and Edit History.
7. Files: save import, backup, validation report and export.

Entity detail pages should use section tabs, a compact identity header, explicit editable/read-only field states, inline range validation, unsaved-change markers, and a sticky review/apply action. On phones, prefer searchable lists and focused edit sheets over dense desktop-style tables.

## Edit transaction contract

Every edit must follow this sequence:

1. Resolve a stable entity ID and entity type.
2. Load the verified field definition, encoding, range and owning record.
3. Capture the original raw bytes and decoded value.
4. Validate the proposed value and all dependent constraints.
5. Show a before/after preview, including the affected entity and field.
6. Stage the edit in an isolated change set; do not mutate the source file.
7. Apply edits to a copy of the correct component while preserving unknown bytes.
8. Rebuild the FMV container and validate manifest, frame boundaries, lengths and hashes.
9. Re-open the exported file through the app's parser.
10. Confirm the save loads in FM21 Touch and the intended value appears in-game.
11. Record the edit in history with undo information.

If any stage fails, reject the export and preserve the original save. Bulk edits must report exactly which records succeeded or failed; no silent partial success.

## Field definition registry

Do not hard-code guessed offsets in screens. Each writable field must be registered with:

- Entity type and stable identity key
- Logical field name and display label
- Owning component and record family
- Verified offset or structural path, encoding, endianness and scale
- Minimum, maximum, allowed values and cross-field constraints
- Source fixture(s), observed values and confidence/status
- Read adapter and write adapter
- Regression test and in-game validation evidence

Only fields marked VERIFIED may become editable. UNKNOWN and EXPERIMENTAL fields remain read-only and must be labelled honestly.

## Implementation sequence

### Gate A — data discovery
- Build complete inventory of all save components and record families.
- Identify stable IDs and links for players, staff, clubs, competitions, nations and stadiums.
- Classify player/staff/person records without assuming every person record is a player.
- Map field owners using controlled save pairs where exactly one known value changes.

### Gate B — read-only parity
- Global entity browser, search and filters.
- Typed entity detail pages and side-by-side compare.
- CSV export for fields whose semantics are verified.
- Entity counts and unresolved-link diagnostics.

### Gate C — safe editing foundation
- Field registry and typed codecs.
- Change-set staging, preview, validation and edit journal.
- Unit tests for each codec and property-preservation tests.
- Component-level write path behind a disabled feature flag until validated.

### Gate D — first writable vertical slice
- Choose one low-risk, well-understood field.
- Implement read -> edit -> diff -> write -> rebuild -> reopen -> in-game verification.
- Do not unlock the next field family until the full round-trip passes.

### Gate E — parity expansion
- Add players, staff, contracts, clubs, finances, competitions, prize money, reputation, nations and stadiums in independently tested groups.
- Add mass edit and presets only after single-record edits are stable.
- Add pre-game database mode only after its format and game compatibility are separately proven.

## Current truth

The current Android app is a read-only FMV inspector with component extraction/comparison and an experimental container rebuild path. Person name-to-entity mapping is not yet a verified player/staff schema. CA/PA, attributes, club finance, prize money and reputation are not yet mapped to writable fields. This blueprint defines the target; it is not evidence that those capabilities already work.

## Primary references

- FMRTE product overview: https://www.fmrte.com/
- FMRTE documentation: https://docs.fmrte.com/
- FMRTE player editing overview: https://docs.fmrte.com/players/overview/
- FMRTE club editing overview: https://docs.fmrte.com/clubs/overview/
- FMRTE 26 feature overview: https://www.fmrte.com/fmrte/26
- SEGA Pre-Game Editor installation and overview: https://support.sega.com/hc/en-gb/articles/52324155452945-How-To
