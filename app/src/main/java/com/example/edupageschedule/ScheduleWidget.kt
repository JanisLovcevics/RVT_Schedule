package com.example.edupageschedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class ScheduleWidget : AppWidgetProvider() {

        companion object {
                const val ACTION_AUTO_UPDATE = "com.example.edupageschedule.action.AUTO_UPDATE"
        }

        override fun onUpdate(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetIds: IntArray
        ) {
                for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                }
                scheduleNextUpdate(context)
        }

        override fun onReceive(context: Context, intent: Intent) {
                super.onReceive(context, intent)
                if (intent.action == ACTION_AUTO_UPDATE) {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisAppWidget = ComponentName(context, ScheduleWidget::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
                        for (appWidgetId in appWidgetIds) {
                                updateAppWidget(context, appWidgetManager, appWidgetId)
                        }
                        scheduleNextUpdate(context)
                }
        }

        override fun onEnabled(context: Context) {
                scheduleNextUpdate(context)
        }

        override fun onDisabled(context: Context) {
                cancelUpdate(context)
        }

        private fun scheduleNextUpdate(context: Context) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent =
                        Intent(context, ScheduleWidget::class.java).apply {
                                action = ACTION_AUTO_UPDATE
                        }
                val pendingIntent =
                        PendingIntent.getBroadcast(
                                context,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                val now = System.currentTimeMillis()
                val nextMinute = now + 60000 - (now % 60000) // Start of next minute

                // Используем setExactAndAllowWhileIdle для надежного срабатывания даже в режиме
                // Doze
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        nextMinute,
                                        pendingIntent
                                )
                        } else {
                                // Если нет разрешения, используем setAndAllowWhileIdle (менее
                                // точный, но лучше чем
                                // set)
                                alarmManager.setAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        nextMinute,
                                        pendingIntent
                                )
                        }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                nextMinute,
                                pendingIntent
                        )
                } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextMinute, pendingIntent)
                }
        }

        private fun cancelUpdate(context: Context) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent =
                        Intent(context, ScheduleWidget::class.java).apply {
                                action = ACTION_AUTO_UPDATE
                        }
                val pendingIntent =
                        PendingIntent.getBroadcast(
                                context,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                alarmManager.cancel(pendingIntent)
        }
}

