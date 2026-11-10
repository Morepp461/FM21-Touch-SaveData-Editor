package com.morepp.fm21touchsaveeditor.core

/**
 * Evidence-based mapping between the TAD name index and person entity rows
 * observed in FM21 Touch 21.4.0 game_db/.dat.
 *
 * This only links a name entry's unique ID to an entity-record ID. It does
 * not infer player classification, CA/PA, attribute offsets or writable fields.
 */
data class PersonRecordMapping(
    val name: String,
    val nameIndexId: Long,
    val uniqueId: Long,
    val nameFlags: Long,
    val entityRecordId: Long?,
    val entityRecordOffset: Int?,
    val entityRecordTypeTag: String?,
    val entityRecordLength: Int?
) {
    val isMapped: Boolean get() = entityRecordId != null && entityRecordOffset != null
}

data class PersonRecordIndex(
    val entries: List<PersonRecordMapping>,
    val scannedNameEntries: Int,
    val mappedEntries: Int,
    val unresolvedEntries: Int
)

object PersonRecordMapper {
    private const val MIN_NAME_BYTES = 2
    private const val MAX_NAME_BYTES = 96
    private const val NAME_ENTRY_KIND = 5L
    private const val MAX_ENTITY_RECORD_ID = 1_000_000L

    fun map(gameDb: ByteArray): PersonRecordIndex {
        if (gameDb.size < 64) throw FmvFormatException("game_db payload is too small")

        val names = scanNameIndex(gameDb)
        if (names.isEmpty()) {
            throw FmvFormatException("No supported FM21 person-name index entries were found")
        }

        val wantedUids = HashSet<Long>(names.size * 2)
        names.forEach { wantedUids.add(it.uniqueId) }

        val resolved = HashMap<Long, Pair<Long, Int>>(names.size)
        val ambiguousUids = HashSet<Long>()
        var p = 4
        while (p <= gameDb.size - 13) {
            val uid = u32(gameDb, p)
            if (uid in wantedUids && u32(gameDb, p + 4) == uid) {
                val afterPair = u32(gameDb, p + 8)
                val afterMarker = gameDb[p + 12].toInt() and 0xff
                val isNameIndexCopy = afterPair == NAME_ENTRY_KIND && afterMarker == 0

                if (!isNameIndexCopy) {
                    val entityId = u32(gameDb, p - 4)
                    if (entityId in 1..MAX_ENTITY_RECORD_ID && uid !in ambiguousUids) {
                        val candidate = entityId to (p - 4)
                        val previous = resolved[uid]
                        if (previous == null) {
                            resolved[uid] = candidate
                        } else if (previous != candidate) {
                            // Multiple different entity rows for one UID are ambiguous.
                            resolved.remove(uid)
                            ambiguousUids.add(uid)
                        }
                    }
                }
            }
            p++
        }

        val resolvedByEntityId = resolved.values.associateBy { it.first }
        val entries = names.map { name ->
            val entity = if (name.uniqueId in ambiguousUids) null else resolved[name.uniqueId]
            val nextEntity = entity?.let { resolvedByEntityId[it.first + 1] }
            val inferredLength = if (entity != null && nextEntity != null && nextEntity.second > entity.second) {
                (nextEntity.second - entity.second).takeIf { it in 16..4096 }
            } else null
            val typeTag = entity?.let { record ->
                if (record.second + 16 <= gameDb.size) {
                    gameDb.copyOfRange(record.second + 12, record.second + 16)
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                } else null
            }
            PersonRecordMapping(
                name = name.name,
                nameIndexId = name.localId,
                uniqueId = name.uniqueId,
                nameFlags = name.flags,
                entityRecordId = entity?.first,
                entityRecordOffset = entity?.second,
                entityRecordTypeTag = typeTag,
                entityRecordLength = inferredLength
            )
        }

        val mapped = entries.count { it.isMapped }
        return PersonRecordIndex(
            entries = entries,
            scannedNameEntries = entries.size,
            mappedEntries = mapped,
            unresolvedEntries = entries.size - mapped
        )
    }

    private data class NameEntry(
        val name: String,
        val localId: Long,
        val uniqueId: Long,
        val flags: Long
    )

    private fun scanNameIndex(data: ByteArray): List<NameEntry> {
        val result = ArrayList<NameEntry>()
        var p = 0
        val last = data.size - (4 + MIN_NAME_BYTES + 21)
        while (p <= last) {
            val nameLength = u32(data, p)
            if (nameLength in MIN_NAME_BYTES.toLong()..MAX_NAME_BYTES.toLong()) {
                val nameStart = p + 4
                val tail = nameStart + nameLength.toInt()
                if (tail + 21 <= data.size &&
                    u32(data, tail + 16) == NAME_ENTRY_KIND &&
                    data[tail + 20].toInt() == 0) {
                    val uniqueId = u32(data, tail + 8)
                    if (uniqueId == u32(data, tail + 12) && uniqueId > 0) {
                        val nameBytes = data.copyOfRange(nameStart, tail)
                        val name = nameBytes.toString(Charsets.UTF_8)
                        if (isPlausibleName(name)) {
                            result += NameEntry(
                                name = name,
                                localId = u32(data, tail + 4),
                                uniqueId = uniqueId,
                                flags = u32(data, tail)
                            )
                            p = tail + 21
                            continue
                        }
                    }
                }
            }
            p++
        }
        return result
    }

    private fun isPlausibleName(value: String): Boolean =
        value.length in 2..96 &&
            value.any { it.isLetter() } &&
            value.none { it.isISOControl() }

    private fun u32(data: ByteArray, at: Int): Long =
        (data[at].toLong() and 0xff) or
            ((data[at + 1].toLong() and 0xff) shl 8) or
            ((data[at + 2].toLong() and 0xff) shl 16) or
            ((data[at + 3].toLong() and 0xff) shl 24)
}
