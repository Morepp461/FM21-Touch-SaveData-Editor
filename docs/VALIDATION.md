# FM21 Touch SaveData Editor — validation record

## Test fixtures

The two supplied files were used as read-only fixtures. They are intentionally not committed to this repository.

| Fixture | File size | Save name | Top-level | Nested | Decompressed payload |
|---|---:|---|---:|---:|---:|
| Before match | 21,401,187 bytes | Coach Morepp - Bolton | 68 | 784 | 107,314,935 bytes |
| After match | 21,629,895 bytes | Coach Morepp - Bolton | 68 | 784 | 107,929,383 bytes |

## Container checks passed

- Outer FMF signature is `02 01 fmf.`.
- Database version string observed: `21.4.0-0`.
- Footer manifest is a Zstandard-compressed FMF structure.
- Manifest entries were parsed for both fixtures.
- All 852 members in each fixture were decompressed.
- Every extracted payload matched its declared plaintext length.
- Multi-frame members were found in both fixtures.
- The four-byte prefix before each subsequent Zstandard frame matched that frame's actual compressed length in both fixtures.
- Final member boundary overlaps the first four bytes of the footer marker. The reader accounts for this format detail.

## Android implementation status

- FMF/FMV manifest parser: implemented in Kotlin; Android CI/build and on-device validation pending.
- Component extractor: streaming output-to-file implemented; Android CI/build and on-device validation pending.
- Component export: Android Storage Access Framework integration implemented; on-device validation pending.
- Synthetic parser tests: added for one-frame and multi-frame components, plus invalid header handling.
- FMV rebuilder: initial Kotlin implementation added. It preserves untouched compressed member streams, rewrites the manifest and frame-size prefixes, reopens the output, and verifies decompressed component lengths plus edited payload hashes.
- Synthetic Kotlin tests cover no-op byte preservation, edited component round-trip, nested members, multi-frame replacements, and rejection of unknown/empty replacements.
- An independent Python implementation of the same observed container layout rebuilt both supplied 21.4.0-0 fixtures; all 852 components in each rebuilt output re-opened and extracted with declared lengths. This is structural evidence only, not a test of the Kotlin writer or an in-game load test.
- Kotlin writer validation against the supplied original FMV fixtures and successful load of a rebuilt save in FM21 Touch remain pending.
- Semantic record parsers: not yet verified.

## What the two-save comparison tells us

The two saves are from the same career before and after a match, not a controlled edit experiment. The comparison found 164 changed members out of 852 (29 top-level and 135 nested). It is useful for locating dynamic areas, but cannot identify CA, PA, player attributes, club balance, transfer budget, prize money, or league reputation fields.

In particular, `finance_manager/.dat` is identical in these two snapshots. This only means that this match did not change that component.

## Release gate

No edited save may be exported until all of these pass:

1. Unchanged round-trip produces a structurally valid save.
2. All members can be extracted and rebuilt with correct offsets and manifest metadata.
3. Unknown components are preserved byte-for-byte where possible.
4. At least one known edit is applied to a copy.
5. The edited copy reopens in the target game and the changed value is confirmed in-game.
6. A second independent fixture confirms the field mapping.


## 0.5.0 CI and UI validation

- Android Gradle build: passed on GitHub Actions for commit `ab2bf12f`.
- Unit tests: passed in the same CI job.
- Debug APK artifact: generated successfully (9,576,146 bytes).
- APK SHA-256: `b59a0f4d04d8d385717574e64c89fac6aee1c1ddace04924ab5e0dfcb2aaf616`.
- Runtime APK contains AndroidX graphics native libraries; the Zstandard JNI runtime dependency is absent. Zstandard decoding uses the pure-Java implementation.
- App minimum SDK remains API 26 (Android 8.0).
- UI includes Overview, People, Compare and Files tabs, component category filters, search and incremental loading.

Additional synthetic mapper tests were added for ambiguous UID matches and malformed payload rejection. A save-write policy test verifies that unvalidated containers are rejected.

This CI result proves the source compiles and the automated unit tests pass. It does not prove installation on every Android 8 device, correct player classification, semantic field offsets, or a game-loadable rebuilt save.
