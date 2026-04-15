package com.example.edupageschedule

object ScheduleMerger {

    fun applySubstitutions(schedule: Schedule, substitutions: SubstitutionData?): Schedule {
        // Проверка наличия расписания
        if (schedule.days.isEmpty()) {
            if (Constants.DEBUG)
                    println("DEBUG applySubstitutions: Schedule is empty, nothing to process")
            return schedule
        }

        // Проверка наличия уроков в расписании
        val hasLessons = schedule.days.any { it.lessons.isNotEmpty() }
        if (!hasLessons) {
            if (Constants.DEBUG)
                    println("DEBUG applySubstitutions: Schedule has no lessons, nothing to process")
            return schedule
        }

        // Проверка наличия замен
        if (substitutions == null || substitutions.days.isEmpty()) {
            if (Constants.DEBUG) println("DEBUG applySubstitutions: No substitutions to apply")
            return schedule
        }

        // Проверка наличия изменений в заменах
        val hasChanges = substitutions.days.values.any { it.changes.isNotEmpty() }
        if (!hasChanges) {
            if (Constants.DEBUG)
                    println("DEBUG applySubstitutions: Substitutions have no changes to apply")
            return schedule
        }

        if (Constants.DEBUG)
                println(
                        "DEBUG applySubstitutions: Applying substitutions for ${substitutions.days.size} days"
                )

        val updatedDays =
                schedule.days.map { day ->
                    val daySubstitutions = substitutions.days[day.date]

                    if (daySubstitutions != null) {
                        if (Constants.DEBUG)
                                println(
                                        "DEBUG: Processing day ${day.date} with ${daySubstitutions.changes.size} changes"
                                )
                        val updatedLessons = applySubstitutionsToDay(day.lessons, daySubstitutions)
                        day.copy(lessons = updatedLessons)
                    } else {
                        if (Constants.DEBUG) println("DEBUG: No substitutions for day ${day.date}")
                        day
                    }
                }

        return schedule.copy(days = updatedDays)
    }

    private fun applySubstitutionsToDay(
            lessons: List<Lesson>,
            substitutions: DaySubstitutions
    ): List<Lesson> {
        // Проверка наличия уроков
        if (lessons.isEmpty()) {
            if (Constants.DEBUG)
                    println("DEBUG applySubstitutionsToDay: No lessons in day, skipping")
            return lessons
        }

        // Проверка наличия изменений
        if (substitutions.changes.isEmpty()) {
            if (Constants.DEBUG) println("DEBUG applySubstitutionsToDay: No changes to apply")
            return lessons
        }

        val updatedLessons = lessons.toMutableList()

        if (Constants.DEBUG)
                println(
                        "DEBUG applySubstitutionsToDay: ${substitutions.changes.size} changes to apply"
                )

        substitutions.changes.forEach { sub ->
            if (Constants.DEBUG)
                    println(
                            "DEBUG change: type=${sub.type}, period='${sub.period}', subject=${sub.subject}, subjectNew=${sub.subjectNew}, group=${sub.group}"
                    )

            when (sub.type) {
                ChangeType.REMOVED -> {
                    handleRemovedLesson(updatedLessons, sub)
                }
                ChangeType.CHANGED -> {
                    handleChangedLesson(updatedLessons, sub)
                }
                ChangeType.ADDED -> {
                    handleAddedLesson(updatedLessons, sub)
                }
                else -> {
                    if (Constants.DEBUG) println("DEBUG: Unknown change type: ${sub.type}")
                }
            }
        }

        return updatedLessons.sortedBy { it.period }
    }

    private fun handleRemovedLesson(lessons: MutableList<Lesson>, sub: Substitution) {
        val periods = extractPeriodNumbers(sub.period)
        if (Constants.DEBUG)
                println("DEBUG handleRemovedLesson: periods=$periods, subject=${sub.subject}, isMoved=${sub.isMoved}, movedTo=${sub.movedTo}")

        if (periods.isEmpty()) return

        lessons.forEachIndexed { index, lesson ->
            if (periods.contains(lesson.period) && matchesSubjectAndGroup(lesson, sub)) {
                if (sub.isMoved && sub.movedTo != null) {
                    // Урок перемещён на другой период — помечаем как MODIFIED+isMoved,
                    // а не CANCELLED, чтобы UI показывал "Pārcelta uz X. stundu"
                    if (Constants.DEBUG) println("DEBUG: Marking lesson ${lesson.period} as MOVED TO ${sub.movedTo}")
                    lessons[index] =
                            lesson.copy(
                                    changed = true,
                                    changeType = LessonChangeType.CANCELLED,
                                    isMoved = true,
                                    movedTo = sub.movedTo,
                                    originalSubject = lesson.subject,
                                    originalTeachers = lesson.teachers,
                                    originalClassrooms = lesson.classrooms
                            )
                } else {
                    if (Constants.DEBUG) println("DEBUG: Marking lesson ${lesson.period} as CANCELLED")
                    lessons[index] =
                            lesson.copy(
                                    changed = true,
                                    changeType = LessonChangeType.CANCELLED,
                                    originalSubject = lesson.subject,
                                    originalTeachers = lesson.teachers,
                                    originalClassrooms = lesson.classrooms
                            )
                }
            }
        }
    }

