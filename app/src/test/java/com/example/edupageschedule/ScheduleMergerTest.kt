package com.example.edupageschedule

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScheduleMergerTest {

        private lateinit var schedule: Schedule
        private lateinit var substitutions: SubstitutionData

        @Before
        fun setUp() {
                // Base schedule for testing
                val lesson1 =
                        Lesson(
                                1,
                                "08:30-09:10",
                                "Math",
                                "Math",
                                listOf("T1"),
                                listOf("101"),
                                null,
                                false,
                                1
                        )
                val lesson2 =
                        Lesson(
                                2,
                                "09:20-10:00",
                                "Physics",
                                "Phys",
                                listOf("T2"),
                                listOf("102"),
                                null,
                                false,
                                1
                        )

                val day = Day("2024-02-17", "17.02.2024", "Saturday", listOf(lesson1, lesson2))
                schedule = Schedule(listOf(day))
        }

        @Test
        fun testCancelledLesson() {
                // Create substitution for cancellation
                val sub =
                        Substitution(
                                type = ChangeType.REMOVED,
                                period = "1",
                                subject = "Math",
                                subjectNew = null,
                                group = null,
                                teacherOld = null,
                                teacherNew = null,
                                roomOld = null,
                                roomNew = null,
                                roomChanged = false,
                                isRemote = false,
                                isCancelled = true,
                                rawDescription = "Cancelled Math"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(sub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 1 }
                assertNotNull(lesson)
                assertEquals(LessonChangeType.CANCELLED, lesson!!.changeType)
                assertTrue(lesson.changed)
        }

        @Test
        fun testAddedLesson() {
                // Create substitution for added lesson
                val sub =
                        Substitution(
                                type = ChangeType.ADDED,
                                period = "3",
                                subject = "History",
                                subjectNew = "History",
                                group = null,
                                teacherOld = null,
                                teacherNew = "T3",
                                roomOld = null,
                                roomNew = "103",
                                roomChanged = false,
                                isRemote = false,
                                isCancelled = false,
                                rawDescription = "Added History"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(sub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 3 }
                assertNotNull(lesson)
                assertEquals(LessonChangeType.ADDED, lesson!!.changeType)
                assertEquals("History", lesson.subject)
                assertEquals("T3", lesson.teachers.firstOrNull())
                assertEquals("103", lesson.classrooms.firstOrNull())
        }

        @Test
        fun testModifiedLesson() {
                // Create substitution for modified lesson (room change)
                val sub =
                        Substitution(
                                type = ChangeType.CHANGED,
                                period = "2",
                                subject = "Physics",
                                subjectNew = null,
                                group = null,
                                teacherOld = null,
                                teacherNew = null,
                                roomOld = "102",
                                roomNew = "202",
                                roomChanged = true,
                                isRemote = false,
                                isCancelled = false,
                                rawDescription = "Room changed to 202"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(sub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 2 }
                assertNotNull(lesson)
                assertEquals(LessonChangeType.MODIFIED, lesson!!.changeType)
                assertEquals("202", lesson.classrooms.firstOrNull())
        }

        @Test
        fun testSubjectReplaced() {
                // Замена предмета: Math -> Chemistry
                // Урок в расписании называется "Math", в замене subject="Math",
                // subjectNew="Chemistry"
                val sub =
                        Substitution(
                                type = ChangeType.CHANGED,
                                period = "1",
                                subject = "Math",
                                subjectNew = "Chemistry",
                                group = null,
                                teacherOld = null,
                                teacherNew = "T5",
                                roomOld = null,
                                roomNew = "201",
                                roomChanged = true,
                                isRemote = false,
                                isCancelled = false,
                                rawDescription = "(Math) -> Chemistry - T5, Kab. 201"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(sub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 1 }
                assertNotNull("Урок должен быть найден по старому предмету Math", lesson)
                assertEquals(LessonChangeType.MODIFIED, lesson!!.changeType)
                // Предмет должен обновиться на Chemistry
                assertEquals("Chemistry", lesson.subject)
                // Кабинет должен обновиться
                assertEquals("201", lesson.classrooms.firstOrNull())
                // Учитель должен обновиться
                assertEquals("T5", lesson.teachers.firstOrNull())
        }

        @Test
        fun testSubjectNotFoundByNewName() {
                // Убеждаемся что поиск НЕ по новому названию — если предмет в расписании "Math",
                // а замена ищет по "Chemistry" (old behaviour), то урок не будет найден.
                // Этот тест проверяет что старая логика была неправильной (документационный тест).
                val sub =
                        Substitution(
                                type = ChangeType.CHANGED,
                                period = "1",
                                subject = "Math",
                                subjectNew = "Chemistry",
                                group = null,
                                teacherOld = null,
                                teacherNew = null,
                                roomOld = null,
                                roomNew = null,
                                roomChanged = false,
                                isRemote = false,
                                isCancelled = false,
                                rawDescription = "(Math) -> Chemistry"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(sub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 1 }
                assertNotNull(lesson)
                // С правильной логикой урок должен быть обновлён (найден по Math, обновлён на
                // Chemistry)
                assertEquals(LessonChangeType.MODIFIED, lesson!!.changeType)
                assertEquals("Chemistry", lesson.subject)
        }

        @Test
        fun testRemovedWithMovedTo() {
                // Урок удалён, но перемещён на другой период (Moved to period: 5)
                // Должен быть CANCELLED + isMoved + movedTo, а не просто CANCELLED
                val sub =
                        Substitution(
                                type = ChangeType.REMOVED,
                                period = "(1)",
                                subject = "Math",
                                subjectNew = null,
                                group = null,
                                teacherOld = null,
                                teacherNew = null,
                                roomOld = null,
                                roomNew = null,
                                roomChanged = false,
                                isRemote = false,
                                isMoved = true,
                                movedTo = "5",
                                isCancelled = false,
                                rawDescription = "Math - T1, Moved to period: 5"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(sub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 1 }
                assertNotNull(lesson)
                assertEquals(LessonChangeType.CANCELLED, lesson!!.changeType)
                assertTrue("Lesson should be marked as moved", lesson.isMoved)
                assertEquals("5", lesson.movedTo)
                assertEquals("Math", lesson.originalSubject)
        }

        @Test
        fun testAddedReplacesOccupiedSlot() {
                // Если слот занят другим предметом (например, уже отменённым),
                // добавленный урок должен его заменить
                val removeSub =
                        Substitution(
                                type = ChangeType.REMOVED,
                                period = "(1)",
                                subject = "Math",
                                isMoved = true,
                                movedTo = "5",
                                rawDescription = "Math - T1, Moved to period: 5"
                        )

                val addSub =
                        Substitution(
                                type = ChangeType.ADDED,
                                period = "1",
                                subject = "History",
                                teacherNew = "T3",
                                roomNew = "103",
                                rawDescription = "History - Added, Skolotājs: T3, Kabinets: 103"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(removeSub, addSub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 1 }
                assertNotNull("Lesson at period 1 should exist", lesson)
                // After REMOVED(movedTo) + ADDED, the slot should have the ADDED lesson
                assertEquals(LessonChangeType.ADDED, lesson!!.changeType)
                assertEquals("History", lesson.subject)
                assertEquals("T3", lesson.teachers.firstOrNull())
                assertEquals("103", lesson.classrooms.firstOrNull())
        }

        @Test
        fun testAddedToEmptySlot() {
                // Добавление на пустой слот (период 3 не существует в базе)
                val addSub =
                        Substitution(
                                type = ChangeType.ADDED,
                                period = "3",
                                subject = "History",
                                teacherNew = "T3",
                                roomNew = "103",
                                rawDescription = "History - Added"
                        )

                val daySubs = DaySubstitutions("2024-02-17", "Saturday", listOf(addSub))
                substitutions = SubstitutionData("7A", mapOf("2024-02-17" to daySubs))

                val result = ScheduleMerger.applySubstitutions(schedule, substitutions)
                val modifiedDay = result.days.find { it.date == "2024-02-17" }

                assertNotNull(modifiedDay)
                val lesson = modifiedDay!!.lessons.find { it.period == 3 }
                assertNotNull("Lesson at period 3 should be added", lesson)
                assertEquals(LessonChangeType.ADDED, lesson!!.changeType)
                assertEquals("History", lesson.subject)
                assertEquals("T3", lesson.teachers.firstOrNull())
        }
}
