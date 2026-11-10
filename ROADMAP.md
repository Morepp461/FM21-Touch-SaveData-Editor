# FM21 Touch SaveData Editor — implementation roadmap

Target: Android / Football Manager Touch 2021 (.fmv), database version 21.4.0-0.

## Status policy
A stage is only marked Complete after tests against real FM21 Touch saves, including an unchanged round-trip and an edited-save load test in the game. A UI screen or placeholder is not completion.

| Stage | Status | Acceptance criteria |
|---|---|---|
| FMU Container Parser | Implemented in code; CI/device validation pending | Validate header/footer, decode manifest and enumerate all 852 members |
| Component Extractor | Implemented in code; CI/device validation pending | Stream Zstandard members to a new file and verify frame prefixes, plaintext lengths and boundaries |
| Player Record Mapping | Research not complete | Resolve stable person/entity IDs and verify records against in-game data |
| CA / PA Editor | Not implemented | Edit correct player records and persist values through a verified rebuild |
| Player Attributes Editor | Not implemented | Map all target attributes, scales and ownership; preserve unrelated bytes |
| Club Finance Editor | Not implemented | Verify club balance and transfer-budget field semantics |
| Prize Money Editor | Not implemented | Verify competition, position and prize amount mapping |
| League Reputation Editor | Not implemented | Verify league IDs and reputation scale |
| FMU Rebuilder | Not implemented | Rebuild valid container, preserve unknown sections and pass round-trip and game-load tests |

## Verified container facts
- Outer header starts with bytes `02 01 fmf.`; first data region starts at byte 30.
- Database version string: `21.4.0-0`.
- Footer contains a nested FMF/Zstandard manifest.
- Manifest contains 68 top-level members and 784 nested members.
- Member offsets are relative to byte 30.
- Members may contain multiple concatenated Zstandard frames.
- Four-byte prefixes before subsequent frames match the compressed size of the following frame in both supplied saves.
- The final member's stored boundary includes the first four bytes of the footer marker.
- All 852 members were decompressed and length-checked in both supplied saves using the read-only Python validator.

## Safety requirements
- Never overwrite the source save; export to a new file.
- Keep byte-for-byte copies of unmodified components.
- Reject unknown container versions and invalid offsets or lengths.
- Do not write guessed offsets, field scales or compression settings.
- Re-open and verify every exported file before calling it successful.

## Required private test fixtures
1. A pre-match save.
2. A post-match save from the same career.
3. A save with a known player edit, if the game exposes a reliable test path.
4. A save with a known club-finance change.

Keep private save files out of public commits. Record file hashes and in-game observations for each fixture.
