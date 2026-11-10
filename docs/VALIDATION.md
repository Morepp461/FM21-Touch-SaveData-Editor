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
- Rebuilder: not implemented.
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
