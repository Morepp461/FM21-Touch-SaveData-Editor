import struct
import unittest

from analyze_person_records import map_entities, parse_names


def le32(value: int) -> bytes:
    return struct.pack("<I", value)


def name_entry(name: str, local_id: int, uid: int) -> bytes:
    raw = name.encode("utf-8")
    return le32(len(raw)) + raw + le32(0) + le32(local_id) + le32(uid) + le32(uid) + le32(5) + b"\0"


class PersonRecordDiagnosticTests(unittest.TestCase):
    def test_maps_unique_uid_and_skips_name_index_copy(self):
        uid = 2_000_102_078
        entity = le32(51_788) + le32(uid) + le32(uid) + bytes.fromhex("0a8000f4")
        payload = entity + name_entry("Bob Fullam", 26_630, uid)
        names = parse_names(payload)
        resolved, ambiguous = map_entities(payload, names)
        self.assertEqual(1, len(names))
        self.assertEqual({"entity_id": 51_788, "offset": 0}, resolved[uid])
        self.assertEqual(set(), ambiguous)

    def test_conflicting_entity_rows_remain_ambiguous(self):
        uid = 2_000_102_078
        first = le32(51_788) + le32(uid) + le32(uid) + bytes.fromhex("0a8000f4")
        second = le32(51_799) + le32(uid) + le32(uid) + bytes.fromhex("0a8000f4")
        payload = first + second + name_entry("Ambiguous Person", 26_630, uid)
        names = parse_names(payload)
        resolved, ambiguous = map_entities(payload, names)
        self.assertNotIn(uid, resolved)
        self.assertIn(uid, ambiguous)

    def test_rejects_mismatched_duplicate_uid(self):
        raw = b"Test Person"
        payload = le32(len(raw)) + raw + le32(0) + le32(1) + le32(2) + le32(3) + le32(5) + b"\0"
        self.assertEqual([], parse_names(payload))


if __name__ == "__main__":
    unittest.main()