    private fun handleChangedLesson(lessons: MutableList<Lesson>, sub: Substitution) {
        val periods = extractPeriodNumbers(sub.period)
        if (Constants.DEBUG)
                println(
                        "DEBUG handleChangedLesson: periods=$periods, subject=${sub.subject}, subjectNew=${sub.subjectNew}"
                )

        if (periods.isEmpty()) return

        // Если урок отменён, помечаем как CANCELLED
        if (sub.isCancelled) {
            lessons.forEachIndexed { index, lesson ->
                // Ищем по старому предмету (sub.subject), т.к. в базовом расписании записан старый
                // предмет
                if (periods.contains(lesson.period) &&
                                matchesSubject(lesson, sub.subject, sub.group) &&
                                (sub.teacherOld == null || matchesTeacher(lesson, sub.teacherOld))
                ) {
                    if (Constants.DEBUG)
                            println("DEBUG: Marking lesson ${lesson.period} as CANCELLED")
                    lessons[index] =
                            lesson.copy(
                                    changed = true,
                                    changeType = LessonChangeType.CANCELLED,
                                    originalSubject = lesson.subject,
                                    originalTeachers = lesson.teachers,
                                    originalClassrooms = lesson.classrooms
                            )
                }
            }
            return
        }

        // Ищем всегда по старому предмету (sub.subject), т.к. в базовом расписании записан старый
        // предмет
        // subjectNew используется только для обновления самого урока (если предмет заменяется)
        val searchSubject = sub.subject

        var matched = false

        lessons.forEachIndexed { index, lesson ->
            if (periods.contains(lesson.period) &&
                            matchesSubject(lesson, searchSubject, sub.group) &&
                            (sub.teacherOld == null || matchesTeacher(lesson, sub.teacherOld))
            ) {
                matched = true
                if (Constants.DEBUG) println("DEBUG: Updating lesson ${lesson.period}")

                var updated = lesson.copy(isRemote = sub.isRemote)

                // Обновляем название предмета, если он заменяется
                if (sub.subjectNew != null) {
                    updated =
                            updated.copy(
                                    subject = sub.subjectNew,
                                    subjectShort = sub.subjectNew.take(10)
                            )
                }

                // Обновляем учителя
                if (sub.teacherNew != null) {
                    updated = updated.copy(teachers = listOf(sub.teacherNew))
                }

                // Обновляем кабинет
                if (sub.roomNew != null) {
                    updated = updated.copy(classrooms = listOf(sub.roomNew))
                }

                // Проверяем, изменилось ли что-то на самом деле
                val subjectChanged = updated.subject != lesson.subject
                val teacherChanged = !sameTeachersIgnoringOrder(updated.teachers, lesson.teachers)
                val roomChanged = updated.classrooms != lesson.classrooms
                val remoteChanged = updated.isRemote != lesson.isRemote
                val isMoved = sub.isMoved

                if (subjectChanged || teacherChanged || roomChanged || remoteChanged || isMoved) {
                    updated =
                            updated.copy(
                                    changed = true,
                                    changeType = LessonChangeType.MODIFIED,
                                    isMoved = isMoved,
                                    movedFrom = sub.movedFrom,
                                    originalSubject = sub.rawDescription,
                                    originalTeachers = lesson.teachers,
                                    originalClassrooms = if (sub.roomOld != null) listOf(sub.roomOld) else lesson.classrooms
                            )
                    if (Constants.DEBUG) println("DEBUG: Lesson ${lesson.period} marked as MODIFIED")
                } else {
                    if (Constants.DEBUG)
                            println("DEBUG: Lesson ${lesson.period} unchanged, skipping MODIFIED mark")
                }

                lessons[index] = updated
            }
        }

        // Fallback: если не нашли урок по старому предмету (сценарий перемещения урока или нового урока),
        // заменяем существующий урок на целевом периоде новыми данными
        val fallbackSubject = sub.subjectNew ?: sub.subject
        if (!matched && fallbackSubject != null) {
            if (Constants.DEBUG)
                    println("DEBUG: No match by subject, trying fallback for moved/added lesson")
            for (period in periods) {
                val idx = lessons.indexOfFirst { it.period == period }
                if (idx >= 0) {
                    val lesson = lessons[idx]
                    if (Constants.DEBUG)
                            println("DEBUG: Fallback replacing lesson at period $period (was '${lesson.subject}')")
                    lessons[idx] =
                            lesson.copy(
                                    subject = fallbackSubject,
                                    subjectShort = fallbackSubject.take(10),
                                    teachers =
                                            if (sub.teacherNew != null) listOf(sub.teacherNew)
                                            else lesson.teachers,
                                    classrooms =
                                            if (sub.roomNew != null) listOf(sub.roomNew)
                                            else lesson.classrooms,
                                    isRemote = sub.isRemote,
                                    isMoved = sub.isMoved,
                                    changed = true,
                                    changeType = LessonChangeType.MODIFIED,
                                    // Используем данные из базового расписания или удаления
                                    originalSubject = lesson.originalSubject ?: lesson.subject,
                                    originalTeachers = lesson.originalTeachers ?: lesson.teachers,
                                    originalClassrooms = lesson.originalClassrooms ?: lesson.classrooms,
                                    movedTo = lesson.movedTo // Сохраняем информацию о том, что старый урок был перенесен
                            )
                } else {
                    if (Constants.DEBUG) println("DEBUG: Fallback adding new lesson at period $period")
                    lessons.add(
                            Lesson(
                                    period = period,
                                    time = "",
                                    subject = fallbackSubject,
                                    subjectShort = fallbackSubject.take(10),
                                    teachers = listOfNotNull(sub.teacherNew ?: sub.teacherOld),
                                    classrooms = listOfNotNull(sub.roomNew ?: sub.roomOld),
                                    group = sub.group,
                                    changed = true,
                                    duration = 1,
                                    changeType = LessonChangeType.MODIFIED,
                                    isRemote = sub.isRemote,
                                    isMoved = sub.isMoved,
                                    movedFrom = sub.movedFrom,
                                    movedTo = sub.movedTo,
                                    originalSubject = sub.rawDescription
                            )
                    )
                }
            }
        }
    }

