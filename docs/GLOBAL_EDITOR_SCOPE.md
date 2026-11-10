# Global Editor Scope and Entity Coverage

## Product boundary

FM21 Touch Save Editor is a save-wide editor, not a manager-club-only editor. The target is every editable entity serialized anywhere in a supported FM21 Touch save, including entities outside the human manager's club and entities not currently visible in the active squad.

The UI must not silently filter the database to Bolton, the selected club, the active league, or the currently loaded squad. Club context may be offered as a filter, never as an access boundary.

## Entity coverage target

These are product requirements and discovery targets. A category is not considered implemented until its records, field semantics, validation rules, write path, and in-game round-trip are verified.

### People
- Players at every club, including unattached/free-agent players and players outside the active league where present in the save.
- Staff at every club, including managers, assistant managers, directors, coaches, scouts, medical staff, and other staff roles.
- People not currently employed or registered to a club, where represented in the save.
- Shared identity fields: full name, date of birth, nationality/citizenship, place of birth, second nationality, languages, height, weight, preferred foot, and other verified person fields.
- Player-specific fields: current ability (CA), potential ability (PA), position familiarity, playing attributes, personality/mental fields, reputation, and other verified player fields.
- Staff-specific fields: staff attributes, coaching qualifications, preferred formations/tactics, responsibilities, reputation, and other verified staff fields.
- Contract, wage, transfer, registration, injury, suspension, and status fields only where their storage and constraints are understood.

### Clubs and organizations
- Every club in the save, not just the manager's club.
- Club identity, name, short name, nickname, colors, reputation, finances, facilities, stadium, kits, affiliations, and other verified fields.
- Club ownership, board/chairperson, staff and squad relationships.
- National teams, reserve/youth teams, and other organization types where present.

### Competitions and world data
- Leagues, cups, international competitions, divisions, rules, schedules, and standings where editable data is represented.
- Nation/country records, continental/federation records, cities, regions, and stadiums.
- Awards, records, histories, and other serialized world data where safe editing can be verified.

### Financial and progression data
- Club balances, debts, budgets, income, expenses, sponsorships, prize money, transfer budgets, wage budgets, and financial status.
- League and competition prize money and reputation.
- Club, league, nation, and competition reputations.
- Manager profile and career-related values.
- Any additional save-level settings or progression data discovered during schema research.

## Screenshot-grounded research observations

The supplied Bolton screenshots show:
- Squad list pages with player names and positions, including Matt Gilks, Lukas Jensen, Billy Crellin, Shay Facey, Jak Hickman, Alex Baptiste, Liam Edwards, Ricardo Almeida Santos, Harry Brockbank, Ryan Delaney, Gethin Jones, M J Williams, Declan John, Brandon Comley, Andrew Tutte, Kieran Lee, George Thomason, Antoni Sarcevic, Ben Jackson, Lloyd Isgrove, Ronan Darcy, Ali Crawford, Marcus Maddison, Zack Elboune, Dennis Politic, Finlay Hurford-Lockett, Arthur Gnahoua, Daniel Delfouneso, Eoin Doyle, and Oladapo Afolayan.
- Staff list entries include Sharon Brittan (Chairman), Andrew Caton (Director of Football), Coach Morepp (Manager), Peter Atherton (Assistant Manager), Mark Litherland (Head of Youth Development), Matt Barrass (Head Physio), and Gary Clayton (Chief Scout).
- Matt Gilks' player profile shows Current Ability 81 and Recommended Current Ability 80.
- His visible attributes include goalkeeper, mental, physical, and technical categories. The screenshot is useful as a ground-truth UI reference, but it does not reveal binary offsets.

## CA/PA discovery status

CA 81 is a confirmed displayed value for Matt Gilks in this particular save snapshot. It is not yet mapped to a byte offset or encoding in the save. Recommended Current Ability 80 is a separate displayed metric and must not be conflated with CA.

No CA, PA, or attribute offset should be written or exposed as editable until it is supported by controlled save comparisons and a successful in-game round-trip. The next research step is to map Matt Gilks' stable person UID/entity ID to the exact person record(s), then use controlled before/after saves where only one known in-game value is changed.

## Identity and data-integrity rules

- Use stable UIDs/entity IDs for identity. Never treat raw byte offsets as stable across saves.
- Keep player and staff classification separate from person identity. A person record may exist independently of current club membership or role.
- Preserve unknown fields and unknown components byte-for-byte whenever possible.
- Do not infer field meaning from a component name, a single byte difference, or proximity to a known value.
- Keep unresolved and ambiguous records visible in diagnostics rather than guessing.
- All edits must be range-checked and validated against the correct entity type and field constraints.
- A write must preserve the FMV container structure and all untouched data.
- Maintain a backup/rollback path and a clear warning when an edit has not been verified in-game.

## Delivery stages

1. **Entity discovery:** identify every entity family and its stable identity keys.
2. **People mapping:** resolve person records and distinguish player, staff, free-agent, and other person states.
3. **Field mapping:** prove CA, PA, attributes, date of birth, nationality, and other fields using controlled comparisons.
4. **Global entity mapping:** clubs, competitions, nations, finances, prize money, reputation, and related records.
5. **Read-only browser:** search and filter all mapped entities without club-only restrictions.
6. **Safe editing:** validated edits with preview, diff, backup, and rollback.
7. **Container rebuild:** preserve all unknown and untouched data while rebuilding a valid FMV.
8. **Round-trip verification:** open the modified save in FM21 Touch and confirm the intended changes and unaffected data.

## Current implementation status

The app and current reverse-engineering tools remain diagnostic/read-only. This scope document records intended product coverage; it does not claim that all entity types or editable fields have been mapped or implemented.
