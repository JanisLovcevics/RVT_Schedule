package com.example.edupageschedule

import java.text.SimpleDateFormat
import java.util.*

// Структура для хранения информации о уроках в группах
internal data class GroupLessonInfo(
        val lessonG1: String = "",
        val timeG1: String = "",
        val lessonG2: String = "",
        val timeG2: String = "",
        val commonTime: String = "",
        val isCommon: Boolean = false
) {
    fun isEmpty(): Boolean = lessonG1.isEmpty() && lessonG2.isEmpty()
}

// Находит текущий урок в группах с учетом правил отображения
internal fun findCurrentLessonForGroups(
        group1OnlyLessons: List<Lesson>,
        group2OnlyLessons: List<Lesson>,
        noGroupLessons: List<Lesson>,
        currentDate: String,
        currentTime: Date
): GroupLessonInfo {
    // Ищем текущий урок среди общих уроков (без групп)
    val (commonLesson, commonTimeVal) = findCurrentLesson(noGroupLessons, currentDate, currentTime)

    if (commonLesson.isNotEmpty()) {
        // Общий урок у всего класса - выводим только его
        // Используем commonTime для отображения времени внизу
        return GroupLessonInfo(lessonG1 = commonLesson, commonTime = commonTimeVal, isCommon = true)
    }

    // Ищем текущие уроки у обеих групп
    val (lesson1, time1) = findCurrentLesson(group1OnlyLessons, currentDate, currentTime)
    val (lesson2, time2) = findCurrentLesson(group2OnlyLessons, currentDate, currentTime)

    // Проверяем, есть ли уроки у обеих групп
    if (lesson1.isNotEmpty() && lesson2.isNotEmpty()) {
        val timePart1 = time1.substringBefore('\n')
        val timePart2 = time2.substringBefore('\n')

        val (finalTime1, finalTime2, common) =
                if (timePart1 == timePart2) {
                    val room1 = if (time1.contains('\n')) "\n" + time1.substringAfter('\n') else ""
                    val room2 = if (time2.contains('\n')) "\n" + time2.substringAfter('\n') else ""
                    Triple(room1.trim(), room2.trim(), timePart1)
                } else {
                    Triple(time1, time2, "")
                }

        // У обеих групп разные уроки - выводим оба (без префиксов)
        return GroupLessonInfo(
                lessonG1 = lesson1,
                timeG1 = finalTime1,
                lessonG2 = lesson2,
                timeG2 = finalTime2,
                commonTime = common,
                isCommon = false
        )
    } else if (lesson1.isNotEmpty()) {
        // Урок только у первой группы
        return GroupLessonInfo(lessonG1 = lesson1, timeG1 = time1, isCommon = false)
    } else if (lesson2.isNotEmpty()) {
        // Урок только у второй группы
        return GroupLessonInfo(lessonG1 = "", lessonG2 = lesson2, timeG2 = time2, isCommon = false)
    }

    return GroupLessonInfo()
}

// Находит следующий урок в группах с учетом правил отображения
internal fun findNextLessonForGroups(
        group1OnlyLessons: List<Lesson>,
        group2OnlyLessons: List<Lesson>,
        noGroupLessons: List<Lesson>,
        currentDate: String,
        afterTime: Date,
        currentTime: Date
): GroupLessonInfo {
    // Ищем следующий урок среди общих уроков (без групп)
    val (commonLesson, commonTimeVal) =
            findNextLessonAfterTime(noGroupLessons, currentDate, afterTime, currentTime)

    // Ищем следующие уроки у обеих групп
    val (lesson1, time1) =
            findNextLessonAfterTime(group1OnlyLessons, currentDate, afterTime, currentTime)
    val (lesson2, time2) =
            findNextLessonAfterTime(group2OnlyLessons, currentDate, afterTime, currentTime)

    // Определяем эффективный урок для Группы 1 (выбираем более ранний)
    var effLesson1 = lesson1
    var effTime1 = time1
    var useCommonForG1 = false

    val commonStart = getStartTime(commonTimeVal, currentDate)
    val g1Start = getStartTime(time1, currentDate)

    if (commonLesson.isNotEmpty()) {
        if (effLesson1.isEmpty()) {
            effLesson1 = commonLesson
            effTime1 = commonTimeVal
            useCommonForG1 = true
        } else if (commonStart != null && g1Start != null && commonStart.before(g1Start)) {
            // Общий урок раньше группового
            effLesson1 = commonLesson
            effTime1 = commonTimeVal
            useCommonForG1 = true
        }
    }

    // Определяем эффективный урок для Группы 2
    var effLesson2 = lesson2
    var effTime2 = time2
    var useCommonForG2 = false

    val g2Start = getStartTime(time2, currentDate)

    if (commonLesson.isNotEmpty()) {
        if (effLesson2.isEmpty()) {
            effLesson2 = commonLesson
            effTime2 = commonTimeVal
            useCommonForG2 = true
        } else if (commonStart != null && g2Start != null && commonStart.before(g2Start)) {
            // Общий урок раньше группового
            effLesson2 = commonLesson
            effTime2 = commonTimeVal
            useCommonForG2 = true
        }
    }

    // Если оба "эффективных" урока - это один и тот же общий урок, то показываем его как общий
    if (useCommonForG1 && useCommonForG2 && effLesson1 == effLesson2) {
        return GroupLessonInfo(lessonG1 = commonLesson, commonTime = commonTimeVal, isCommon = true)
    }

    // Иначе показываем раздельно (даже если один из них общий, а другой - нет)
    if (effLesson1.isNotEmpty() && effLesson2.isNotEmpty()) {
        val timePart1 = effTime1.substringBefore('\n')
        val timePart2 = effTime2.substringBefore('\n')

        val (finalTime1, finalTime2, common) =
                if (timePart1 == timePart2) {
                    val room1 =
                            if (effTime1.contains('\n')) "\n" + effTime1.substringAfter('\n')
                            else ""
                    val room2 =
                            if (effTime2.contains('\n')) "\n" + effTime2.substringAfter('\n')
                            else ""
                    Triple(room1.trim(), room2.trim(), timePart1)
                } else {
                    Triple(effTime1, effTime2, "")
                }

        return GroupLessonInfo(
                lessonG1 = effLesson1,
                timeG1 = finalTime1,
                lessonG2 = effLesson2,
                timeG2 = finalTime2,
                commonTime = common,
                isCommon = false
        )
    } else if (effLesson1.isNotEmpty()) {
        return GroupLessonInfo(lessonG1 = effLesson1, timeG1 = effTime1, isCommon = false)
    } else if (effLesson2.isNotEmpty()) {
        return GroupLessonInfo(
                lessonG1 = "",
                lessonG2 = effLesson2,
                timeG2 = effTime2,
                isCommon = false
        )
    }

    return GroupLessonInfo()
}

