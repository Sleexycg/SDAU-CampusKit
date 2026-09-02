package com.sdau.campuskit

internal object CourseCacheKeys {
    private const val COURSES_PREFIX = "courses_cache_account"
    private const val CUSTOM_COURSES_PREFIX = "custom_courses_cache"
    private const val CUSTOM_COURSES_OWNER_PREFIX = "custom_courses_owner"
    private val invalidKeyCharacter = Regex("[^A-Za-z0-9_-]")

    fun imported(account: String, term: String): String =
        "${COURSES_PREFIX}_${sanitize(account)}_${sanitize(term)}"

    fun custom(account: String, term: String): String =
        "${CUSTOM_COURSES_PREFIX}_${sanitize(account)}_${sanitize(term)}"

    fun legacyCustom(term: String): String =
        "${CUSTOM_COURSES_PREFIX}_${sanitize(term)}"

    fun customOwner(term: String): String =
        "${CUSTOM_COURSES_OWNER_PREFIX}_${sanitize(term)}"

    private fun sanitize(value: String): String = value.replace(invalidKeyCharacter, "_")
}

internal object CourseWeekRule {
    fun isVisible(weeks: String, week: Int): Boolean {
        if (week <= 0) return false
        val normalized = weeks.replace("周", "").replace("—", "-").replace("至", "-")
        val ranges = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(normalized).toList()
        if (ranges.isEmpty()) return true
        return ranges.any { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@any false
            val last = match.groupValues[2].toIntOrNull() ?: first
            week in first.coerceAtLeast(1)..last
        }
    }
}
