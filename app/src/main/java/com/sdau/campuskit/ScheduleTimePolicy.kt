package com.sdau.campuskit

import java.util.Calendar

internal enum class ScheduleMode {
    SPRING,
    SUMMER
}

/**
 * Selects the university timetable from the device's local calendar.
 *
 * Spring/autumn timetable: August 1 through April 30 of the following year.
 * Summer timetable: May 1 through July 31.
 */
internal object ScheduleTimePolicy {
    private val springStartMinutes = intArrayOf(480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195)
    private val summerStartMinutes = intArrayOf(480, 535, 600, 655, 870, 925, 990, 1045, 1170, 1225)

    fun modeFor(date: Calendar): ScheduleMode {
        val month = date.get(Calendar.MONTH)
        return if (month in Calendar.MAY..Calendar.JULY) {
            ScheduleMode.SUMMER
        } else {
            ScheduleMode.SPRING
        }
    }

    fun currentMode(): ScheduleMode = modeFor(Calendar.getInstance())

    fun startMinutes(mode: ScheduleMode): IntArray = when (mode) {
        ScheduleMode.SPRING -> springStartMinutes
        ScheduleMode.SUMMER -> summerStartMinutes
    }.copyOf()

    fun timeRanges(mode: ScheduleMode): Array<Pair<Int, Int>> {
        val starts = startMinutes(mode)
        return Array(starts.size) { index -> starts[index] to starts[index] + 45 }
    }

    fun displayRanges(mode: ScheduleMode): Array<Pair<String, String>> =
        timeRanges(mode).map { (start, end) ->
            formatMinutes(start) to formatMinutes(end)
        }.toTypedArray()

    private fun formatMinutes(minutes: Int): String =
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}
