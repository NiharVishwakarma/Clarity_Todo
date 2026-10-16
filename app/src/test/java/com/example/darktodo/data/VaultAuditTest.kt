package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAuditTest {
    private fun entry(id: String, password: String) = VaultEntry(id, "site-$id", "user", password, "")

    @Test fun flagsShortAndLowVarietyPasswordsAsWeak() {
        assertTrue(VaultAudit.isWeak("Short1!"))
        assertTrue(VaultAudit.isWeak("alllowercaseletters"))
        assertFalse(VaultAudit.isWeak("Str0ng-Passw0rd"))
    }

    @Test fun reportsReusedAndMissingPasswords() {
        val report = VaultAudit.analyze(listOf(
            entry("a", "Str0ng-Passw0rd"), entry("b", "Str0ng-Passw0rd"), entry("c", "Another-Str0ng-One"), entry("d", "")
        ))
        assertEquals(listOf("site-a", "site-b"), report.reused)
        assertEquals(listOf("site-d"), report.missing)
        assertTrue(report.weak.isEmpty())
        assertEquals(3, report.issueCount)
        assertEquals(4, report.total)
    }
}
