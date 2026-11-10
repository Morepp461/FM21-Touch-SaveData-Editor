# FM21 Touch SaveData Editor — implementation roadmap
Target: Android / Football Manager Touch 2021 (.fmv)

## Status policy
A stage is only marked Complete after tests against real FM21 Touch saves, including an unchanged round-trip and an edited-save load test in the game. A UI screen or placeholder is not completion.

| Stage | Status | Acceptance criteria |
|---|---|---|
| FMU Container Parser | Implemented, device validation pending | Validate FMF header/footer, decode manifest and enumerate 852 members on Android |
| Component Extractor | Core implemented, UI pending | Decompress member frames and verify declared plaintext length and boundaries |
| Player Record Mapping | Not started | Resolve stable player IDs and prove fields against in-game records |
| CA / PA Editor | Model/validation scaffold | Edit correct records and persist values through rebuild |
| Player Attributes Editor | Model/validation scaffold | Map attributes correctly and preserve unrelated bytes |
| Club Finance Editor | Pending schema | Verify balance and transfer-budget field semantics |
| Prize Money Editor | Pending schema | Verify competition and position mapping |
| League Reputation Editor | Pending schema | Verify reputation scale and league IDs |
| FMU Rebuilder | Safety interface scaffold | Rebuild valid container, preserve unknown sections, pass round-trip tests |

## Verified container facts from the supplied Bolton save pair\n- Outer header starts with bytes 02 01 followed by fmf.; first data region starts at byte 30.\n- Database version string: 21.4.0-0.\n- Footer contains a nested FMF/Zstandard manifest.\n- Manifest contains 68 top-level members and 784 nested members.\n- Member offsets are relative to byte 30; stored lengths include a four-byte frame-length prefix at the member boundary.\n- Members may contain multiple concatenated Zstandard frames.\n- Player/club/competition field layouts and safe rebuild rules remain unverified.\n\n## Safety requirements
- Never overwrite the source save; export to a new file.
- Keep byte-for-byte copies of unmodified components.
- Reject unknown container versions and invalid offsets or lengths.
- Do not write guessed offsets or guessed compression settings.
- Re-open and verify every exported file before calling it successful.

## Required private test fixtures
1. A pre-match save.
2. A post-match save from the same career.
3. A save with a known player edit.
4. A save with a known club-finance edit.

Keep private save files out of public commits. Record file hashes and in-game observations for each fixture.
