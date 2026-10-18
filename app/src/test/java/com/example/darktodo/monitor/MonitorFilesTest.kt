package com.example.darktodo.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class MonitorFilesTest {
    private fun directory(): Path = Files.createDirectories(Paths.get("build", "monitor-test-${UUID.randomUUID()}"))

    private fun remove(directory: Path) {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    @Test fun classificationDoesNotTreatApkBundleAsSingleInstaller() {
        assertEquals("APK installer", documentKind("Example.APK", "application/octet-stream"))
        assertEquals("APK bundle archive (not installed)", documentKind("Example.apks", "application/zip"))
        assertEquals("Archive", documentKind("Example.ZIP", "application/octet-stream"))
        assertEquals("Image", documentKind("example", "image/png"))
        assertEquals("Document", documentKind("example", "application/octet-stream"))
    }

    @Test fun discoveryStopsCyclesExcessDepthAndDirectoryCount() {
        val directories = DiscoveryDirectories(maxDepth = 2, maxDirectories = 3)
        assertTrue(directories.accept("root", 0))
        assertFalse(directories.accept("root", 1))
        assertTrue(directories.accept("child", 1))
        assertFalse(directories.accept("too-deep", 3))
        assertTrue(directories.accept("grandchild", 2))
        assertFalse(directories.accept("over-budget", 1))
    }

    @Test fun discoveryTimeoutKeepsPartialRowsAndRequestsProviderCancellation() = runBlocking {
        val found = mutableListOf("first")
        var providerCancelled = false
        val completed = runDiscoveryWithinDeadline(30, { providerCancelled = true }) {
            found.add("second")
            delay(10_000)
        }
        assertFalse(completed)
        assertEquals(listOf("first", "second"), found)
        assertTrue(providerCancelled)
    }

    @Test fun explicitDiscoveryCancellationIsNotMistakenForTimeout() = runBlocking {
        var propagated = false
        var providerCancelled = false
        try {
            runDiscoveryWithinDeadline(10_000, { providerCancelled = true }) { throw CancellationException("user cancel") }
        } catch (_: CancellationException) { propagated = true }
        assertTrue(propagated)
        assertTrue(providerCancelled)
    }

    @Test fun deadlineSignalsProviderWhileCollectionThreadIsBlocked() = runBlocking {
        val cancellation = CountDownLatch(1)
        val completed = runDiscoveryWithinDeadline(30, cancellation::countDown) {
            assertTrue(cancellation.await(2, TimeUnit.SECONDS))
            delay(1_000)
        }
        assertFalse(completed)
    }

    @Test fun onlyCacheContentsAreDeletedAndRootRemains() {
        val dir = directory()
        try {
            val cache = Files.createDirectories(dir.resolve("cache"))
            val nested = Files.createDirectories(cache.resolve("nested"))
            Files.write(nested.resolve("one"), byteArrayOf(1, 2, 3))
            val outside = Files.write(dir.resolve("vault"), byteArrayOf(4, 5))
            val before = MonitorCache.walk(listOf(cache.toFile()), false)
            assertEquals(3L, before.bytes)
            assertEquals(1, before.files)
            assertFalse(before.partial)
            val result = MonitorCache.walk(listOf(cache.toFile()), true)
            assertEquals(3L, result.bytes)
            assertEquals(1, result.files)
            assertFalse(result.partial)
            assertTrue(Files.isDirectory(cache))
            assertFalse(Files.exists(nested))
            assertEquals(2L, Files.size(outside))
        } finally { remove(dir) }
    }

    @Test fun symlinkEscapeIsSkippedForMeasurementAndDeletion() {
        val dir = directory()
        try {
            val cache = Files.createDirectories(dir.resolve("cache"))
            val outside = Files.write(dir.resolve("secret"), byteArrayOf(1, 2))
            Files.createSymbolicLink(cache.resolve("link"), outside.toAbsolutePath())
            val result = MonitorCache.walk(listOf(cache.toFile()), true)
            assertTrue(result.partial)
            assertEquals(0L, result.bytes)
            assertTrue(Files.exists(outside))
        } finally { remove(dir) }
    }

    @Test fun symbolicRootIsNeverTraversed() {
        val dir = directory()
        try {
            val outside = Files.createDirectories(dir.resolve("outside"))
            Files.write(outside.resolve("data"), byteArrayOf(1))
            val root = Files.createSymbolicLink(dir.resolve("cache"), outside.toAbsolutePath())
            val result = MonitorCache.walk(listOf(root.toFile()), true)
            assertTrue(result.partial)
            assertTrue(Files.exists(outside.resolve("data")))
        } finally { remove(dir) }
    }

    @Test fun traversalIsCancellableBeforeDeleting() {
        val dir = directory()
        try {
            Files.write(dir.resolve("data"), byteArrayOf(1))
            var cancelled = false
            try {
                MonitorCache.walk(listOf(dir.toFile()), true) { throw kotlinx.coroutines.CancellationException("test") }
            } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertTrue(Files.exists(dir.resolve("data")))
        } finally { remove(dir) }
    }

    @Test fun excessiveDepthReportsPartialAndPreservesSkippedFiles() {
        val dir = directory()
        try {
            var nested = dir
            repeat(35) { nested = Files.createDirectories(nested.resolve("nested")) }
            val data = Files.write(nested.resolve("data"), byteArrayOf(1))
            val result = MonitorCache.walk(listOf(dir.toFile()), true)
            assertTrue(result.partial)
            assertTrue(Files.exists(data))
        } finally { remove(dir) }
    }
}
