# Publication readiness checklist

This checklist is intentionally strict. A successful APK build is not equivalent to a complete save editor or a store-ready release.

## Android package

- [x] Minimum SDK is Android 8.0 / API 26.
- [x] Zstandard decoder plus Android zstd-jni compression runtime; packaged native libraries are verified in CI.
- [x] Version code/name: 8 / 0.8.0-alpha.
- [x] Debug APK build in CI.
- [x] Unsigned release APK build in CI.
- [x] Unit-test task in CI.
- [x] Android 8.0 / API 26 emulator launch smoke-test job with an explicit Compose home-screen assertion.
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
- [x] Component export writes to a unique temporary file and replaces the destination only after successful extraction; regression test covers preservation of an existing destination on failure.
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

- PR run 172 for head `5485803a67f7b3c63b0ae7780d07ed68e873c98c` is GitHub Actions run `43645927595`. Both jobs failed in approximately one to two seconds.
- GitHub's job records for both the build and API 26 smoke jobs report `runner_id: 0`, an empty runner name, and an empty step list. The earlier run 168 and its failed-job rerun (attempt 2) returned the same result.
- GitHub's log download endpoint returned `BlobNotFound`; therefore there is no evidence that Gradle, Python tests, the Android emulator, or any workflow step actually started. This must be treated as a GitHub Actions execution/runner problem, not as a confirmed source-code or Gradle failure.
- Do not mark CI as passed, and do not infer a build failure cause from these records. Re-run once GitHub Actions provides a runner and inspect actual step logs before changing dependencies or build configuration.


## Android UI smoke coverage (15 November 2026)

- Added `MainActivitySmokeTest` under `app/src/androidTest`.
- The test launches the real activity and asserts that the app title, FMV picker action, and Android 8.0 minimum-support note are visible.
- This test is wired into the existing API 26 emulator job through `connectedDebugAndroidTest`. It is committed but not yet verified by CI because GitHub Actions jobs have been failing before runner allocation and before any workflow steps execute.


## Latest CI attempt (15 November 2026)

- Run 175 (43646167421) targets head `bd9d82136748c714892f8f8a277f72ea323172c9`, including the Android launcher UI smoke test.
- Both jobs completed as failures with no step summaries or log URLs exposed by the job API. Earlier runs 168, 172, and 173 show the same no-runner / zero-step pattern.
- The storage failure-path regression test was strengthened after run 175 and therefore is not covered by that run. A new CI result for the latest head is still required.


## Release artifact verification hardening (15 November 2026)

- CI now checks both debug and release APKs for minimum SDK 26, target SDK 36, and the packaged Zstandard native runtime. It also requires the release AAB to exist.
- When a private release keystore is configured, CI verifies the APK signature with apksigner and the AAB signature with jarsigner.
- These checks are committed but have not run yet. Run 176 and its rerun both failed before runner allocation; no Gradle task or validation step is known to have executed.


- Follow-up run 179 (43646291072), which includes the artifact-verification workflow changes, also failed both jobs in 1–2 seconds. The GitHub job records show `runner_id: 0`, empty runner names, zero steps, and no log URLs. This confirms the workflow has still not reached Gradle or the emulator; it does not validate or invalidate the new workflow commands.


## Parser ambiguity guard (15 November 2026)

- The Kotlin FMV parser now rejects empty or duplicate component names during manifest validation instead of allowing ambiguous name-based lookups to continue into export/rebuild paths.
- Added a synthetic duplicate-name manifest regression test.
- CI run 181 (43646485787) for this change failed both jobs before runner allocation. GitHub reports `runner_id: 0`, empty runner names, zero steps, and no job logs. The new parser guard and regression test are therefore committed but not yet executed by CI.


- Run 183 (43646522974) for the duplicate-name parser guard also failed before runner allocation. Both job records report `runner_id: 0`, empty runner names, zero executed steps, and no log URLs. No Kotlin unit test, Android package build, or emulator launch has been executed for the latest branch head.

## Memory-mapped diagnostics update (15 November 2026)

- Person indexing now uses a read-only memory map through `PersonRecordMapper.mapFile`; the People tab no longer allocates another full `game_db/.dat` byte array.
- Cross-save person comparison maps both extracted `game_db/.dat` files and joins unique UID groups only when their entity IDs match.
- Person diff is restricted to the observed FM21 Touch `21.4.0-0` database version. Unknown/different versions disable the action.
- Duplicate UID groups and UID-to-entity-ID mismatches are counted separately rather than being silently collapsed.
- The Compare tab exports all changed mapped person rows to CSV. Changed offsets remain opaque evidence, not semantic field labels.
- Added tests for memory-mapped file loading/comparison, empty files, offset shifts, duplicate UIDs, and entity-ID mismatches.
- Latest branch head `f1fc0f6ff45bc1663f87951c7c6311e365ee9f67` is committed but still requires an actual passing Gradle/unit-test/API 26 run.
- The latest observed Actions run for the preceding head still failed before runner allocation (`runner_id: 0`, no runner name, no steps). The current branch must not be marked build-verified or publication-ready.
- The release blockers listed above remain: semantic field mapping, Kotlin writer validation against a real save, successful in-game load, Android device QA, and a valid signed release artifact.

