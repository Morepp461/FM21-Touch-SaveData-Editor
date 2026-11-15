import struct
import tempfile
import unittest
from pathlib import Path

from fm21_database_person_index import (
    parse_basic_people_uid_index,
    parse_people_name_uid,
    parse_save_uid_set,
)


def u32(value):
    return struct.pack("<I", value)


def people_name_entry(name, name_id, uid):
    raw = name.encode("utf-8")
    return u32(0x3777B) + u32(name_id) + b"\x05\0\0\0\0" + u32(len(raw)) + raw + b"\0\0\0\0" + u32(name_id) + u32(uid) + u32(uid)


def basic_index_entry(name_id, uid):
    return u32(name_id) + u32(uid) + u32(uid) + b"\x11\x05\0\0\0\0"


def save_name_entry(name, local_id, uid):
    raw = name.encode("utf-8")
    return u32(len(raw)) + raw + u32(0) + u32(local_id) + u32(uid) + u32(uid) + u32(5) + b"\0"


class DatabasePersonIndexTests(unittest.TestCase):
    def test_people_name_uid_pattern(self):
        uid = 2_000_086_420
        payload = people_name_entry("Michael Gilkes", 226531, uid)
        self.assertEqual({226531: uid}, parse_people_name_uid(payload))

    def test_basic_people_uid_index_pattern(self):
        uid = 2_000_086_420
        payload = basic_index_entry(226531, uid)
        self.assertEqual({226531: uid}, parse_basic_people_uid_index(payload))

    def test_cross_table_mismatch_is_not_joined(self):
        people = parse_people_name_uid(people_name_entry("Michael Gilkes", 226531, 2_000_086_420))
        basic = parse_basic_people_uid_index(basic_index_entry(226531, 2_000_086_421))
        self.assertEqual({}, {key: value for key, value in people.items() if basic.get(key) == value})

    def test_save_name_index_extracts_uid(self):
        uid = 2_000_086_420
        self.assertEqual({uid}, parse_save_uid_set(save_name_entry("Michael Gilkes", 16031, uid)))

    def test_duplicate_conflicting_name_id_is_removed(self):
        uid_a = 2_000_086_420
        uid_b = 2_000_086_421
        payload = people_name_entry("Michael Gilkes", 226531, uid_a) + people_name_entry("Paul Ifill", 226531, uid_b)
        self.assertEqual({}, parse_people_name_uid(payload))


if __name__ == "__main__":
    unittest.main()