internal fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val schedule = ScheduleStorage.loadSchedule(context)
        val substitutions = ScheduleStorage.loadSubstitutions(context)

        val finalSchedule =
                if (schedule != null && substitutions != null) {
                        ScheduleMerger.applySubstitutions(schedule, substitutions)
                } else {
                        schedule
                }

        if (finalSchedule != null) {
                val widgetData = getWidgetData(finalSchedule)

                if (widgetData != null) {
                        // Устанавливаем дату следующего дня
                        if (widgetData.nextDayDate.isNotEmpty()) {
                                views.setTextViewText(
                                        R.id.widget_next_day_date,
                                        widgetData.nextDayDate
                                )
                                views.setViewVisibility(
                                        R.id.widget_next_day_date,
                                        android.view.View.VISIBLE
                                )
                        } else {
                                views.setViewVisibility(
                                        R.id.widget_next_day_date,
                                        android.view.View.GONE
                                )
                        }

                        if (widgetData.hasGroups) {
                                // Отображаем с группами
                                // Текущий урок
                                if (widgetData.currentLessonG1.isNotEmpty() ||
                                                widgetData.currentLessonG2.isNotEmpty()
                                ) {

                                        views.setViewVisibility(
                                                R.id.widget_current_block,
                                                android.view.View.VISIBLE
                                        )

                                        if (widgetData.nextDayDate.isNotEmpty()) {
                                                views.setTextViewText(
                                                        R.id.widget_title,
                                                        widgetData.nextDayDate
                                                )
                                                // Стиль "Следующий урок" (Оранжевый)
                                                views.setInt(
                                                        R.id.widget_current_block,
                                                        "setBackgroundResource",
                                                        R.drawable.widget_next_bg
                                                )
                                                val orangeColor =
                                                        android.graphics.Color.parseColor("#E65100")
                                                views.setTextColor(R.id.widget_title, orangeColor)
                                                views.setTextColor(
                                                        R.id.widget_current_group1_label,
                                                        orangeColor
                                                )
                                                views.setTextColor(
                                                        R.id.widget_current_group2_label,
                                                        orangeColor
                                                )
                                        } else {
                                                views.setTextViewText(
                                                        R.id.widget_title,
                                                        "Pašreizējā stunda"
                                                )
                                                // Стиль "Текущий урок" (Синий)
                                                views.setInt(
                                                        R.id.widget_current_block,
                                                        "setBackgroundResource",
                                                        R.drawable.widget_current_bg
                                                )
                                                val blueColor =
                                                        android.graphics.Color.parseColor("#1565C0")
                                                views.setTextColor(R.id.widget_title, blueColor)
                                                views.setTextColor(
                                                        R.id.widget_current_group1_label,
                                                        blueColor
                                                )
                                                views.setTextColor(
                                                        R.id.widget_current_group2_label,
                                                        blueColor
                                                )
                                        }

                                        // Проверяем, является ли урок общим
                                        val isCommonLesson = widgetData.isCurrentCommonLesson

                                        if (isCommonLesson) {
                                                // Общий урок для всего класса - показываем в блоке
                                                // без групп
                                                views.setViewVisibility(
                                                        R.id.widget_current_groups_container,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_current_no_group_block,
                                                        android.view.View.VISIBLE
                                                )
                                                views.setTextViewText(
                                                        R.id.widget_current_lesson,
                                                        widgetData.currentLessonG1
                                                )
                                                views.setTextViewText(
                                                        R.id.widget_current_time,
                                                        widgetData.currentCommonTime
                                                )
                                        } else {
                                                // Уроки с группами - показываем контейнер групп
                                                views.setViewVisibility(
                                                        R.id.widget_current_no_group_block,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_current_groups_container,
                                                        android.view.View.VISIBLE
                                                )

                                                // Группа 1
                                                if (widgetData.currentLessonG1.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group1_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_lesson_g1,
                                                                widgetData.currentLessonG1
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_time_g1,
                                                                widgetData.currentTimeG1
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group1_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // Группа 2
                                                if (widgetData.currentLessonG2.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group2_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_lesson_g2,
                                                                widgetData.currentLessonG2
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_time_g2,
                                                                widgetData.currentTimeG2
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group2_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // Общее время
                                                if (widgetData.currentCommonTime.isNotEmpty()) {
                                                        views.setTextViewText(
                                                                R.id.widget_current_common_time,
                                                                widgetData.currentCommonTime
                                                        )
                                                        views.setViewVisibility(
                                                                R.id.widget_current_common_time,
                                                                android.view.View.VISIBLE
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_common_time,
                                                                android.view.View.GONE
                                                        )
                                                }
                                        }
                                } else {
                                        views.setViewVisibility(
                                                R.id.widget_current_block,
                                                android.view.View.GONE
                                        )
                                }

                                // Следующий урок
                                if (widgetData.nextLessonG1.isNotEmpty() ||
                                                widgetData.nextLessonG2.isNotEmpty()
                                ) {
                                        views.setViewVisibility(
                                                R.id.widget_next_block,
                                                android.view.View.VISIBLE
                                        )

                                        // Проверяем, является ли урок общим (без префикса группы)
                                        val isCommonNextLesson = widgetData.isNextCommonLesson

                                        if (isCommonNextLesson) {
                                                // Общий урок для всего класса - показываем в блоке
                                                // без групп
                                                views.setViewVisibility(
                                                        R.id.widget_next_groups_container,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_next_common_time,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_next_no_group_block,
                                                        android.view.View.VISIBLE
                                                )
                                                views.setTextViewText(
                                                        R.id.widget_next_lesson,
                                                        widgetData.nextLessonG1.trim()
                                                )
                                                views.setTextViewText(
                                                        R.id.widget_next_time,
                                                        widgetData.nextCommonTime
                                                )
                                        } else {
                                                // Уроки с группами - показываем контейнер групп
                                                views.setViewVisibility(
                                                        R.id.widget_next_no_group_block,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_next_groups_container,
                                                        android.view.View.VISIBLE
                                                )

                                                // Группа 1
                                                if (widgetData.nextLessonG1.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group1_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_lesson_g1,
                                                                widgetData.nextLessonG1.trim()
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_time_g1,
                                                                widgetData.nextTimeG1
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group1_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // Группа 2
                                                if (widgetData.nextLessonG2.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group2_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_lesson_g2,
                                                                widgetData.nextLessonG2.trim()
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_time_g2,
                                                                widgetData.nextTimeG2
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group2_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // Общее время
                                                if (widgetData.nextCommonTime.isNotEmpty()) {
                                                        views.setTextViewText(
                                                                R.id.widget_next_common_time,
                                                                widgetData.nextCommonTime
                                                        )
                                                        views.setViewVisibility(
                                                                R.id.widget_next_common_time,
                                                                android.view.View.VISIBLE
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_common_time,
                                                                android.view.View.GONE
                                                        )
                                                }
                                        }
                                } else {
                                        views.setViewVisibility(
                                                R.id.widget_next_block,
                                                android.view.View.GONE
                                        )
                                }
                        } else {
                                // Отображаем без групп (старый формат для совместимости)
                                views.setViewVisibility(
                                        R.id.widget_current_group1_block,
                                        android.view.View.GONE
                                )
                                views.setViewVisibility(
                                        R.id.widget_current_group2_block,
                                        android.view.View.GONE
                                )
                                views.setViewVisibility(
                                        R.id.widget_next_group1_block,
                                        android.view.View.GONE
                                )
                                views.setViewVisibility(
                                        R.id.widget_next_group2_block,
                                        android.view.View.GONE
                                )

                                // Текущий урок
                                if (widgetData.currentLessonG1.isNotEmpty()) {
                                        views.setViewVisibility(
                                                R.id.widget_current_block,
                                                android.view.View.VISIBLE
                                        )
                                        views.setViewVisibility(
                                                R.id.widget_current_no_group_block,
                                                android.view.View.VISIBLE
                                        )
                                        views.setTextViewText(
                                                R.id.widget_current_lesson,
                                                widgetData.currentLessonG1
                                        )
                                        views.setTextViewText(
                                                R.id.widget_current_time,
                                                widgetData.currentTimeG1
                                        )
                                } else {
                                        views.setViewVisibility(
                                                R.id.widget_current_block,
                                                android.view.View.GONE
                                        )
                                }

                                // Следующий урок
                                if (widgetData.nextLessonG1.isNotEmpty()) {
                                        views.setViewVisibility(
                                                R.id.widget_next_block,
                                                android.view.View.VISIBLE
                                        )
                                        views.setViewVisibility(
                                                R.id.widget_next_no_group_block,
                                                android.view.View.VISIBLE
                                        )
                                        views.setTextViewText(
                                                R.id.widget_next_lesson,
                                                widgetData.nextLessonG1
                                        )
                                        views.setTextViewText(
                                                R.id.widget_next_time,
                                                widgetData.nextTimeG1
                                        )
                                } else {
                                        views.setViewVisibility(
                                                R.id.widget_next_block,
                                                android.view.View.GONE
                                        )
                                }
                        }
                } else {
                        // Нет данных
                        views.setViewVisibility(
                                R.id.widget_current_group1_block,
                                android.view.View.GONE
                        )
                        views.setViewVisibility(
                                R.id.widget_current_group2_block,
                                android.view.View.GONE
                        )
                        views.setViewVisibility(
                                R.id.widget_current_no_group_block,
                                android.view.View.VISIBLE
                        )
                        views.setTextViewText(R.id.widget_current_lesson, "Nav datu")
                        views.setTextViewText(R.id.widget_current_time, "Atveriet aplikāciju")
                        views.setViewVisibility(
                                R.id.widget_current_block,
                                android.view.View.VISIBLE
                        )
                        views.setViewVisibility(R.id.widget_next_block, android.view.View.GONE)
                }
        } else {
                views.setViewVisibility(R.id.widget_current_group1_block, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_current_group2_block, android.view.View.GONE)
                views.setViewVisibility(
                        R.id.widget_current_no_group_block,
                        android.view.View.VISIBLE
                )
                views.setTextViewText(R.id.widget_current_lesson, "Nav datu")
                views.setTextViewText(R.id.widget_current_time, "Atveriet aplikāciju")
                views.setViewVisibility(R.id.widget_current_block, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_next_block, android.view.View.GONE)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun getWidgetData(schedule: Schedule): WidgetData? {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(calendar.time)
        val currentTime = calendar.time

        // Находим сегодняшний день
        val today = schedule.days.find { it.date.trim() == currentDate }

        // Проверяем, есть ли на сегодня хотя бы один неотменённый урок
        val todayValidLessons =
                today?.lessons?.filter { it.changeType != LessonChangeType.CANCELLED }
                        ?: emptyList()

        // Проверяем, есть ли группы
        val hasGroups = todayValidLessons.any { !it.group.isNullOrBlank() }

        // Если сегодня есть уроки - работаем с сегодняшним днём
        if (todayValidLessons.isNotEmpty()) {
                // Обрабатываем уроки с группами (универсальная логика для всех сценариев)
                val group1OnlyLessons = todayValidLessons.filter { it.group == "1" }
                val group2OnlyLessons = todayValidLessons.filter { it.group == "2" }
                val noGroupLessons = todayValidLessons.filter { it.group.isNullOrBlank() }

                // Находим текущий урок
                val currentLessonInfo =
                        findCurrentLessonForGroups(
                                group1OnlyLessons,
                                group2OnlyLessons,
                                noGroupLessons,
                                currentDate,
                                currentTime
                        )

                // Находим время окончания текущих уроков
                val currentEndTime =
                        findCurrentLessonEndTime(todayValidLessons, currentDate, currentTime)

                // Находим следующий урок
                val nextLessonInfo =
                        findNextLessonForGroups(
                                group1OnlyLessons,
                                group2OnlyLessons,
                                noGroupLessons,
                                currentDate,
                                currentEndTime ?: currentTime,
                                currentTime
                        )

                // Если нет текущих и следующих уроков, показываем следующий день
                if (currentLessonInfo.isEmpty() && nextLessonInfo.isEmpty()) {
                        return getNextDayData(schedule, currentDate)
                }

                return WidgetData(
                        currentLessonG1 = currentLessonInfo.lessonG1,
                        currentTimeG1 = currentLessonInfo.timeG1,
                        currentLessonG2 = currentLessonInfo.lessonG2,
                        currentTimeG2 = currentLessonInfo.timeG2,
                        currentCommonTime = currentLessonInfo.commonTime,
                        isCurrentCommonLesson = currentLessonInfo.isCommon,
                        nextLessonG1 = nextLessonInfo.lessonG1,
                        nextTimeG1 = nextLessonInfo.timeG1,
                        nextLessonG2 = nextLessonInfo.lessonG2,
                        nextTimeG2 = nextLessonInfo.timeG2,
                        nextCommonTime = nextLessonInfo.commonTime,
                        isNextCommonLesson = nextLessonInfo.isCommon,
                        hasGroups = true
                )
        }

        // Сегодня нет уроков - показываем следующий день
        return getNextDayData(schedule, currentDate)
}

private fun findCurrentLessonEndTime(
        lessons: List<Lesson>,
        currentDate: String,
        currentTime: Date
): Date? {
        var maxEndTime: Date? = null

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
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .parse(fullDateStr)
                        } catch (e: Exception) {
                                continue
                        }

                val endTime =
                        try {
                                val timeStr = times[1].trim()
                                val fullDateStr = "$currentDate $timeStr"
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .parse(fullDateStr)
                        } catch (e: Exception) {
                                continue
                        }

                // Урок идёт прямо сейчас - обновляем максимальное время окончания
                if (currentTime.after(startTime) && currentTime.before(endTime)) {
                        if (maxEndTime == null || endTime.after(maxEndTime)) {
                                maxEndTime = endTime
                        }
                }
        }
        return maxEndTime
}

private fun getNextDayData(schedule: Schedule, currentDate: String): WidgetData? {
        val nextDay =
                schedule.days
                        .filter { day ->
                                day.date.trim() > currentDate &&
                                        day.lessons.any {
                                                it.changeType != LessonChangeType.CANCELLED
                                        }
                        }
                        .minByOrNull { it.date }

        return if (nextDay != null) {
                val validLessons =
                        nextDay.lessons.filter { it.changeType != LessonChangeType.CANCELLED }
                val commonDate = "${nextDay.dayName}, ${nextDay.dateFormatted}"

                // Разделяем уроки по группам (универсальная логика)
                val group1OnlyLessons = validLessons.filter { it.group == "1" }
                val group2OnlyLessons = validLessons.filter { it.group == "2" }
                val noGroupLessons = validLessons.filter { it.group.isNullOrBlank() }

                // Создаем время "начала дня" для поиска первого урока
                val startOfDay =
                        try {
                                val cal = Calendar.getInstance()
                                val dateParts = nextDay.date.split("-")
                                if (dateParts.size == 3) {
                                        cal.set(
                                                dateParts[0].toInt(),
                                                dateParts[1].toInt() - 1,
                                                dateParts[2].toInt(),
                                                0,
                                                0,
                                                0
                                        )
                                        cal.add(Calendar.MINUTE, -1) // За минуту до начала дня
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

                // Используем ту же логику поиска, что и для поиска "следующего" урока
                val firstLessonInfo =
                        findNextLessonForGroups(
                                group1OnlyLessons,
                                group2OnlyLessons,
                                noGroupLessons,
                                nextDay.date,
                                startOfDay,
                                startOfDay // currentTime не важен для этой функции, но передаем
                                // startOfDay
                                )

                // Если нашли урок, возвращаем данные
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
                                hasGroups =
                                        true, // Всегда используем режим с группами для унификации
                                nextDayDate = commonDate
                        )
                } else {
                        null
                }
        } else {
                null
        }
}

data class WidgetData(
        val currentLessonG1: String,
        val currentTimeG1: String,
        val currentLessonG2: String,
        val currentTimeG2: String,
        val currentCommonTime: String,
        val isCurrentCommonLesson: Boolean,
        val nextLessonG1: String,
        val nextTimeG1: String,
        val nextLessonG2: String,
        val nextTimeG2: String,
        val nextCommonTime: String,
        val isNextCommonLesson: Boolean,
        val hasGroups: Boolean,
        val nextDayDate: String = ""
)
