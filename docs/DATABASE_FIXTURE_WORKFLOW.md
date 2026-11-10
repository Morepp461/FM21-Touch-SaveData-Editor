# FM21 Touch 21.4.0 database fixture workflow

## Fixture handling

The extracted database contains proprietary Sports Interactive game data. Keep the original `.dat` files local and do not commit or publish their contents, including through Git LFS or workflow artifacts. The repository contains only tools and derived structural diagnostics.

Place the ten extracted files in a local folder, for example:

```text
research-fixtures/database-2140/
  basic_people_db.dat
  2140_fmc.xml
  lang_db.dat
  non_pl_hist_dt.dat
  non_pl_hist_id.dat
  non_pl_hist_index.dat
  people_db.dat
  pl_hist_dt.dat
  pl_hist_id.dat
  pl_hist_index.dat
```

## Generate an inventory

From the repository root:

```bash
python tools/inventory_database_2140.py research-fixtures/database-2140 --json database-2140-inventory.json
```

The tool is read-only. It reports file sizes, SHA-256 hashes, entropy, byte distribution, zero/printable ratios, 64-byte prefix/suffix hex, and the FMC metadata fields. It does not output database names or copy the source files.

The report is git-ignored by default. Review it locally, then share only the derived report if it contains no private career or user data.

## What this does and does not establish

The inventory helps verify that the expected fixture set is complete and compare binary structure across components. It does not identify table schemas, record boundaries, person types, CA, PA, attributes, club finances, or prize-money fields. Those require format-specific parsers and controlled experiments. No write support should be enabled from byte statistics alone.
