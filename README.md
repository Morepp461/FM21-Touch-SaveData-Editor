# FM21 Touch SaveData Editor

Android project for inspecting Football Manager Touch 2021 save files (.fmv).

## Current build state
Alpha 0.3 development scaffold. The app includes:
- Android Storage Access Framework file picker.
- FMF/FMV outer-header and footer-manifest validation.
- Zstandard footer decoding and manifest parsing.
- Listing of top-level and nested save components.
- Component extraction core with length and boundary checks.
- Typed domain models for the requested editor areas.

## Important limitation
The current app is read-only. Player record layouts, CA/PA and attribute offsets, club finance fields, prize-money records, league reputation fields, and a verified FMV rebuild procedure are not yet implemented. Do not edit or overwrite original save files. The app must not claim a save is editable until a rebuilt test save has been opened successfully in FM21 Touch 2021.

## Build
Open this repository in Android Studio with JDK 17 and Android SDK 35, then build the app module.

## Test data
Use private, user-owned save fixtures locally. Do not commit personal save files to this repository.
