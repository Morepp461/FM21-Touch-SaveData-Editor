# Publication readiness checklist

This checklist is intentionally strict. A successful APK build is not equivalent to a complete save editor or a store-ready release.

## Android package

- [x] Minimum SDK is Android 8.0 / API 26.
- [x] Zstandard decoder plus Android zstd-jni compression runtime; packaged native libraries are verified in CI.
- [x] Version code/name: 7 / 0.7.0.
- [x] Debug APK build in CI.
- [x] Unsigned release APK build in CI.
- [x] Unit-test task in CI.
- [x] Android 8.0 / API 26 emulator launch smoke-test job.
- [ ] API 26 emulator job passes on the latest commit.
- [ ] Manual install and launch test on a physical Android 8 device.
- [ ] Manual install and launch test on a recent Android device.
- [ ] Verify large career saves on a low-memory device.
- [ ] Verify Storage Access Framework import/export on Android 8, 10, 13 and 16.

## Save format and data integrity

- [x] Read-only FMF/FMV parser for observed FM21 Touch 21.4.x save layout.
- [x] Manifest bounds and component metadata validation.
- [x] Zstandard component extraction and plaintext length validation.
- [x] Component comparison and export.
- [x] Initial experimental FMV container rebuilder implementation added (not yet accepted for production writes).
- [ ] Verify every untouched component remains byte-identical after Kotlin rebuild against real fixtures.
- [ ] Verify all frame prefixes, footer metadata and checksums/stamps after Kotlin rebuild against real fixtures.
- [ ] Open a rebuilt no-op save in FM21 Touch 2021.
- [ ] Open an edited test save in FM21 Touch 2021.

## Requested database editors

- [ ] Identify and validate player-only record classification.
- [ ] Verify stable player join keys across two saves.
- [ ] Map CA field, encoding, scale and allowed range.
- [ ] Map PA field, encoding, scale and allowed range.
- [ ] Map all requested player attributes and prove each field owner.
- [ ] Map club balance and transfer budget independently.
- [ ] Map competition prize money by competition and position.
- [ ] Map league reputation and its scale.
- [ ] Implement edits with validation, undo and explicit change review.
- [ ] Rebuild modified components and preserve unknown fields.
- [ ] Validate every editor against controlled known-value fixtures and in-game reload.

## Public release

- [x] Add initial adaptive launcher icon (final marketing screenshots/store art still pending).
- [x] Draft offline-first privacy policy.
- [ ] Choose final store screenshots and store description.
- [ ] Add privacy policy and support contact.
- [ ] Generate and securely back up a release signing key outside GitHub.
- [ ] Add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` as private GitHub Actions secrets.
- [ ] Sign release APK/AAB using private CI secrets or a secure local signing process.
- [ ] Verify APK signature, package metadata and release checksum.
- [ ] Complete Play Console target API, data safety and content declarations.
- [ ] Publish a closed testing build and resolve tester reports.
- [ ] Tag a versioned GitHub release with signed APK/AAB and changelog.

## Required controlled fixtures to finish reverse engineering

The project needs private save pairs from the same career where exactly one known in-game value changes at a time:
1. Player CA change, with player identity and old/new values recorded.
2. Player PA change, if the game exposes a reliable method.
3. One player attribute change.
4. Club balance change.
5. Transfer budget change.
6. Competition prize money change.
7. League reputation change.

Keep original files private and unchanged. Include hashes and a short note describing the exact in-game action and expected old/new value. A pre-match/post-match pair alone is not enough to attribute a changed byte range to CA, PA or finance.

## Release decision

Current state: **not ready for public publication**. The app is a read-only save inspector and component exporter. It does not yet have a validated FMV writer/rebuilder or verified implementations of the requested field editors. Do not market or distribute it as a working Full Database Editor until every required test above passes.
