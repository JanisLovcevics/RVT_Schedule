package com.example.edupageschedule

data class Schedule(val days: List<Day>)

data class Day(
        val date: String,
        val dateFormatted: String,
        val dayName: String,
        val lessons: List<Lesson>
)

data class Lesson(
        val period: Int,
        val time: String,
        val subject: String,
        val subjectShort: String,
        val teachers: List<String>,
        val classrooms: List<String>,
        val group: String?,
        val changed: Boolean,
        val duration: Int,
        val changeType: LessonChangeType = LessonChangeType.NONE,
        val isRemote: Boolean = false,
        val isMoved: Boolean = false,
        val movedFrom: String? = null,
        val movedTo: String? = null,
        val originalSubject: String? = null,
        val originalTeachers: List<String>? = null,
        val originalClassrooms: List<String>? = null
)

data class Subject(val id: String, val name: String, val short: String)

data class Teacher(val id: String, val name: String, val short: String)

data class Classroom(val id: String, val name: String, val short: String)

data class Period(val period: Int, val startTime: String, val endTime: String)

enum class LessonChangeType {
    NONE,
    ADDED,
    CANCELLED,
    MODIFIED
}
