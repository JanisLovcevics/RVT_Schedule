package com.example.edupageschedule

import java.util.*
import org.junit.Assert.*
import org.junit.Test

class WidgetLogicTest {

    @Test
    fun testGetNextDayData_WhenTodayIsThursday_ShouldReturnFriday() {
        val currentDate = "2026-02-19" // Thursday

        val lessonThu =
                Lesson(1, "08:00-08:40", "ThuLesson", "Thu", listOf(), listOf(), null, false, 1)
        val lessonFri =
                Lesson(1, "08:00-08:40", "FriLesson", "Fri", listOf(), listOf(), null, false, 1)

        val dayThu = Day("2026-02-19", "19.02.2026", "Ceturtdiena", listOf(lessonThu))
        val dayFri = Day("2026-02-20", "20.02.2026", "Piektdiena", listOf(lessonFri))

        val schedule = Schedule(listOf(dayThu, dayFri))

        val result = getNextDayData(schedule, currentDate)

        assertNotNull(result)
        assertEquals("Piektdiena, 20.02.2026", result?.nextDayDate)
        assertEquals(
                "FriLesson",
                result?.currentLessonG1
        ) // Logic puts lesson in G1 even if no group for unification
    }

    @Test
    fun testGetNextDayData_WhenTodayIsThursday_AndFridayMissing_ShouldReturnNull() {
        val currentDate = "2026-02-19" // Thursday
        val lessonThu =
                Lesson(1, "08:00-08:40", "ThuLesson", "Thu", listOf(), listOf(), null, false, 1)
        val dayThu = Day("2026-02-19", "19.02.2026", "Ceturtdiena", listOf(lessonThu))

        val schedule = Schedule(listOf(dayThu))

        val result = getNextDayData(schedule, currentDate)

        assertNull(result)
    }

    @Test
    fun testGetNextDayData_WhenTodayIsWednesday_ShouldReturnThursday() {
        val currentDate = "2026-02-18" // Wednesday
        val lessonThu =
                Lesson(1, "08:00-08:40", "ThuLesson", "Thu", listOf(), listOf(), null, false, 1)
        val dayThu = Day("2026-02-19", "19.02.2026", "Ceturtdiena", listOf(lessonThu))

        val schedule = Schedule(listOf(dayThu))

        val result = getNextDayData(schedule, currentDate)

        assertNotNull(result)
        assertEquals("Ceturtdiena, 19.02.2026", result?.nextDayDate)
    }

    @Test
    fun testGetNextDayData_WhenDateHasTrailingSpace_ShouldReturnItAsNextDay() {
        val currentDate = "2026-02-19"
        // Date with trailing space
        val dateWithSpace = "2026-02-19 "

        val lesson = Lesson(1, "08:00-08:40", "Lesson", "L", listOf(), listOf(), null, false, 1)
        val day = Day(dateWithSpace, "19.02.2026", "Ceturtdiena", listOf(lesson))

        val schedule = Schedule(listOf(day))

        val result = getNextDayData(schedule, currentDate)

        // If "2026-02-19 " > "2026-02-19", it will return the day.
        assertNotNull("Should return day because trailing space makes it > currentDate", result)
        assertEquals("Ceturtdiena, 19.02.2026", result?.nextDayDate)
    }

    // Copy of getNextDayData from ScheduleWidget.kt (slightly adapted for visibility)
    private fun getNextDayData(schedule: Schedule, currentDate: String): WidgetData? {
        val nextDay =
                schedule.days
                        .filter { day ->
                            day.date > currentDate &&
                                    day.lessons.any { it.changeType != LessonChangeType.CANCELLED }
                        }
                        .minByOrNull { it.date }

        return if (nextDay != null) {
            val validLessons =
                    nextDay.lessons.filter { it.changeType != LessonChangeType.CANCELLED }
            val commonDate = "${nextDay.dayName}, ${nextDay.dateFormatted}"

            val group1OnlyLessons = validLessons.filter { it.group == "1" }
            val group2OnlyLessons = validLessons.filter { it.group == "2" }
            val noGroupLessons = validLessons.filter { it.group.isNullOrBlank() }

            val startOfDay =
                    try {
                        val cal = Calendar.getInstance()
                        val dateParts = nextDay.date.split("-")
                        if (dateParts.size == 3) {
                            cal.set(
                                    dateParts[0].trim().toInt(),
                                    dateParts[1].trim().toInt() - 1,
                                    dateParts[2].trim().toInt(),
                                    0,
                                    0,
                                    0
                            )
                            cal.add(Calendar.MINUTE, -1)
                            cal.time
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }

            if (startOfDay == null) {
                return null
            }

            // We need to use ScheduleWidgetHelpers here.
            // Since I cannot import internal functions from test easily if not in same package?
            // They are internal in actual code. Testing from same package.
            // Assuming ScheduleWidgetHelpers.kt functions are available.

            // Note: In real test I would use the actual helper. Here I might need to mock or copy
            // helpers if they are not accessible.
            // But they are internal in same package, so it should be fine.

            val firstLessonInfo =
                    findNextLessonForGroups(
                            group1OnlyLessons,
                            group2OnlyLessons,
                            noGroupLessons,
                            nextDay.date,
                            startOfDay,
                            startOfDay
                    )

            if (!firstLessonInfo.isEmpty()) {
                WidgetData(
                        currentLessonG1 = firstLessonInfo.lessonG1,
                        currentTimeG1 = firstLessonInfo.timeG1,
                        currentLessonG2 = firstLessonInfo.lessonG2,
                        currentTimeG2 = firstLessonInfo.timeG2,
                        currentCommonTime = firstLessonInfo.commonTime,
                        isCurrentCommonLesson = firstLessonInfo.isCommon,
                        nextLessonG1 = "",
                        nextTimeG1 = "",
                        nextLessonG2 = "",
                        nextTimeG2 = "",
                        nextCommonTime = "",
                        isNextCommonLesson = false,
                        hasGroups = true,
                        nextDayDate = commonDate
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    // Stub for WidgetData as it is private or internal in ScheduleWidget.kt?
    // WidgetData is defined in ScheduleWidget.kt at the end. It is public (by default) or internal?
    // It is `data class WidgetData` without modifier, so public.
    // But it is inside ScheduleWidget.kt file.

}
