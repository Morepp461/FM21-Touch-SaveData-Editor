import tempfile
import unittest
from pathlib import Path

import inventory_database_2140 as inventory


class Database2140InventoryTest(unittest.TestCase):
    def test_entropy_of_uniform_byte_stream_is_zero(self):
        self.assertEqual(0.0, inventory.entropy(b"\\x00" * 128))

    def test_profile_reports_hash_and_size_without_content(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.dat"
            path.write_bytes(b"FM21\\x00\\x01" * 20)
            result = inventory.profile(path)
            self.assertEqual(path.name, result["file"])
            self.assertEqual(path.stat().st_size, result["size_bytes"])
            self.assertEqual(64, len(result["sha256"]))
            self.assertIn("prefix_hex_64", result)
            self.assertNotIn("strings", result)

    def test_metadata_detects_selected_2140_target(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "2140_fmc.xml"
            path.write_text(
                '<properties><string id="version" value="21.4.0"/>'
                '<string id="required_version" value="21.3.1"/>'
                '<boolean id="fmc" value="true"/><boolean id="fmm" value="false"/>'
                '<string id="author" value="Sports Interactive"/></properties>',
                encoding="utf-8",
            )
            metadata = inventory.parse_metadata(path)
            self.assertTrue(metadata["is_selected_2140_target"])
            self.assertEqual("21.3.1", metadata["required_version"])

    def test_metadata_rejects_other_database_version_as_target(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "2140_fmc.xml"
            path.write_text(
                '<properties><string id="version" value="21.3.0"/>'
                '<string id="required_version" value="21.2.3"/>'
                '<boolean id="fmc" value="true"/><boolean id="fmm" value="false"/>'
                '</properties>',
                encoding="utf-8",
            )
            self.assertFalse(inventory.parse_metadata(path)["is_selected_2140_target"])


if __name__ == "__main__":
    unittest.main()