- Runner labels have been restored to `ubuntu-latest`. Repeated failures across default and pinned Ubuntu labels all occurred before runner allocation, so changing the image label was not a source-level fix and was reverted.


## UI/export regression hardening — 15 November 2026
- Fixed duplicate UID keys in the People lazy list by assigning per-row keys.
- Moved export copying and FMV SHA-256 revalidation off the main thread to avoid UI stalls on larger files.
- These changes are not considered verified until CI can allocate runners and execute Gradle/unit tests plus API 26 smoke testing.


## Export dialog lifecycle regression hardening
- Export actions now remain locked while the Android document picker is open and until the user saves or cancels.
- This prevents concurrent export requests from replacing the pending temporary-file reference.
- Runtime behavior still requires Android instrumentation/manual validation.


## Additional person-index ambiguity guard
- Duplicate entity IDs shared by different UIDs are excluded from next-record boundary inference.
- Regression coverage ensures those rows retain unknown record lengths rather than receiving an arbitrary boundary from map overwrite order.
- Semantic field editing remains disabled; this is a mapping safety improvement only.


## CSV export safety hardening
- CSV cells beginning with spreadsheet formula prefixes are neutralized, including formula prefixes after whitespace or a BOM.
- CSV quoting remains covered for commas and embedded quotation marks. Regression tests are committed but await an executable CI run.


## Latest hardening pass — 15 November 2026

- Export picker lifecycle: UI remains busy until Android's document picker returns a destination or cancellation, preventing concurrent exports from replacing the pending temporary file.
- Person mapper: entity-record length inference now ignores entity IDs claimed by multiple UIDs. Such record lengths remain unknown.
- CSV safety: exported cells beginning with spreadsheet formula prefixes are neutralized, including prefixes preceded by whitespace or a BOM. Unit tests cover quoting and formula-like values.
- A local Kotlin smoke check passed for the CSV escaping helper only. This is not a Gradle build or Android test.
- Latest PR head: `1de6f4854256ae498e176ed4bbc39a27e6432194`.
- GitHub Actions run 251 (`43674363556`) failed both jobs without step records or downloadable job logs. No Gradle, unit-test, or API 26 emulator execution is confirmed.
- No publication gate is cleared by these source changes. The app remains read-only and unsigned; real-save Kotlin rebuild validation, successful in-game load, verified semantic field mappings, Android device QA, and signed release artifacts remain required.

- Follow-up mapper guard: both the current entity ID and the next contiguous entity ID must be unique before a record length is inferred. Duplicate current IDs cannot borrow the next row's offset. Regression test: `doesNotInferRecordLengthsWhenEntityIdsAreDuplicatedAcrossUids`.
- Latest source commit for this guard: `fa1ffa34ce9e70869dea1d163aa2a5435b215d51`. CI status for this exact head is pending/no workflow run returned at the time of this update.


## Follow-up regression-test correction — 15 November 2026

- Corrected the CSV formula-neutralization regression assertions to compare actual tab and BOM characters rather than literal backslash sequences.
- A local Kotlin smoke test passed for tab-prefixed formulas, BOM-prefixed formulas, whitespace-prefixed formulas, and ordinary text. This is a focused helper check only, not a repository Gradle build.
- Commit: `71ba9430ee199a6e22c32814c2312f78a9103329`.
- GitHub Actions run 255 (`43674537532`) again failed both jobs before runner allocation. The job API returned no runner IDs, steps, or log URLs; Gradle, unit tests, and the API 26 emulator did not execute.
- The app remains read-only and is not ready for public publication. All save-format, semantic mapping, real-device, in-game reload, and signed-release gates remain open.


## Streaming export verification — 15 November 2026

- FMV export verification now computes SHA-256 for the prepared payload and the destination URI using fixed-size 64 KiB buffers. It no longer allocates a second full-save byte array just to compare hashes.
- The source FMV has already passed full component decompression/validation before the export picker is opened; the streaming destination hash confirms the saved copy is byte-identical to that validated payload.
- Added unit tests for a known SHA-256 vector, short-read input streams, and input-stream closure.
- README wording updated to describe streaming hash verification accurately.
- Source commit: `29d041e9f221e7cc176cf037a6f939417db710cc`; regression test commit: `80639728f3d101ec9b2d085916642f6748d95439`; documentation commit: `9be5d57b13a7712325b8e8a207b7465fe9a4453b`.
- These changes still require a passing Gradle/unit-test run and Android API 26 QA. No release gate is cleared by source changes alone.