    private fun handleAddedLesson(lessons: MutableList<Lesson>, sub: Substitution) {
        val periods = extractPeriodNumbers(sub.period)
        if (Constants.DEBUG)
                println("DEBUG handleAddedLesson: periods=$periods, subject=${sub.subject}")

        if (periods.isEmpty() || sub.subject == null) return

        val exactMatchExists =
                lessons.any { lesson ->
                    periods.contains(lesson.period) && matchesSubject(lesson, sub.subject, sub.group)
                }

        if (exactMatchExists) {
            if (Constants.DEBUG) println("DEBUG: Added lesson already exists (exact match). Treating as modified.")
            handleChangedLesson(lessons, sub)
            return
        }

        periods.forEach { period ->
            // Ищем существующий урок на этом периоде с той же группой
            val existingIdx = lessons.indexOfFirst { it.period == period && matchesGroup(it, sub) }

            if (existingIdx >= 0) {
                val existing = lessons[existingIdx]
                // Слот занят другим предметом — заменяем его добавленным уроком.
                // Это происходит, когда оригинальный урок отменён/перемещён
                // и на его место ставится новый.
                if (Constants.DEBUG)
                        println("DEBUG: Replacing existing lesson '${existing.subject}' at period $period with ADDED '${sub.subject}'")
                lessons[existingIdx] =
                        existing.copy(
                                subject = sub.subject,
                                subjectShort = sub.subject.take(10),
                                teachers = listOfNotNull(sub.teacherNew ?: sub.teacherOld),
                                classrooms = listOfNotNull(sub.roomNew ?: sub.roomOld),
                                group = sub.group ?: existing.group,
                                changed = true,
                                changeType = if (existing.subject == "N/A" && existing.originalSubject == null) LessonChangeType.ADDED else LessonChangeType.MODIFIED,
                                isMoved = sub.isMoved || existing.isMoved, // Если старый урок перенесен, оставляем флаг
                                movedFrom = sub.movedFrom ?: existing.movedFrom,
                                movedTo = sub.movedTo ?: existing.movedTo,
                                // Сохраняем исходные данные заменяемого урока
                                originalSubject = existing.originalSubject ?: existing.subject,
                                originalTeachers = existing.originalTeachers ?: existing.teachers,
                                originalClassrooms = existing.originalClassrooms ?: existing.classrooms
                        )
            } else {
                // Слот пустой — добавляем
                val newLesson =
                        Lesson(
                                period = period,
                                time = "",
                                subject = sub.subject,
                                subjectShort = sub.subject.take(10),
                                teachers = listOfNotNull(sub.teacherNew ?: sub.teacherOld),
                                classrooms = listOfNotNull(sub.roomNew ?: sub.roomOld),
                                group = sub.group,
                                changed = true,
                                duration = 1,
                                changeType = LessonChangeType.ADDED,
                                isMoved = sub.isMoved,
                                movedFrom = sub.movedFrom,
                                originalSubject = sub.rawDescription
                        )

                if (Constants.DEBUG) println("DEBUG: Adding new lesson for period $period")
                lessons.add(newLesson)
            }
        }
    }