// Вспомогательная функция для получения времени начала урока
internal fun getStartTime(timeRange: String?, currentDate: String): Date? {
    if (timeRange == null) return null
    val times = timeRange.split("-")
    if (times.size != 2) return null

    return try {
        val timeStr = times[0].trim()
        val fullDateStr = "$currentDate $timeStr"
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(fullDateStr)
    } catch (e: Exception) {
        null
    }
}

// Находит текущий урок в списке уроков
internal fun findCurrentLesson(
        lessons: List<Lesson>,
        currentDate: String,
        currentTime: Date
): Pair<String, String> {
    // Сортируем уроки по времени начала в предсказуемом порядке
    val sortedLessons =
            lessons.sortedBy { lesson ->
                try {
                    val times = lesson.time.split("-")
                    if (times.size != 2) return@sortedBy Long.MAX_VALUE
                    val timeStr = times[0].trim()
                    val fullDateStr = "$currentDate $timeStr"
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .parse(fullDateStr)
                            ?.time
                            ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            }

    for (lesson in sortedLessons) {
        val times = lesson.time.split("-")
        if (times.size != 2) continue

        val startTime =
                try {
                    val timeStr = times[0].trim()
                    val fullDateStr = "$currentDate $timeStr"
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(fullDateStr)
                } catch (e: Exception) {
                    continue
                }

        val endTime =
                try {
                    val timeStr = times[1].trim()
                    val fullDateStr = "$currentDate $timeStr"
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(fullDateStr)
                } catch (e: Exception) {
                    continue
                }

        // Урок идет прямо сейчас
        if (currentTime.after(startTime) && currentTime.before(endTime)) {
            val minutesLeft = ((endTime.time - currentTime.time) / 1000 / 60).toInt()
            val classroomStr =
                    if (lesson.classrooms.isNotEmpty())
                            "\nKab: ${lesson.classrooms.joinToString(", ")}"
                    else ""
            return Pair(lesson.subject, "Lidz beigam: $minutesLeft min$classroomStr")
        }
    }
    return Pair("", "")
}

// Находит следующий урок после указанного времени
internal fun findNextLessonAfterTime(
        lessons: List<Lesson>,
        currentDate: String,
        afterTime: Date,
        currentTime: Date
): Pair<String, String> {
    // Сортируем уроки по времени начала, чтобы найти самый ранний следующий урок
    val sortedLessons =
            lessons.sortedBy { lesson ->
                try {
                    val times = lesson.time.split("-")
                    if (times.size != 2) return@sortedBy Long.MAX_VALUE
                    val timeStr = times[0].trim()
                    val fullDateStr = "$currentDate $timeStr"
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .parse(fullDateStr)
                            ?.time
                            ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            }

    for (lesson in sortedLessons) {
        val times = lesson.time.split("-")
        if (times.size != 2) continue

        val startTime =
                try {
                    val timeStr = times[0].trim()
                    val fullDateStr = "$currentDate $timeStr"
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(fullDateStr)
                } catch (e: Exception) {
                    continue
                }

        // Ищем урок, который начинается после указанного времени
        if (startTime.after(afterTime)) {
            val classroomStr =
                    if (lesson.classrooms.isNotEmpty())
                            "\nKab: ${lesson.classrooms.joinToString(", ")}"
                    else ""
            // Возвращаем абсолютное время урока (как для завтрашнего дня), а не относительное
            return Pair(lesson.subject, "${lesson.time}$classroomStr")
        }
    }
    return Pair("", "")
}