## Latest streaming-export CI observation — 15 November 2026

- Head `3f1ea8c25c9a3115325dafa925c5287ec8f81ac6` triggered Actions run 261 (`43674713677`).
- Both `build` and `android-api26-smoke` failed. The job API again returned no step summaries or log URLs, so the workflow's source/build/test steps are not confirmed to have started.
- A local Kotlin smoke harness passed the streaming SHA-256 known-vector, short-read, and stream-close checks. This validates the helper behavior in isolation only; it does not replace the repository Gradle unit tests.
- The Android build, actual unit tests, API 26 emulator launch, real-save Kotlin rebuild, in-game load, semantic editor mappings, physical-device QA, and signed release artifact remain unverified.

## Export picker failure handling — 15 November 2026
- All export actions now share one document-picker launch path.
- If Android cannot launch the save-location picker, the pending payload reference is cleared, its temporary file is deleted, the busy state is released, and an error status is shown.
- This prevents a failed picker launch from leaving the UI locked or leaking the prepared export file. Runtime verification still requires Android instrumentation/device testing.

## Pengurangan alokasi saat impor save — 15 November 2026
- Fungsi pembaca save sekarang menyalin stream ke file sementara di cache aplikasi menggunakan buffer 64 KiB dan menolak input di atas 128 MiB sebelum menulis blok yang melewati batas.
- Setelah stream ditutup, file sementara dibaca menjadi satu ByteArray untuk parser yang sekarang, lalu dihapus melalui finally. Ini menghilangkan pertumbuhan buffer ByteArrayOutputStream dan salinan penuh tambahan saat toByteArray(), tetapi belum menghilangkan kebutuhan satu ByteArray penuh.
- Tes unit memeriksa batas tepat, input yang melampaui batas, penutupan stream, dan penghapusan file sementara pada semua jalur tersebut.
- Smoke test Kotlin 1.9 untuk helper pembaca berbatas dan cleanup lulus. Ini hanya uji helper terisolasi, bukan build Android atau Gradle test.
- Head 734a48983fec4c3d32f00956b36be3193d745a2c memicu Actions run 271 (43675142135); kedua job berakhir gagal. Job API tidak mengembalikan langkah atau log (steps kosong, logs_url null), sehingga build/test Gradle dan emulator belum terbukti berjalan.
- Tetap belum siap publikasi: parser masih menyimpan seluruh save di memori, field semantik belum dipetakan, Kotlin rebuild belum divalidasi pada fixture nyata, in-game reload dan QA perangkat fisik belum dilakukan, serta APK/AAB release bertanda tangan belum tersedia.

- Menambahkan kasus uji kegagalan I/O di tengah stream dan menampilkan batas 128 MiB pada layar awal aplikasi. Smoke test Kotlin 1.9 untuk helper pembaca termasuk cleanup setelah kegagalan I/O lulus; validasi Gradle dan Android tetap tertunda.

## CI setelah pembacaan save berbasis file sementara — 15 November 2026
- Run 282 (`43675541726`) pada head `123f06f5e538e754ecfea3af6e73f9c7f7c76438` gagal pada job `build` dan `android-api26-smoke`.
- Kedua job tidak menyediakan langkah eksekusi maupun URL log (`steps: []`, `logs_url: null`). Tidak ada dasar untuk menyimpulkan Gradle atau emulator berjalan; penyebab tetap masalah alokasi/eksekusi runner yang belum terdiagnosis.
- Pembaca save berbatas sudah diuji dengan smoke test Kotlin terisolasi, termasuk cleanup setelah kegagalan I/O. Seluruh build, unit test proyek, dan QA perangkat tetap terbuka.

## Lifecycle ekspor dan Activity recreation — 15 November 2026
- Path payload ekspor disimpan menggunakan Compose rememberSaveable, bukan state objek File yang hilang ketika Activity dibuat ulang.
- Callback memvalidasi bahwa file sementara masih tersedia sebelum menyalin ke URI tujuan. File yang hilang menghasilkan status error eksplisit.
- Perubahan ini belum diverifikasi lewat Android instrumentation/device test karena job emulator CI masih gagal sebelum menyediakan langkah atau log.

## Status validasi terbaru — 15 November 2026
- Path payload ekspor kini dipulihkan melalui rememberSaveable dan callback memeriksa file sementara sebelum memulai penyalinan. Perubahan masih memerlukan instrumentation test di Android.
- Actions run 288 (43675661930) untuk head a6897490a1414194dcec6b0a0ecc136e2ccb9c17 gagal pada kedua job tanpa step summary atau URL log. Build, unit test, dan emulator belum terbukti berjalan.
- Smoke test lokal helper Kotlin 1.9 lulus untuk pembacaan berbatas, cleanup setelah kegagalan I/O, dan penutupan stream. Ini bukan bukti kompilasi seluruh aplikasi.

