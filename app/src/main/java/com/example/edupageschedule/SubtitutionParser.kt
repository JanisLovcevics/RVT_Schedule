package com.example.edupageschedule

import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*

class SubstitutionParser(private val school: String) {

    private val baseUrl = "https://$school.edupage.org"
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

    suspend fun getSubstitutions(className: String, daysAhead: Int = 7): SubstitutionData? =
            withContext(Dispatchers.IO) {
                try {
                    val allSubstitutions = mutableMapOf<String, DaySubstitutions>()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

                    for (i in 0 until daysAhead) {
                        val currentDate =
                                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }

                        val dateStr = dateFormat.format(currentDate.time)
                        val dateFormatted = displayDateFormat.format(currentDate.time)

                        val substitutions = fetchDaySubstitutions(dateStr, className)

                        if (substitutions.isNotEmpty()) {
                            val dayName = getDayName(currentDate.get(Calendar.DAY_OF_WEEK) - 2)
                            allSubstitutions[dateStr] =
                                    DaySubstitutions(
                                            date = dateFormatted,
                                            dayName = dayName,
                                            changes = substitutions
                                    )
                        }
                    }

                    if (allSubstitutions.isEmpty()) {
                        if (Constants.DEBUG) println("DEBUG: No substitutions found for any day")
                        return@withContext null
                    }

                    SubstitutionData(className = className.uppercase(), days = allSubstitutions)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

    private fun fetchDaySubstitutions(date: String, className: String): List<Substitution> {
        val url = "$baseUrl/substitution/?date=$date"
        if (Constants.DEBUG) println("DEBUG: Fetching URL: $url")

        val request = Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0").build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body.string()
                if (Constants.DEBUG) println("DEBUG: HTML fetched, length = ${html.length}")
                parseHtml(html, className)
            } else {
                if (Constants.DEBUG) println("DEBUG: Response not successful: ${response.code}")
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseHtml(html: String, className: String): List<Substitution> {
        val substitutions = mutableListOf<Substitution>()

        if (Constants.DEBUG) println("DEBUG parseHtml: Looking for class '$className'")

        // Убираем экранирование из HTML
        val cleanHtml = html.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t")

        // ИСПРАВЛЕННЫЙ ПАТТЕРН: учитываем "print-nobreak" и разные варианты пробелов
        val sectionPattern =
                """<div\s+class="section[^"]*">\s*<div\s+class="header">\s*<span[^>]*>([^<]+)</span>""".toRegex()
        val matches = sectionPattern.findAll(cleanHtml)

        var foundSection: String? = null
        var sectionStart = -1

        if (Constants.DEBUG) println("DEBUG: Searching for sections...")

        for (match in matches) {
            val headerText = match.groupValues[1].trim()
            println("DEBUG: Found section header: '$headerText'")

            if (headerText.equals(className, ignoreCase = true)) {
                if (Constants.DEBUG) println("DEBUG: ✅ FOUND MATCHING SECTION for '$className'")
                sectionStart = match.range.first

                // Находим конец секции (начало следующей секции или конец HTML)
                val nextSectionIndex = cleanHtml.indexOf("<div class=\"section", sectionStart + 10)
                val sectionEnd = if (nextSectionIndex > 0) nextSectionIndex else cleanHtml.length

                foundSection = cleanHtml.substring(sectionStart, sectionEnd)
                if (Constants.DEBUG) println("DEBUG: Section length = ${foundSection.length} chars")
                break
            }
        }

        if (foundSection == null) {
            if (Constants.DEBUG) println("DEBUG: ❌ Section for class '$className' NOT FOUND")
            return emptyList()
        }

        // ИСПРАВЛЕННЫЙ ПАТТЕРН ДЛЯ СТРОК: учитываем разные варианты пробелов
        val rowPattern =
                """<div\s+class="row\s+([^"]+)">\s*<div\s+class="period">\s*<span[^>]*>([^<]+)</span>\s*</div>\s*<div\s+class="info">\s*<span[^>]*>([^<]+)</span>""".toRegex()
        val rowMatches = rowPattern.findAll(foundSection)

        var rowCount = 0
        for (rowMatch in rowMatches) {
            rowCount++
            val changeClass = rowMatch.groupValues[1].trim()
            val period = rowMatch.groupValues[2].trim()
            val info = rowMatch.groupValues[3].trim()

            if (Constants.DEBUG)
                    println(
                            "DEBUG: Row $rowCount - class='$changeClass', period='$period', info='${info.take(50)}...'"
                    )

            val changeType =
                    when {
                        changeClass.contains("remove") -> ChangeType.REMOVED
                        changeClass.contains("add") -> ChangeType.ADDED
                        changeClass.contains("change") -> ChangeType.CHANGED
                        else -> ChangeType.UNKNOWN
                    }

            val details = parseChangeDetails(info)

            val substitution =
                    Substitution(
                            type = changeType,
                            period = period,
                            subject = details.subject,
                            subjectNew = details.subjectNew,
                            group = details.group,
                            teacherOld = details.teacherOld,
                            teacherNew = details.teacherNew,
                            roomOld = details.roomOld,
                            roomNew = details.roomNew,
                            roomChanged = details.roomChanged,
                            isRemote = details.isRemote,
                            isMoved = details.isMoved,
                            movedFrom = details.movedFrom,
                            movedTo = details.movedTo,
                            isCancelled = details.isCancelled,
                            rawDescription = info
                    )

            if (Constants.DEBUG)
                    println("DEBUG: ✅ Parsed: $changeType - $period - ${details.subject}")
            substitutions.add(substitution)
        }

        if (Constants.DEBUG)
                println("DEBUG parseHtml: Total substitutions found = ${substitutions.size}")
        return substitutions
    }

    private fun parseChangeDetails(info: String): ChangeDetails {
        var text =
                info.replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("➔", "->")
                        .replace("→", "->")

        var group: String? = null
        var subject: String? = null
        var subjectNew: String? = null
        var teacherOld: String? = null
        var teacherNew: String? = null
        var roomOld: String? = null
        var roomNew: String? = null
        var roomChanged = false
        var isRemote = false
        var isMoved = false
        var movedFrom: String? = null
        var movedTo: String? = null
        var isCancelled = false

        // Extract movedFrom
        val movedMatch =
                Regex(
                                """(?:Pārcelt[^\s]*|Moved)\s+(?:no|from)\s+([^,;]+)""",
                                RegexOption.IGNORE_CASE
                        )
                        .find(text)
        if (movedMatch != null) {
            val extracted = movedMatch.groupValues[1].trim()
            movedFrom = extracted.replace(Regex("""\s*[\(\)]\s*"""), "") // cleaner formatting
            isMoved = true
        }

        // Extract movedTo
        val movedToMatch =
                Regex(
                                """(?:Pārcelt[^\s]*|Moved)\s+(?:uz|to)\s+([^,;]+)""",
                                RegexOption.IGNORE_CASE
                        )
                        .find(text)
        if (movedToMatch != null) {
            val extracted = movedToMatch.groupValues[1].trim()
            movedTo = extracted.replace(Regex("""\s*[\(\)]\s*"""), "") // cleaner formatting
            isMoved = true
        }

        if (movedMatch == null && movedToMatch == null) {
            isMoved =
                    text.contains("Pārcelt", ignoreCase = true) ||
                            text.contains("Moved", ignoreCase = true)
        }

        // Парсинг группы (например "1:" в начале)
        val groupMatch = Regex("""^(\d+):\s*""").find(text)
        if (groupMatch != null) {
            group = groupMatch.groupValues[1]
            text = text.substring(groupMatch.range.last + 1).trim()
        }

        // Парсинг замены предмета: (Старый предмет) -> Новый предмет
        val subjectChangeMatch = Regex("""^\(([^)]+)\)\s*->\s*([^-]+?)(?:\s*-|$)""").find(text)
        if (subjectChangeMatch != null) {
            subject = subjectChangeMatch.groupValues[1].trim()
            subjectNew = subjectChangeMatch.groupValues[2].trim()
        } else {
            // Парсинг обычного предмета
            val subjectMatch = Regex("""^([^-,]+)(?:\s*[-;,]\s*|$)""").find(text)
            if (subjectMatch != null) {
                subject = subjectMatch.groupValues[1].trim()

                // Если после предмета есть текст, пробуем извлечь учителя
                if (subjectMatch.range.last < text.length - 1) {
                    val remaining = text.substring(subjectMatch.range.last + 1).trim()

                    // Паттерн: "Учитель, ..." или просто "Учитель" (до запятой или конца)
                    // Исключаем ключевые слова, чтобы не захватить их как имя учителя
                    val teacherSimpleMatch = Regex("""^([^,]+?)(?:\s*,|$)""").find(remaining)

                    if (teacherSimpleMatch != null) {
                        val potentialTeacher = teacherSimpleMatch.groupValues[1].trim()
                        // Проверяем, что это не ключевые слова
                        if (!potentialTeacher.contains("Atcelts", true) &&
                                        !potentialTeacher.contains("Attālināti", true) &&
                                        !potentialTeacher.contains("Skolot", true) &&
                                        !potentialTeacher.contains("Kabinet", true) &&
                                        !potentialTeacher.contains("Moved", true) &&
                                        !potentialTeacher.contains("Pārcelt", true)
                        ) {

                            teacherOld = potentialTeacher
                        }
                    }
                }
            }
        }

        // Отмена (только "Atcelts", "Attālināti" означает дистанционный урок)
        isCancelled = text.contains("Atcelts", ignoreCase = true)

        // Учитель (замена)
        val teacherSubstMatch = Regex("""Aizvietošana:\s*\(([^)]+)\)\s*->\s*([^,]+)""").find(text)
        if (teacherSubstMatch != null) {
            teacherOld = teacherSubstMatch.groupValues[1].trim()
            teacherNew = teacherSubstMatch.groupValues[2].trim()
        } else {
            val teacherMatch = Regex("""Skolot[aā]js:\s*([^,]+)""").find(text)
            if (teacherMatch != null) {
                teacherNew = teacherMatch.groupValues[1].trim()
            }
        }

        // Кабинет (замена)
        val roomChangeMatch = Regex("""Kabineta nomaiņa:\s*\((.+)\)\s*->\s*([^,;]+)""").find(text)
        if (roomChangeMatch != null) {
            roomOld = roomChangeMatch.groupValues[1].trim()
            roomNew = roomChangeMatch.groupValues[2].trim()
            roomChanged = true
        } else {
            // Ищем кабинет после "+" или просто "Kabinets:"
            val roomPlusMatch = Regex("""\+\s*([^\s,]+(?:\s+[^\s,]+)*)""").find(text)
            if (roomPlusMatch != null) {
                roomNew = roomPlusMatch.groupValues[1].trim()
            } else {
                val roomMatch = Regex("""Kabinets?:\s*([^,]+)""").find(text)
                if (roomMatch != null) {
                    var roomName = roomMatch.groupValues[1].trim()
                    // If the room ends with ')' but doesn't have an opening '(', it's probably
                    // a closing parenthesis from an outer wrapper like (Kabinets: 123)
                    if (roomName.endsWith(")") && !roomName.contains("(")) {
                        roomName = roomName.dropLast(1).trim()
                    }
                    roomNew = roomName
                }
            }
        }

        isRemote = text.contains("Attālināti", ignoreCase = true)

        return ChangeDetails(
                subject = subject,
                subjectNew = subjectNew,
                group = group,
                teacherOld = teacherOld,
                teacherNew = teacherNew,
                roomOld = roomOld,
                roomNew = roomNew,
                roomChanged = roomChanged,
                isRemote = isRemote,
                isMoved = isMoved,
                movedFrom = movedFrom,
                movedTo = movedTo,
                isCancelled = isCancelled
        )
    }

    private fun getDayName(weekday: Int): String {
        return Constants.DAY_NAMES.getOrNull(weekday) ?: "Diena"
    }
}

data class SubstitutionData(val className: String, val days: Map<String, DaySubstitutions>)

data class DaySubstitutions(val date: String, val dayName: String, val changes: List<Substitution>)

data class Substitution(
        val type: ChangeType,
        val period: String,
        val subject: String? = null,
        val subjectNew: String? = null,
        val group: String? = null,
        val teacherOld: String? = null,
        val teacherNew: String? = null,
        val roomOld: String? = null,
        val roomNew: String? = null,
        val roomChanged: Boolean = false,
        val isRemote: Boolean = false,
        val isMoved: Boolean = false,
        val movedFrom: String? = null,
        val movedTo: String? = null,
        val isCancelled: Boolean = false,
        val rawDescription: String = ""
)

data class ChangeDetails(
        val subject: String? = null,
        val subjectNew: String? = null,
        val group: String? = null,
        val teacherOld: String? = null,
        val teacherNew: String? = null,
        val roomOld: String? = null,
        val roomNew: String? = null,
        val roomChanged: Boolean = false,
        val isRemote: Boolean = false,
        val isMoved: Boolean = false,
        val movedFrom: String? = null,
        val movedTo: String? = null,
        val isCancelled: Boolean = false
)

enum class ChangeType {
    ADDED,
    REMOVED,
    CHANGED,
    UNKNOWN
}
