package com.sdau.campuskit

import java.util.Calendar

internal object AcademicTermCalendar {
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
}
