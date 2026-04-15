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

class ScheduleWidgetDark : AppWidgetProvider() {

        companion object {
                const val ACTION_AUTO_UPDATE_DARK = "com.example.edupageschedule.action.AUTO_UPDATE"
        }

        override fun onUpdate(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetIds: IntArray
        ) {
                for (appWidgetId in appWidgetIds) {
                        updateAppWidgetDark(context, appWidgetManager, appWidgetId)
                }
                scheduleNextUpdate(context)
        }

        override fun onReceive(context: Context, intent: Intent) {
                super.onReceive(context, intent)
                if (intent.action == ACTION_AUTO_UPDATE_DARK) {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisAppWidget = ComponentName(context, ScheduleWidgetDark::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
                        for (appWidgetId in appWidgetIds) {
                                updateAppWidgetDark(context, appWidgetManager, appWidgetId)
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
                        Intent(context, ScheduleWidgetDark::class.java).apply {
                                action = ACTION_AUTO_UPDATE_DARK
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

                // РСЃРїРѕР»СЊР·СѓРµРј setExactAndAllowWhileIdle РґР»СЏ РЅР°РґРµР¶РЅРѕРіРѕ СЃСЂР°Р±Р°С‚С‹РІР°РЅРёСЏ РґР°Р¶Рµ РІ СЂРµР¶РёРјРµ
                // Doze
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        nextMinute,
                                        pendingIntent
                                )
                        } else {
                                // Р•СЃР»Рё РЅРµС‚ СЂР°Р·СЂРµС€РµРЅРёСЏ, РёСЃРїРѕР»СЊР·СѓРµРј setAndAllowWhileIdle (РјРµРЅРµРµ
                                // С‚РѕС‡РЅС‹Р№, РЅРѕ Р»СѓС‡С€Рµ С‡РµРј
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
                        Intent(context, ScheduleWidgetDark::class.java).apply {
                                action = ACTION_AUTO_UPDATE_DARK
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

internal fun updateAppWidgetDark(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout_dark)

        val schedule = ScheduleStorage.loadSchedule(context)
        val substitutions = ScheduleStorage.loadSubstitutions(context)

        val finalSchedule =
                if (schedule != null && substitutions != null) {
                        ScheduleMerger.applySubstitutions(schedule, substitutions)
                } else {
                        schedule
                }

        if (finalSchedule != null) {
                val WidgetDataDark = getWidgetDataDark(finalSchedule)

                if (WidgetDataDark != null) {
                        // РЈСЃС‚Р°РЅР°РІР»РёРІР°РµРј РґР°С‚Сѓ СЃР»РµРґСѓСЋС‰РµРіРѕ РґРЅСЏ
                        if (WidgetDataDark.nextDayDate.isNotEmpty()) {
                                views.setTextViewText(
                                        R.id.widget_next_day_date,
                                        WidgetDataDark.nextDayDate
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

                        if (WidgetDataDark.hasGroups) {
                                // РћС‚РѕР±СЂР°Р¶Р°РµРј СЃ РіСЂСѓРїРїР°РјРё
                                // РўРµРєСѓС‰РёР№ СѓСЂРѕРє
                                if (WidgetDataDark.currentLessonG1.isNotEmpty() ||
                                                WidgetDataDark.currentLessonG2.isNotEmpty()
                                ) {

                                        views.setViewVisibility(
                                                R.id.widget_current_block,
                                                android.view.View.VISIBLE
                                        )

                                        if (WidgetDataDark.nextDayDate.isNotEmpty()) {
                                                views.setTextViewText(
                                                        R.id.widget_title,
                                                        WidgetDataDark.nextDayDate
                                                )
                                                // РЎС‚РёР»СЊ "РЎР»РµРґСѓСЋС‰РёР№ СѓСЂРѕРє" (РћСЂР°РЅР¶РµРІС‹Р№)
                                                views.setInt(
                                                        R.id.widget_current_block,
                                                        "setBackgroundResource",
                                                        R.drawable.widget_next_bg_dark
                                                )
                                                val orangeColor =
                                                        android.graphics.Color.parseColor("#FFB74D")
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
                                                // РЎС‚РёР»СЊ "РўРµРєСѓС‰РёР№ СѓСЂРѕРє" (РЎРёРЅРёР№)
                                                views.setInt(
                                                        R.id.widget_current_block,
                                                        "setBackgroundResource",
                                                        R.drawable.widget_current_bg_dark
                                                )
                                                val blueColor =
                                                        android.graphics.Color.parseColor("#64B5F6")
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

                                        // РџСЂРѕРІРµСЂСЏРµРј, СЏРІР»СЏРµС‚СЃСЏ Р»Рё СѓСЂРѕРє РѕР±С‰РёРј
                                        val isCommonLesson = WidgetDataDark.isCurrentCommonLesson

                                        if (isCommonLesson) {
                                                // РћР±С‰РёР№ СѓСЂРѕРє РґР»СЏ РІСЃРµРіРѕ РєР»Р°СЃСЃР° - РїРѕРєР°Р·С‹РІР°РµРј РІ Р±Р»РѕРєРµ
                                                // Р±РµР· РіСЂСѓРїРї
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
                                                        WidgetDataDark.currentLessonG1
                                                )
                                                views.setTextViewText(
                                                        R.id.widget_current_time,
                                                        WidgetDataDark.currentCommonTime
                                                )
                                        } else {
                                                // РЈСЂРѕРєРё СЃ РіСЂСѓРїРїР°РјРё - РїРѕРєР°Р·С‹РІР°РµРј РєРѕРЅС‚РµР№РЅРµСЂ РіСЂСѓРїРї
                                                views.setViewVisibility(
                                                        R.id.widget_current_no_group_block,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_current_groups_container,
                                                        android.view.View.VISIBLE
                                                )

                                                // Р“СЂСѓРїРїР° 1
                                                if (WidgetDataDark.currentLessonG1.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group1_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_lesson_g1,
                                                                WidgetDataDark.currentLessonG1
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_time_g1,
                                                                WidgetDataDark.currentTimeG1
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group1_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // Р“СЂСѓРїРїР° 2
                                                if (WidgetDataDark.currentLessonG2.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group2_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_lesson_g2,
                                                                WidgetDataDark.currentLessonG2
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_current_time_g2,
                                                                WidgetDataDark.currentTimeG2
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_current_group2_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // РћР±С‰РµРµ РІСЂРµРјСЏ
                                                if (WidgetDataDark.currentCommonTime.isNotEmpty()) {
                                                        views.setTextViewText(
                                                                R.id.widget_current_common_time,
                                                                WidgetDataDark.currentCommonTime
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

                                // РЎР»РµРґСѓСЋС‰РёР№ СѓСЂРѕРє
                                if (WidgetDataDark.nextLessonG1.isNotEmpty() ||
                                                WidgetDataDark.nextLessonG2.isNotEmpty()
                                ) {
                                        views.setViewVisibility(
                                                R.id.widget_next_block,
                                                android.view.View.VISIBLE
                                        )

                                        // РџСЂРѕРІРµСЂСЏРµРј, СЏРІР»СЏРµС‚СЃСЏ Р»Рё СѓСЂРѕРє РѕР±С‰РёРј (Р±РµР· РїСЂРµС„РёРєСЃР° РіСЂСѓРїРїС‹)
                                        val isCommonNextLesson = WidgetDataDark.isNextCommonLesson

                                        if (isCommonNextLesson) {
                                                // РћР±С‰РёР№ СѓСЂРѕРє РґР»СЏ РІСЃРµРіРѕ РєР»Р°СЃСЃР° - РїРѕРєР°Р·С‹РІР°РµРј РІ Р±Р»РѕРєРµ
                                                // Р±РµР· РіСЂСѓРїРї
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
                                                        WidgetDataDark.nextLessonG1.trim()
                                                )
                                                views.setTextViewText(
                                                        R.id.widget_next_time,
                                                        WidgetDataDark.nextCommonTime
                                                )
                                        } else {
                                                // РЈСЂРѕРєРё СЃ РіСЂСѓРїРїР°РјРё - РїРѕРєР°Р·С‹РІР°РµРј РєРѕРЅС‚РµР№РЅРµСЂ РіСЂСѓРїРї
                                                views.setViewVisibility(
                                                        R.id.widget_next_no_group_block,
                                                        android.view.View.GONE
                                                )
                                                views.setViewVisibility(
                                                        R.id.widget_next_groups_container,
                                                        android.view.View.VISIBLE
                                                )

                                                // Р“СЂСѓРїРїР° 1
                                                if (WidgetDataDark.nextLessonG1.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group1_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_lesson_g1,
                                                                WidgetDataDark.nextLessonG1.trim()
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_time_g1,
                                                                WidgetDataDark.nextTimeG1
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group1_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // Р“СЂСѓРїРїР° 2
                                                if (WidgetDataDark.nextLessonG2.isNotEmpty()) {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group2_block,
                                                                android.view.View.VISIBLE
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_lesson_g2,
                                                                WidgetDataDark.nextLessonG2.trim()
                                                        )
                                                        views.setTextViewText(
                                                                R.id.widget_next_time_g2,
                                                                WidgetDataDark.nextTimeG2
                                                        )
                                                } else {
                                                        views.setViewVisibility(
                                                                R.id.widget_next_group2_block,
                                                                android.view.View.GONE
                                                        )
                                                }

                                                // РћР±С‰РµРµ РІСЂРµРјСЏ
                                                if (WidgetDataDark.nextCommonTime.isNotEmpty()) {
                                                        views.setTextViewText(
                                                                R.id.widget_next_common_time,
                                                                WidgetDataDark.nextCommonTime
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
                                // РћС‚РѕР±СЂР°Р¶Р°РµРј Р±РµР· РіСЂСѓРїРї (СЃС‚Р°СЂС‹Р№ С„РѕСЂРјР°С‚ РґР»СЏ СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚Рё)
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

                                // РўРµРєСѓС‰РёР№ СѓСЂРѕРє
                                if (WidgetDataDark.currentLessonG1.isNotEmpty()) {
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
                                                WidgetDataDark.currentLessonG1
                                        )
                                        views.setTextViewText(
                                                R.id.widget_current_time,
                                                WidgetDataDark.currentTimeG1
                                        )
                                } else {
                                        views.setViewVisibility(
                                                R.id.widget_current_block,
                                                android.view.View.GONE
                                        )
                                }

                                // РЎР»РµРґСѓСЋС‰РёР№ СѓСЂРѕРє
                                if (WidgetDataDark.nextLessonG1.isNotEmpty()) {
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
                                                WidgetDataDark.nextLessonG1
                                        )
                                        views.setTextViewText(
                                                R.id.widget_next_time,
                                                WidgetDataDark.nextTimeG1
                                        )
                                } else {
                                        views.setViewVisibility(
                                                R.id.widget_next_block,
                                                android.view.View.GONE
                                        )
                                }
                        }
                } else {
                        // РќРµС‚ РґР°РЅРЅС‹С…
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

private fun getWidgetDataDark(schedule: Schedule): WidgetDataDark? {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(calendar.time)
        val currentTime = calendar.time

        // РќР°С…РѕРґРёРј СЃРµРіРѕРґРЅСЏС€РЅРёР№ РґРµРЅСЊ
        val today = schedule.days.find { it.date.trim() == currentDate }

        // РџСЂРѕРІРµСЂСЏРµРј, РµСЃС‚СЊ Р»Рё РЅР° СЃРµРіРѕРґРЅСЏ С…РѕС‚СЏ Р±С‹ РѕРґРёРЅ РЅРµРѕС‚РјРµРЅС‘РЅРЅС‹Р№ СѓСЂРѕРє
        val todayValidLessons =
                today?.lessons?.filter { it.changeType != LessonChangeType.CANCELLED }
                        ?: emptyList()

        // РџСЂРѕРІРµСЂСЏРµРј, РµСЃС‚СЊ Р»Рё РіСЂСѓРїРїС‹
        val hasGroups = todayValidLessons.any { !it.group.isNullOrBlank() }

        // Р•СЃР»Рё СЃРµРіРѕРґРЅСЏ РµСЃС‚СЊ СѓСЂРѕРєРё - СЂР°Р±РѕС‚Р°РµРј СЃ СЃРµРіРѕРґРЅСЏС€РЅРёРј РґРЅС‘Рј
        if (todayValidLessons.isNotEmpty()) {
                // РћР±СЂР°Р±Р°С‚С‹РІР°РµРј СѓСЂРѕРєРё СЃ РіСЂСѓРїРїР°РјРё (СѓРЅРёРІРµСЂСЃР°Р»СЊРЅР°СЏ Р»РѕРіРёРєР° РґР»СЏ РІСЃРµС… СЃС†РµРЅР°СЂРёРµРІ)
                val group1OnlyLessons = todayValidLessons.filter { it.group == "1" }
                val group2OnlyLessons = todayValidLessons.filter { it.group == "2" }
                val noGroupLessons = todayValidLessons.filter { it.group.isNullOrBlank() }

                // РќР°С…РѕРґРёРј С‚РµРєСѓС‰РёР№ СѓСЂРѕРє
                val currentLessonInfo =
                        findCurrentLessonForGroups(
                                group1OnlyLessons,
                                group2OnlyLessons,
                                noGroupLessons,
                                currentDate,
                                currentTime
                        )

                // РќР°С…РѕРґРёРј РІСЂРµРјСЏ РѕРєРѕРЅС‡Р°РЅРёСЏ С‚РµРєСѓС‰РёС… СѓСЂРѕРєРѕРІ
                val currentEndTime =
                        findCurrentLessonEndTimeDark(todayValidLessons, currentDate, currentTime)

                // РќР°С…РѕРґРёРј СЃР»РµРґСѓСЋС‰РёР№ СѓСЂРѕРє
                val nextLessonInfo =
                        findNextLessonForGroups(
                                group1OnlyLessons,
                                group2OnlyLessons,
                                noGroupLessons,
                                currentDate,
                                currentEndTime ?: currentTime,
                                currentTime
                        )

                // Р•СЃР»Рё РЅРµС‚ С‚РµРєСѓС‰РёС… Рё СЃР»РµРґСѓСЋС‰РёС… СѓСЂРѕРєРѕРІ, РїРѕРєР°Р·С‹РІР°РµРј СЃР»РµРґСѓСЋС‰РёР№ РґРµРЅСЊ
                if (currentLessonInfo.isEmpty() && nextLessonInfo.isEmpty()) {
                        return getNextDayDataDark(schedule, currentDate)
                }

                return WidgetDataDark(
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

        // РЎРµРіРѕРґРЅСЏ РЅРµС‚ СѓСЂРѕРєРѕРІ - РїРѕРєР°Р·С‹РІР°РµРј СЃР»РµРґСѓСЋС‰РёР№ РґРµРЅСЊ
        return getNextDayDataDark(schedule, currentDate)
}

private fun findCurrentLessonEndTimeDark(
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

                // РЈСЂРѕРє РёРґС‘С‚ РїСЂСЏРјРѕ СЃРµР№С‡Р°СЃ - РѕР±РЅРѕРІР»СЏРµРј РјР°РєСЃРёРјР°Р»СЊРЅРѕРµ РІСЂРµРјСЏ РѕРєРѕРЅС‡Р°РЅРёСЏ
                if (currentTime.after(startTime) && currentTime.before(endTime)) {
                        if (maxEndTime == null || endTime.after(maxEndTime)) {
                                maxEndTime = endTime
                        }
                }
        }
        return maxEndTime
}

private fun getNextDayDataDark(schedule: Schedule, currentDate: String): WidgetDataDark? {
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

                // Р Р°Р·РґРµР»СЏРµРј СѓСЂРѕРєРё РїРѕ РіСЂСѓРїРїР°Рј (СѓРЅРёРІРµСЂСЃР°Р»СЊРЅР°СЏ Р»РѕРіРёРєР°)
                val group1OnlyLessons = validLessons.filter { it.group == "1" }
                val group2OnlyLessons = validLessons.filter { it.group == "2" }
                val noGroupLessons = validLessons.filter { it.group.isNullOrBlank() }

                // РЎРѕР·РґР°РµРј РІСЂРµРјСЏ "РЅР°С‡Р°Р»Р° РґРЅСЏ" РґР»СЏ РїРѕРёСЃРєР° РїРµСЂРІРѕРіРѕ СѓСЂРѕРєР°
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
                                        cal.add(Calendar.MINUTE, -1) // Р—Р° РјРёРЅСѓС‚Сѓ РґРѕ РЅР°С‡Р°Р»Р° РґРЅСЏ
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

                // РСЃРїРѕР»СЊР·СѓРµРј С‚Сѓ Р¶Рµ Р»РѕРіРёРєСѓ РїРѕРёСЃРєР°, С‡С‚Рѕ Рё РґР»СЏ РїРѕРёСЃРєР° "СЃР»РµРґСѓСЋС‰РµРіРѕ" СѓСЂРѕРєР°
                val firstLessonInfo =
                        findNextLessonForGroups(
                                group1OnlyLessons,
                                group2OnlyLessons,
                                noGroupLessons,
                                nextDay.date,
                                startOfDay,
                                startOfDay // currentTime РЅРµ РІР°Р¶РµРЅ РґР»СЏ СЌС‚РѕР№ С„СѓРЅРєС†РёРё, РЅРѕ РїРµСЂРµРґР°РµРј
                                // startOfDay
                                )

                // Р•СЃР»Рё РЅР°С€Р»Рё СѓСЂРѕРє, РІРѕР·РІСЂР°С‰Р°РµРј РґР°РЅРЅС‹Рµ
                if (!firstLessonInfo.isEmpty()) {
                        WidgetDataDark(
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
                                        true, // Р’СЃРµРіРґР° РёСЃРїРѕР»СЊР·СѓРµРј СЂРµР¶РёРј СЃ РіСЂСѓРїРїР°РјРё РґР»СЏ СѓРЅРёС„РёРєР°С†РёРё
                                nextDayDate = commonDate
                        )
                } else {
                        null
                }
        } else {
                null
        }
}

data class WidgetDataDark(
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


