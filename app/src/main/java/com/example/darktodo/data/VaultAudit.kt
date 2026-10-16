package com.example.darktodo.data

data class VaultAuditReport(
    val total: Int,
    val weak: List<String>,
    val reused: List<String>,
    val missing: List<String>
) {
    val issueCount: Int get() = (weak + reused + missing).distinct().size
}

/** Local-only password hygiene check. Reports entry titles, never password values. */
object VaultAudit {
    fun analyze(entries: List<VaultEntry>): VaultAuditReport {
        val missing = entries.filter { it.password.isEmpty() }
        val filled = entries.filter { it.password.isNotEmpty() }
        val weak = filled.filter { isWeak(it.password) }
        val reused = filled.groupBy { it.password }.values.filter { it.size > 1 }.flatten()
        return VaultAuditReport(entries.size, weak.map { it.title }, reused.map { it.title }, missing.map { it.title })
    }

    fun isWeak(password: String): Boolean {
        val classes = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() }
        ).count { it }
        return password.length < 10 || classes < 3
    }
}
