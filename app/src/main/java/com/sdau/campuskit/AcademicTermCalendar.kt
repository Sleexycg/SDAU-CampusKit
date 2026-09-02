package com.sdau.campuskit

import java.util.Calendar

internal object AcademicTermCalendar {
    const val OFFICIAL_TERM = "2026-2027-1"

    private data class StartDate(val year: Int, val month: Int, val day: Int)

    private val knownStartDates = mapOf(
        "2023-2024-1" to StartDate(2023, Calendar.SEPTEMBER, 4),
        "2023-2024-2" to StartDate(2024, Calendar.FEBRUARY, 26),
        "2024-2025-1" to StartDate(2024, Calendar.SEPTEMBER, 2),
        "2024-2025-2" to StartDate(2025, Calendar.FEBRUARY, 24),
        "2025-2026-1" to StartDate(2025, Calendar.SEPTEMBER, 1),
        "2025-2026-2" to StartDate(2026, Calendar.MARCH, 2),
        "2026-2027-1" to StartDate(2026, Calendar.SEPTEMBER, 7),
        "2026-2027-2" to StartDate(2027, Calendar.FEBRUARY, 22)
    )

    fun startDate(term: String): Calendar = Calendar.getInstance().apply {
        val known = knownStartDates[term]
        if (known != null) {
            set(known.year, known.month, known.day, 0, 0, 0)
        } else {
            val parts = term.split("-")
            val startYear = parts.firstOrNull()?.toIntOrNull() ?: get(Calendar.YEAR)
            if (parts.getOrNull(2) == "2") {
                set(startYear + 1, Calendar.FEBRUARY, 1, 0, 0, 0)
            } else {
                set(startYear, Calendar.SEPTEMBER, 1, 0, 0, 0)
            }
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        set(Calendar.MILLISECOND, 0)
    }

    fun currentTerm(now: Calendar = Calendar.getInstance()): String {
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        return when {
            month > 7 || (month == 7 && day >= 20) -> "$year-${year + 1}-1"
            month > 2 || (month == 2 && day >= 16) -> "${year - 1}-$year-2"
            else -> "${year - 1}-$year-1"
        }
    }

    fun nextTerm(term: String): String {
        val parts = term.split("-")
        if (parts.size != 3) return term
        val start = parts[0].toIntOrNull() ?: return term
        return if (parts[2] == "1") {
            "$start-${start + 1}-2"
        } else {
            "${start + 1}-${start + 2}-1"
        }
    }

    fun order(term: String): Int {
        val parts = term.split("-")
        val start = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MIN_VALUE
        val semester = parts.getOrNull(2)?.toIntOrNull() ?: return Int.MIN_VALUE
        return start * 2 + semester - 1
    }

    fun termsForAccount(
        account: String,
        now: Calendar = Calendar.getInstance()
    ): List<String>? {
        val current = currentTerm(now)
        val currentStartYear = current.substringBefore('-').toIntOrNull()
            ?: now.get(Calendar.YEAR)
        val enrollmentYear = account.take(4).toIntOrNull()
            ?.takeIf { it in 2000..currentStartYear }
            ?: return null
        val lastUndergraduateTerm = "${enrollmentYear + 3}-${enrollmentYear + 4}-2"
        val lastAvailableOrder = minOf(order(current), order(lastUndergraduateTerm))
        val result = mutableListOf<String>()
        var term = "$enrollmentYear-${enrollmentYear + 1}-1"
        while (result.size < 8 && order(term) <= lastAvailableOrder) {
            result += term
            val next = nextTerm(term)
            if (next == term) break
            term = next
        }
        return result.takeIf { it.isNotEmpty() }
    }

    fun availableTerms(
        account: String? = null,
        now: Calendar = Calendar.getInstance()
    ): List<String> {
        account?.let { termsForAccount(it, now) }?.let { return it }
        val current = currentTerm(now)
        val result = mutableListOf<String>()
        var term = if (order(current) < order(OFFICIAL_TERM)) current else OFFICIAL_TERM
        while (order(term) <= order(current)) {
            result += term
            val next = nextTerm(term)
            if (next == term) break
            term = next
        }
        return result.takeLast(8)
    }

    fun latestTermForAccount(
        account: String,
        now: Calendar = Calendar.getInstance()
    ): String {
        val current = currentTerm(now)
        val currentStartYear = current.substringBefore('-').toIntOrNull() ?: return current
        val enrollmentYear = account.take(4).toIntOrNull()
            ?.takeIf { it in 2000..currentStartYear }
            ?: return current
        val lastUndergraduateTerm = "${enrollmentYear + 3}-${enrollmentYear + 4}-2"
        return if (order(current) > order(lastUndergraduateTerm)) {
            lastUndergraduateTerm
        } else {
            current
        }
    }
}
