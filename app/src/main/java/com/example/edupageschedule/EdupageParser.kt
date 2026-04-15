package com.example.edupageschedule

import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class EdupageParser(private val school: String) {

    private val baseUrl = "https://$school.edupage.org"
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

    suspend fun getSchedule(
            className: String,
            year: Int = Constants.DEFAULT_YEAR,
            daysAhead: Int = Constants.DEFAULT_DAYS_AHEAD
    ): Schedule? =
            withContext(Dispatchers.IO) {
                try {
                    val ttNum = getTimetableNum(year) ?: return@withContext null
                    val structure = getTimetableStructure(ttNum) ?: return@withContext null
                    val (classId, tables) =
                            findClass(structure, className) ?: return@withContext null
                    val timetableData =
                            getClassTimetable(ttNum, classId, year, daysAhead)
                                    ?: return@withContext null
                    parseSchedule(timetableData, tables)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

    private fun getTimetableNum(year: Int): String? {
        val url = "$baseUrl/timetable/server/ttviewer.js?__func=getTTViewerData"
        val json =
                JSONObject().apply {
                    put("__args", JSONArray().put(JSONObject.NULL).put(year))
                    put("__gsh", "00000000")
                }

        val response = makeRequest(url, json) ?: return null
        return response.getJSONObject("r").getJSONObject("regular").getString("default_num")
    }

    private fun getTimetableStructure(ttNum: String): JSONObject? {
        val url = "$baseUrl/timetable/server/regulartt.js?__func=regularttGetData"
        val json =
                JSONObject().apply {
                    put("__args", JSONArray().put(JSONObject.NULL).put(ttNum))
                    put("__gsh", "00000000")
                }
        return makeRequest(url, json)
    }

    private fun findClass(structure: JSONObject, className: String): Pair<String, JSONArray>? {
        val tables =
                structure.getJSONObject("r").getJSONObject("dbiAccessorRes").getJSONArray("tables")

        for (i in 0 until tables.length()) {
            val table = tables.getJSONObject(i)
            if (table.getString("id") == "classes") {
                val classes = table.getJSONArray("data_rows")
                for (j in 0 until classes.length()) {
                    val cls = classes.getJSONObject(j)
                    if (cls.getString("name").equals(className, ignoreCase = true)) {
                        return Pair(cls.getString("id"), tables)
                    }
                }
            }
        }
        return null
    }

    private fun getClassTimetable(
            ttNum: String,
            classId: String,
            year: Int,
            daysAhead: Int
    ): JSONObject? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        // Устанавливаем на понедельник текущей недели
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val dateFrom = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
        val dateTo = dateFormat.format(calendar.time)

        val url = "$baseUrl/timetable/server/currenttt.js?__func=curentttGetData"
        val params =
                JSONObject().apply {
                    put("year", year)
                    put("datefrom", dateFrom)
                    put("dateto", dateTo)
                    put("table", "classes")
                    put("id", classId)
                    put("showColors", true)
                    put("showIgroupsInClasses", true)
                    put("showOrig", true)
                    put("log_module", "CurrentTT")
                }

        val json =
                JSONObject().apply {
                    put("__args", JSONArray().put(JSONObject.NULL).put(params))
                    put("__gsh", "00000000")
                }
        return makeRequest(url, json)
    }

    private fun parseSchedule(timetableData: JSONObject, tables: JSONArray): Schedule {
        val subjects = mutableMapOf<String, Subject>()
        val teachers = mutableMapOf<String, Teacher>()
        val classrooms = mutableMapOf<String, Classroom>()
        val periods = mutableMapOf<Int, Period>()

        for (i in 0 until tables.length()) {
            val table = tables.getJSONObject(i)
            val tableId = table.getString("id")
            val rows = table.getJSONArray("data_rows")

            when (tableId) {
                "subjects" -> {
                    for (j in 0 until rows.length()) {
                        val row = rows.getJSONObject(j)
                        subjects[row.getString("id")] =
                                Subject(
                                        id = row.getString("id"),
                                        name = row.getString("name"),
                                        short = row.getString("short")
                                )
                    }
                }
                "teachers" -> {
                    for (j in 0 until rows.length()) {
                        val row = rows.getJSONObject(j)
                        teachers[row.getString("id")] =
                                Teacher(
                                        id = row.getString("id"),
                                        name = row.optString("name", ""),
                                        short = row.getString("short")
                                )
                    }
                }
                "classrooms" -> {
                    for (j in 0 until rows.length()) {
                        val row = rows.getJSONObject(j)
                        classrooms[row.getString("id")] =
                                Classroom(
                                        id = row.getString("id"),
                                        name = row.getString("name"),
                                        short = row.getString("short")
                                )
                    }
                }
                "periods" -> {
                    for (j in 0 until rows.length()) {
                        val row = rows.getJSONObject(j)
                        periods[row.getInt("period")] =
                                Period(
                                        period = row.getInt("period"),
                                        startTime = normalizeTime(row.getString("starttime")),
                                        endTime = normalizeTime(row.getString("endtime"))
                                )
                    }
                }
            }
        }

        val r = timetableData.getJSONObject("r")
        val lessons = r.optJSONArray("ttitems") ?: JSONArray()
        val daysMap = mutableMapOf<String, MutableList<Lesson>>()

        for (i in 0 until lessons.length()) {
            val lesson = lessons.getJSONObject(i)
            val isRemoved = lesson.optBoolean("removed", false)

            val date = lesson.getString("date").trim()
            val period = lesson.optInt("uniperiod", 1)
            val subjectId = lesson.optString("subjectid", "")
            val teacherIds = lesson.optJSONArray("teacherids") ?: JSONArray()
            val classroomIds = lesson.optJSONArray("classroomids") ?: JSONArray()
            val groupNames = lesson.optJSONArray("groupnames") ?: JSONArray()

            val duration = lesson.optInt("durationperiods", lesson.optInt("duration", 1))

            val periodInfo = periods[period]
            val time = periodInfo?.let { "${it.startTime}-${it.endTime}" } ?: ""

            if (Constants.DEBUG && isRemoved) {
                println(
                        "DEBUG EdupageParser: Lesson period=$period subject=${subjects[subjectId]?.name} is removed=true, adding as CANCELLED"
                )
            }

            val lessonObj =
                    Lesson(
                            period = period,
                            time = time,
                            subject = subjects[subjectId]?.name ?: "N/A",
                            subjectShort = subjects[subjectId]?.short ?: "N/A",
                            teachers =
                                    (0 until teacherIds.length()).map {
                                        teachers[teacherIds.getString(it)]?.short ?: "N/A"
                                    },
                            classrooms =
                                    (0 until classroomIds.length()).map {
                                        classrooms[classroomIds.getString(it)]?.short ?: "N/A"
                                    },
                            group =
                                    if (groupNames.length() > 0) {
                                        val groupName = groupNames.getString(0)
                                        // Извлекаем только цифру из названия группы (например, "1"
                                        // из "Grupa: 1")
                                        Regex("""(\d+)""").find(groupName)?.groupValues?.get(1)
                                    } else null,
                            changed = isRemoved || lesson.optBoolean("changed", false),
                            duration = duration,
                            changeType =
                                    if (isRemoved) LessonChangeType.CANCELLED
                                    else LessonChangeType.NONE
                    )

            // Если урок длится несколько периодов - разбиваем на отдельные уроки
            if (duration > 1) {
                for (j in 0 until duration) {
                    val currentPeriod = period + j
                    val currentPeriodInfo = periods[currentPeriod]
                    val currentTime =
                            currentPeriodInfo?.let { "${it.startTime}-${it.endTime}" } ?: time

                    val singleLesson =
                            lessonObj.copy(period = currentPeriod, time = currentTime, duration = 1)

                    daysMap.getOrPut(date) { mutableListOf() }.add(singleLesson)
                }
            } else {
                daysMap.getOrPut(date) { mutableListOf() }.add(lessonObj)
            }
        }

        val dayNames = Constants.DAY_NAMES
        val daysList = mutableListOf<Day>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        for ((date, lessonsList) in daysMap.toSortedMap()) {
            val calendar = Calendar.getInstance()
            calendar.time = dateFormat.parse(date) ?: continue
            val dayName = dayNames.getOrNull(calendar.get(Calendar.DAY_OF_WEEK) - 2) ?: "Diena"
            val dateFormatted = displayDateFormat.format(calendar.time)

            daysList.add(
                    Day(
                            date = date,
                            dateFormatted = dateFormatted,
                            dayName = dayName,
                            lessons = lessonsList.sortedBy { it.period }
                    )
            )
        }

        return Schedule(days = daysList)
    }

    private fun normalizeTime(time: String): String {
        return if (time.length == 4 && time.indexOf(':') == 1) {
            "0$time"
        } else {
            time
        }
    }

    private fun makeRequest(url: String, json: JSONObject): JSONObject? {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request =
                Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json; charset=UTF-8")
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Referer", "$baseUrl/")
                        .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body.string())
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
