# FM21 Touch database metadata reference

## Selected database

The project uses **`2140_fmc.xml`** as its sole database metadata reference.

| Field | Value |
|---|---|
| Database version | `21.4.0` |
| Required game version | `21.3.1` |
| FMC | `true` |
| FMM | `false` |
| Author | Sports Interactive |

## Parser behavior

`FmcDatabaseMetadataParser` parses the XML metadata with external entity and DTD processing disabled. The target predicate is true only when the database version is exactly `21.4.0`, FMC is enabled, and FMM is disabled.

The parser is metadata validation only. It does **not** parse `.dat` tables, classify player/staff records, identify CA/PA or attributes, or authorize save writes. Those binary structures remain subject to independent reverse-engineering and controlled validation.

## Scope decision

Only the selected 21.4.0 metadata file is used as the project's current database reference. Other metadata variants are not part of the active fixture set. This does not imply that other game/database versions are supported.