    private fun extractPeriodNumbers(period: String): List<Int> {
        val cleaned = period.replace("(", "").replace(")", "").trim()

        val rangeMatch = Regex("""(\d+)\s*-\s*(\d+)""").find(cleaned)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: return emptyList()
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: return emptyList()
            return (start..end).toList()
        }

        val singleMatch = Regex("""(\d+)""").find(cleaned)
        if (singleMatch != null) {
            val num = singleMatch.groupValues[1].toIntOrNull()
            if (num != null) return listOf(num)
        }

        return emptyList()
    }

    private fun matchesSubjectAndGroup(lesson: Lesson, sub: Substitution): Boolean {
        if (!matchesGroup(lesson, sub)) return false

        // Если указан "старый" учитель (чей урок отменяется/заменяется), проверяем его
        if (sub.teacherOld != null && !matchesTeacher(lesson, sub.teacherOld)) {
            if (Constants.DEBUG)
                    println(
                            "DEBUG: Teacher mismatch! Lesson teacher='${lesson.teachers}', sub teacher='${sub.teacherOld}'"
                    )
            return false
        }

        if (sub.subject == null) return true

        val lessonSubject = lesson.subject.lowercase()
        val subSubject = sub.subject.lowercase()

        val cleanLessonSubject =
                lessonSubject
                        .replace(Regex("""\s*\(\d+/\d+\)"""), "")
                        .replace(Regex("""\s*✖\s*atcelts"""), "")
                        .trim()

        val matches =
                cleanLessonSubject.contains(subSubject) ||
                        subSubject.contains(cleanLessonSubject) ||
                        cleanLessonSubject.startsWith(subSubject) ||
                        subSubject.startsWith(cleanLessonSubject)

        if (Constants.DEBUG)
                println(
                        "DEBUG matchesSubject: lesson='$cleanLessonSubject', sub='$subSubject', matches=$matches"
                )
        return matches
    }

    private fun matchesTeacher(lesson: Lesson, teacherName: String): Boolean {
        if (lesson.teachers.isEmpty())
                return true // Если у урока нет учителя, считаем что совпадает (или наоборот?)
        // Лучше считать что совпадает только если имя содержится

        val normalize = { s: String -> s.lowercase().replace(".", "").trim() }
        val search = normalize(teacherName)

        return lesson.teachers.any { lessonTeacher ->
            val normLessonTeacher = normalize(lessonTeacher)
            normLessonTeacher.contains(search) || search.contains(normLessonTeacher)
        }
    }

    private fun matchesSubject(lesson: Lesson, subject: String?, group: String?): Boolean {
        if (!matchesGroup(lesson, group)) return false
        if (subject == null) return true

        val lessonSubject = lesson.subject.lowercase()
        val subSubject = subject.lowercase()

        val cleanLessonSubject =
                lessonSubject
                        .replace(Regex("""\s*\(\d+/\d+\)"""), "")
                        .replace(Regex("""\s*✖\s*atcelts"""), "")
                        .trim()

        val matches =
                cleanLessonSubject.contains(subSubject) ||
                        subSubject.contains(cleanLessonSubject) ||
                        cleanLessonSubject.startsWith(subSubject) ||
                        subSubject.startsWith(cleanLessonSubject)

        if (Constants.DEBUG)
                println(
                        "DEBUG matchesSubject: lesson='$cleanLessonSubject', sub='$subSubject', matches=$matches"
                )
        return matches
    }

    private fun matchesGroup(lesson: Lesson, sub: Substitution): Boolean {
        return matchesGroup(lesson, sub.group)
    }

    private fun matchesGroup(lesson: Lesson, group: String?): Boolean {
        if (group == null) return true
        if (lesson.group == null) return false
        return lesson.group == group
    }

    /** Сравнивает списки учителей, игнорируя порядок слов в именах ("Motivāne Aina" == "Aina Motivāne") */
    private fun sameTeachersIgnoringOrder(a: List<String>, b: List<String>): Boolean {
        if (a.size != b.size) return false
        val normalize = { name: String -> name.lowercase().split(" ").sorted().joinToString(" ") }
        val aNorm = a.map(normalize).sorted()
        val bNorm = b.map(normalize).sorted()
        return aNorm == bNorm
    }

    fun getChangesCount(day: Day, substitutions: SubstitutionData?): Int {
        if (substitutions == null) return 0
        return substitutions.days[day.date]?.changes?.size ?: 0
    }

    fun hasChanges(day: Day, substitutions: SubstitutionData?): Boolean {
        return getChangesCount(day, substitutions) > 0
    }
}