## Perapian cakupan instrumentation Android — 15 November 2026
- Pengujian UI layar awal digabungkan ke MainActivityLaunchTest, termasuk pemeriksaan batas impor 128 MiB dan minimum API 26; tes layar awal duplikat dihapus.
- Tes native Zstandard tetap dijalankan di emulator melalui test class yang sama. Belum ada hasil emulator terbaru yang valid karena GitHub Actions belum mengeksekusi langkah job.

## Repository dan signing-secret hygiene — 15 November 2026
- `.gitignore` diperluas untuk output build Android/Gradle, konfigurasi SDK lokal, IDE, keystore dan file environment.
- Perubahan ini mengurangi risiko commit tidak sengaja, tetapi bukan pengganti audit riwayat Git. Belum ada keystore rilis atau signed APK/AAB; kredensial signing harus tetap disimpan secara privat oleh pemilik.

## Checksum dan provenance release artifact — 15 November 2026
- CI menyiapkan SHA256SUMS.txt untuk debug APK, release APK, dan release AAB setelah pemeriksaan metadata paket.
- CI juga menyiapkan BUILD-METADATA.txt dengan commit SHA dan versi aplikasi serta SDK minimum/target. File ini disertakan pada artifact upload.
- Perubahan workflow masih belum tervalidasi karena Actions run terbaru gagal sebelum job steps tersedia. Tidak ada APK/AAB yang dihasilkan atau diverifikasi oleh perubahan ini.

## Penanganan kegagalan Android document picker — 15 November 2026
- Peluncuran pemilih save utama/pembanding kini menangani exception secara eksplisit; kegagalan tidak dibiarkan menjadi crash tanpa status UI.
- Jalur ekspor CreateDocument juga memiliki cleanup payload dan status error. Seluruh perilaku masih menunggu uji instrumentasi pada emulator/perangkat nyata.

## CI terakhir setelah hardening pemilih file — 15 November 2026
- Run 311 (`43676030826`) pada head `ce2ff55bdc1ef564e109106418b66d57000284e5` gagal pada job build dan android-api26-smoke.
- Job API tidak menyediakan langkah atau log. Perubahan picker, workflow checksum, dan tes terbaru belum diverifikasi oleh CI. Jangan menganggap kegagalan ini sebagai kegagalan kompilasi karena tidak ada langkah build yang tercatat.

- Perintah checksum dan ekstraksi versi pada workflow diuji dengan file dummy; validasi non-empty ditambahkan untuk version name/code. Uji shell ini tidak menjalankan Gradle atau memverifikasi APK/AAB nyata.

- Dependency `androidx.compose.runtime:runtime-saveable` ditambahkan secara eksplisit untuk state ekspor yang disimpan. Perlu validasi kompilasi dan Activity recreation pada emulator API 26.

## CI terakhir — 15 November 2026
- Actions run 322 (`43676170041`) untuk head `aa43f3fc5c1e663e4a1b6949324ea0d9c93ac2f5` gagal pada job build dan API 26 smoke.
- Kedua job tidak memiliki step summary atau URL log. Build/test Android, tes instrumentation, serta artefak APK/AAB tetap belum terverifikasi.

## Validasi konsistensi header dan footer — 15 November 2026
- Parser memeriksa pointer footer 32-bit pada header terhadap lokasi marker manifest aktual, mengurangi kemungkinan menerima container dengan metadata footer yang saling bertentangan.
- Menambahkan regresi untuk pointer footer yang tidak cocok dan memperbarui builder fixture sintetis. Dua file FMV nyata yang diberikan sebelumnya cocok dengan aturan ini.
- Perubahan belum tervalidasi oleh Gradle karena job CI masih gagal sebelum runner/steps tersedia.

- Validasi footer diperketat: pointer header menjadi sumber lokasi footer, lalu marker diverifikasi tepat di posisi itu. Tes mencakup pointer di luar area file dan pointer yang menunjuk ke byte non-footer.
- Perubahan ini memperkuat parser tetapi belum lulus Gradle test karena runner CI masih gagal sebelum langkah berjalan.

- Empat kasus SaveReadLimitTest berhasil dijalankan lokal dengan Kotlin 1.9 dan assertion shim (bukan JUnit/Gradle). Build aplikasi, seluruh unit test, dan emulator API 26 masih belum terverifikasi.

## Parser footer dan CI terbaru — 15 November 2026
- Parser menggunakan pointer footer dari header, lalu memverifikasi marker FMF di offset yang ditunjuk. Tes mencakup pointer di luar file dan pointer yang menunjuk ke data non-footer.
- Actions run 336 (`43676489758`) gagal pada kedua job tanpa steps/logs. Perubahan parser dan tes belum dijalankan oleh Gradle CI.
