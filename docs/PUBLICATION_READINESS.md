# Publication readiness checklist

This checklist is intentionally strict. A successful APK build is not equivalent to a complete save editor or a store-ready release.

## Android package

- [x] Minimum SDK is Android 8.0 / API 26.
- [x] Zstandard decoder plus Android zstd-jni compression runtime; packaged native libraries are verified in CI.
- [x] Version code/name: 8 / 0.8.0-alpha.
- [x] Debug APK build in CI.
- [x] Unsigned release APK build in CI.
- [x] Unit-test task in CI.
- [x] Android 8.0 / API 26 emulator launch smoke-test job.
- [ ] API 26 emulator job passes on the latest commit.
- [ ] Manual install and launch test on a physical Android 8 device.
- [ ] Manual install and launch test on a recent Android device.
- [ ] Verify large career saves on a low-memory device.
- [ ] Verify Storage Access Framework import/export on Android 8, 10, 13 and 16.

## Latest CI candidate (10 November 2026)

- Commit `71f78082297df18f49133bda93fa370acac07c21` on `main`.
- Android build, Python read-only diagnostics, Kotlin unit tests, package compatibility checks, and artifact uploads completed successfully in GitHub Actions run `42844299542`.
- CI artifacts: debug APK ZIP and unsigned release APK/AAB ZIP. The release outputs are unsigned because no private release keystore is configured.
- The Android API 26 emulator launch smoke test is still running; do not mark it passed until its job reports success.
- Temporary extracted person-database files are now deleted after indexing, and temporary exported cache files are deleted after the document-picker callback.

## Save format and data integrity

- [x] Read-only FMF/FMV parser for observed FM21 Touch 21.4.x save layout.
- [x] Manifest bounds and component metadata validation.
- [x] Zstandard component extraction and plaintext length validation.
- [x] Component comparison and export.
- [x] Initial experimental FMV container rebuilder implementation added (not yet accepted for production writes).
- [x] Add byte-identical unchanged-save copy path and synthetic regression test.
- [ ] Verify every untouched component remains byte-identical after an edited Kotlin rebuild against real fixtures.
- [ ] Verify all frame prefixes, footer metadata and checksums/stamps after Kotlin rebuild against real fixtures.
- [x] Validate all components and export a byte-identical no-op FMV copy with post-export SHA-256 verification.
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


## Revalidation update (15 November 2026)

- Latest documented fixture-validation commit on `main`: `ad1401a11c3ac517e5d0c8fad50e14b376149fa7`.
- The real FMV fixture was re-extracted successfully: 852/852 members and 1,610 Zstandard frames; the independent Python structural round-trip reopened all members with identical plaintext.
- This is not Kotlin writer validation and does not prove that FM21 Touch can load a rebuilt save. The no-op in-game load, controlled semantic field fixtures, physical Android QA, and signing key remain release blockers.
- A controlled fixture capture protocol is maintained at `docs/CONTROLLED_FIXTURE_PROTOCOL.md` on the development branch.
- The latest PR validation run for head `045fe30cb89a2d9dce53ff8fd8a90936d71f8a64` (GitHub Actions run `43644407137`, run 163) concluded **failure** for both the Android build/test job and API 26 emulator job. The connector could not retrieve the job logs or step summaries, so the failure cause is unknown and must not be guessed.
- The synthetic Kotlin regression test was corrected to use an edited payload of 8 MiB + 4 KiB and assert exactly two output Zstandard frames. This now exercises the writer's multi-frame branch, but remains a synthetic test until CI can report a passing run.

## CI execution diagnosis (15 November 2026)

- The latest PR run for head `5276db83997c66b0b8b42a5bcf888bdea4dcb911` is GitHub Actions run `43645357405` (run 168). Both jobs failed in approximately two seconds.
- GitHub's job records for both the build and API 26 smoke jobs report `runner_id: 0`, an empty runner name, and an empty step list. The failed-job rerun (attempt 2) returned the same result.
- GitHub's log download endpoint returned `BlobNotFound`; therefore there is no evidence that Gradle, Python tests, the Android emulator, or any workflow step actually started. This must be treated as a GitHub Actions execution/runner problem, not as a confirmed source-code or Gradle failure.
- Do not mark CI as passed, and do not infer a build failure cause from these records. Re-run once GitHub Actions provides a runner and inspect actual step logs before changing dependencies or build configuration.
