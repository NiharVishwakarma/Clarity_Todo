package com.example.darktodo.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BackupHistoryTest {
    private fun point(index: Int) = BackupPoint(
        "id$index", "snapshot-$index.json", index.toLong(), "manual", 1, null,
        "a".repeat(64), null, false, "operation-$index", true
    )

    @Test fun orderingIsNewestFirstStableAndUniqueByDriveId() {
        val input = listOf(point(1), point(3), point(2), point(3))
        assertEquals(listOf("id3", "id2", "id1"), BackupHistory.newestFirst(input).map { it.id })
    }

    @Test fun unknownDateIsNotFabricatedAndSortsLast() {
        val unknown = point(3).copy(createdAt = null)
        assertEquals(listOf("id1", "id3"), BackupHistory.newestFirst(listOf(unknown, point(1))).map { it.id })
    }

    @Test fun retainsLatestTenVerifiedSnapshotsIncludingManualAutomaticAndSafety() {
        val points = (1..13).map { point(it).copy(type = listOf("manual", "automatic", "safety")[it % 3]) }
        assertEquals(listOf("id3", "id2", "id1"), BackupHistory.excess(points).map { it.id })
        assertEquals(emptyList<BackupPoint>(), BackupHistory.excess(points.take(10)))
    }

    @Test fun neverPrunesLegacyUnverifiedUnsupportedUnknownDateOrProtectedPoints() {
        val points = (1..12).map(::point)
        val special = listOf(
            point(100).copy(legacy = true), point(101).copy(verified = false),
            point(102).copy(version = 2), point(103).copy(sha256 = null), point(104).copy(createdAt = null)
        )
        assertEquals(listOf("id2"), BackupHistory.excess(points + special, setOf("id1")).map { it.id })
    }

    @Test fun legacyIsDiscoveredWithoutMigrationOrInventedMetadata() {
        val legacy = BackupHistory.parse(JSONObject()
            .put("id", "old-id").put("name", "clarity-backup.json")
            .put("modifiedTime", "2026-10-20T01:02:03Z").put("size", "1234"))!!
        assertTrue(legacy.legacy)
        assertEquals(1234L, legacy.size)
        assertTrue(legacy.createdAt!! > 0)
        assertNull(legacy.type)
        assertNull(legacy.version)
        assertFalse(legacy.verified)
        val unknown = BackupHistory.parse(JSONObject().put("id", "old").put("name", "clarity-backup.json"))!!
        assertNull(unknown.createdAt)
    }

    @Test fun metadataPreservesStableIdTypeTimestampVersionSizeAndOperation() {
        val point = BackupHistory.parse(JSONObject().put("id", "drive-123").put("name", "dated.json")
            .put("size", "321").put("createdTime", "2026-10-20T01:00:00Z")
            .put("appProperties", JSONObject().put("clarityBackup", BackupHistory.MARKER)
                .put("snapshotTime", "1000").put("backupType", "automatic").put("schema", "1")
                .put("sha256", "b".repeat(64)).put("verified", "true").put("operationId", "work-id")))!!
        assertEquals("drive-123", point.id)
        assertEquals(1000L, point.createdAt)
        assertEquals("automatic", point.type)
        assertEquals(1, point.version)
        assertEquals(321L, point.size)
        assertEquals("work-id", point.operationId)
        assertTrue(point.verified)
    }

    @Test fun unrelatedFilesAreNeverHistoryEntries() {
        assertNull(BackupHistory.parse(JSONObject().put("id", "other").put("name", "other-file.json")))
    }

    @Test fun verifiesExactBytesAndLegacyDriveChecksum() {
        val bytes = "snapshot".toByteArray()
        val valid = point(1).copy(size = bytes.size.toLong(), sha256 = BackupHistory.digest(bytes),
            md5 = BackupHistory.digest(bytes, "MD5"))
        BackupHistory.verify(valid, bytes)
        BackupHistory.verify(valid.copy(legacy = true, sha256 = null), bytes)
    }

    @Test fun rejectsDamagedSizeDigestAndMissingNewSnapshotIntegrityMetadata() {
        val bytes = "snapshot".toByteArray()
        listOf(point(1).copy(size = 1), point(1), point(1).copy(sha256 = null),
            point(1).copy(sha256 = BackupHistory.digest(bytes), md5 = "0".repeat(32))).forEach {
            expectFailure { BackupHistory.verify(it, bytes) }
        }
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); throw AssertionError("Expected invalid backup to fail.") } catch (_: IOException) { }
    }
}
