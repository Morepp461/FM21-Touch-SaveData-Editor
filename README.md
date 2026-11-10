# FM21 Touch SaveData Editor

Android save inspector and in-progress database editor for Football Manager Touch 2021 saves (`*.fmv`).

## Current build

- App version: **0.6.0**
- Minimum Android: **8.0 (API 26)**
- Target SDK: **35**
- Runtime: Kotlin + Jetpack Compose
- Zstandard: pure-Java Android decoder (no zstd-jni runtime dependency)

## Implemented

- Android Storage Access Framework file selection and component export.
- FMF/FMV header and footer-manifest validation for the observed FM21 Touch 21.4.0-0 save format.
- Manifest listing of top-level and nested components.
- Multi-frame Zstandard component extraction with length and boundary checks.
- Searchable component browser with category filters and incremental loading.
- Two-save component comparison with SHA-256 hashes, changed-byte counts and first-difference offsets.
- Read-only person name-index to entity-record UID mapping.
- Dark motorsport-inspired UI with Overview, People, Compare and Files tabs.
- Android CI builds debug and unsigned release APKs, runs unit tests, and launches an API 26 emulator smoke test.

## Not implemented yet

The app is still **read-only**. The following are not ready for editing:

- Player-only classification and verified player profile mapping.
- Current Ability (CA) and Potential Ability (PA).
- Visible and hidden player attributes.
- Club balance and transfer budget.
- Competition prize money.
- League reputation.
- FMV container rebuild and save-writing pipeline.

The person index is not the same as a player list. It can include staff and other person records. Entity IDs, raw record tags and candidate record spans are diagnostic observations only; they are not permission to edit bytes.

## UI navigation

- **Overview**: save identity, database version, component counts and available tools.
- **People**: build and search the person index. Results remain explicitly unclassified until player-vs-staff semantics are proven.
- **Compare**: compare a second save, preferably from the same career.
- **Files**: filter components by category, search archive paths and export individual components.

## Build locally

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle :app:assembleDebug :app:testDebugUnitTest
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Safety and validation

- The original save is never overwritten.
- No editor writes guessed offsets or unverified values.
- The write gate rejects containers that have not passed write-validation.
- Keep private save fixtures out of public commits.
- A stage is complete only after synthetic tests, real-save validation, unchanged round-trip, controlled edit round-trip and an in-game load test pass.

See `ROADMAP.md`, `docs/PLAYER_RECORD_MAPPING.md`, `docs/VALIDATION.md` and `docs/REVERSE_ENGINEERING_STATUS.md`.

This is an independent community project and is not affiliated with Sports Interactive or SEGA.
