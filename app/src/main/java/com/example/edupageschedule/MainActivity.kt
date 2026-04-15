package com.example.edupageschedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Refresh
import com.example.edupageschedule.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val sharedPrefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
            var themePrefString by remember {
                mutableStateOf(sharedPrefs.getString("theme", ThemePreference.SYSTEM.name) ?: ThemePreference.SYSTEM.name)
            }
            
            val themePreference = ThemePreference.valueOf(themePrefString)
            val isDarkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            EdupageScheduleTheme(darkTheme = isDarkTheme) {
                ScheduleApp(
                    themePreference = themePreference,
                    onThemeChange = { newPref ->
                        sharedPrefs.edit().putString("theme", newPref.name).apply()
                        themePrefString = newPref.name
                    }
                )
            }
        }
    }
}

// Вынесенная логика загрузки для избежания дублирования
suspend fun loadScheduleData(
        parser: EdupageParser,
        subParser: SubstitutionParser,
        context: android.content.Context,
        onStatusUpdate: (String) -> Unit
): Pair<Schedule?, SubstitutionData?> {
    onStatusUpdate("Ielādē stundu sarakstu...")

    return try {
        val newSchedule =
                parser.getSchedule(
                        Constants.DEFAULT_CLASS,
                        daysAhead = Constants.DEFAULT_DAYS_AHEAD
                )

        if (newSchedule != null) {
            withContext(Dispatchers.IO) { ScheduleStorage.saveSchedule(context, newSchedule) }

            onStatusUpdate("Ielādē nomaiņas...")
            val newSubstitutions =
                    subParser.getSubstitutions(
                            Constants.DEFAULT_CLASS.uppercase(),
                            daysAhead = Constants.DEFAULT_DAYS_AHEAD
                    )

            val cachedSubs = withContext(Dispatchers.IO) { ScheduleStorage.loadSubstitutions(context) }
            val mergedDays = mutableMapOf<String, DaySubstitutions>()

            if (cachedSubs != null) {
                val scheduleDates = newSchedule.days.map { it.date }.toSet()
                for ((date, daySub) in cachedSubs.days) {
                    if (date in scheduleDates) {
                        mergedDays[date] = daySub
                    }
                }
            }

            if (newSubstitutions != null) {
                for ((date, daySub) in newSubstitutions.days) {
                    mergedDays[date] = daySub
                }
            }

            val finalSubstitutions = if (mergedDays.isNotEmpty()) {
                SubstitutionData(className = Constants.DEFAULT_CLASS.uppercase(), days = mergedDays)
            } else {
                null
            }

            if (finalSubstitutions != null) {
                withContext(Dispatchers.IO) {
                    ScheduleStorage.saveSubstitutions(context, finalSubstitutions)
                }

                // Trigger widget update
                val intent = Intent(ScheduleWidget.ACTION_AUTO_UPDATE)
                intent.setPackage(context.packageName)
                context.sendBroadcast(intent)

                val mergedSchedule = ScheduleMerger.applySubstitutions(newSchedule, finalSubstitutions)
                val changesCount = mergedSchedule.days.sumOf { day ->
                    day.lessons.count { it.changeType != LessonChangeType.NONE }
                }
                onStatusUpdate(
                        "Atjaunināts: ${newSchedule.days.size} dienas, $changesCount izmaiņas"
                )
                Pair(newSchedule, finalSubstitutions)
            } else {
                // Even if no substitutions, we saved schedule, so update widget
                val intent = Intent(ScheduleWidget.ACTION_AUTO_UPDATE)
                intent.setPackage(context.packageName)
                context.sendBroadcast(intent)

                onStatusUpdate(
                        "Atjaunināts: ${newSchedule.days.size} dienas (nomaiņas nav atrastas)"
                )
                Pair(newSchedule, null)
            }
        } else {
            onStatusUpdate("Ielādes kļūda")
            Pair(null, null)
        }
    } catch (e: Exception) {
        onStatusUpdate("Kļūda: ${e.message}")
        e.printStackTrace()
        Pair(null, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {}
) {
    var schedule by remember { mutableStateOf<Schedule?>(null) }
    var substitutions by remember { mutableStateOf<SubstitutionData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Nospiediet pogu, lai ielādētu") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val parser = remember { EdupageParser(Constants.DEFAULT_SCHOOL) }
    val subParser = remember { SubstitutionParser(Constants.DEFAULT_SCHOOL) }

    LaunchedEffect(Unit) {
        val cached = ScheduleStorage.loadSchedule(context)
        val cachedSubs = ScheduleStorage.loadSubstitutions(context)

        if (cached != null) {
            substitutions = cachedSubs
            schedule =
                    if (cachedSubs != null) {
                        ScheduleMerger.applySubstitutions(cached, cachedSubs)
                    } else {
                        cached
                    }
            statusMessage =
                    if (cachedSubs != null) {
                        "Ielādēts kešatmiņa (ar nomaiņām)"
                    } else {
                        "Ielādēts kešatmiņa"
                    }
        }

        isLoading = true
        val (newSchedule, newSubstitutions) =
                loadScheduleData(parser, subParser, context) { status -> statusMessage = status }

        if (newSchedule != null) {
            schedule =
                    if (newSubstitutions != null) {
                        substitutions = newSubstitutions
                        ScheduleMerger.applySubstitutions(newSchedule, newSubstitutions)
                    } else {
                        newSchedule
                    }
        }
        isLoading = false
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Stundu saraksts dp1-3") },
                        actions = {
                            IconButton(onClick = {
                                val nextPref = when (themePreference) {
                                    ThemePreference.SYSTEM -> ThemePreference.LIGHT
                                    ThemePreference.LIGHT -> ThemePreference.DARK
                                    ThemePreference.DARK -> ThemePreference.SYSTEM
                                }
                                onThemeChange(nextPref)
                            }) {
                                val icon = when (themePreference) {
                                    ThemePreference.SYSTEM -> Icons.Default.BrightnessAuto
                                    ThemePreference.LIGHT -> Icons.Default.Brightness7
                                    ThemePreference.DARK -> Icons.Default.Brightness4
                                }
                                Icon(icon, contentDescription = "Mainīt tēmu")
                            }
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                                )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val (newSchedule, newSubstitutions) =
                                        loadScheduleData(parser, subParser, context) { status ->
                                            statusMessage = status
                                        }

                                if (newSchedule != null) {
                                    schedule =
                                            if (newSubstitutions != null) {
                                                substitutions = newSubstitutions
                                                ScheduleMerger.applySubstitutions(
                                                        newSchedule,
                                                        newSubstitutions
                                                )
                                            } else {
                                                newSchedule
                                            }
                                }
                                isLoading = false
                            }
                        }
                ) { Icon(Icons.Default.Refresh, contentDescription = "Atjaunināt") }
            }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                        text = statusMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                )
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (schedule != null) {
                SchedulePager(schedule = schedule!!)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                            text = "Stundu saraksts nav ielādēts",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SchedulePager(schedule: Schedule) {
    val initialIndex =
            remember(schedule) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val index = schedule.days.indexOfFirst { it.date == currentDate }
                if (index != -1) index else 0
            }

    val pagerState =
            androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = initialIndex,
                    pageCount = { schedule.days.size }
            )
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
        ) {
            schedule.days.forEachIndexed { index, day ->
                val dateParts = day.dateFormatted.split(".")
                val shortDate =
                        if (dateParts.size >= 2) "${dateParts[0]}.${dateParts[1]}"
                        else day.dateFormatted

                Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                        text = day.dayName.take(3),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                )
                                Text(text = shortDate, fontSize = 12.sp)
                            }
                        }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page < schedule.days.size) {
                val day = schedule.days[page]
                ScheduleDayList(day = day)
            }
        }
    }
}

@Composable
fun ScheduleDayList(day: Day) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {

        // Группируем уроки по периодам
        val lessonsByPeriod = day.lessons.groupBy { it.period }

        if (lessonsByPeriod.isNotEmpty()) {
            val minPeriod = lessonsByPeriod.keys.min()
            val maxPeriod = lessonsByPeriod.keys.max()

            for (period in minPeriod..maxPeriod) {
                val lessonsInPeriod = lessonsByPeriod[period]

                item {
                    if (lessonsInPeriod == null) {
                        // Пропущенный период — показываем пустую строку на всю ширину
                        EmptyPeriodCard(period = period)
                    } else {
                        // Проверяем есть ли уроки с группами
                        val hasGroups = lessonsInPeriod.any { !it.group.isNullOrBlank() }

                        if (hasGroups) {
                            val group1Lesson = lessonsInPeriod.firstOrNull { it.group == "1" }
                            val group2Lesson = lessonsInPeriod.firstOrNull { it.group == "2" }

                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (group1Lesson != null) {
                                        LessonCard(lesson = group1Lesson)
                                    } else {
                                        EmptyLessonCard(period = period, group = "1")
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    if (group2Lesson != null) {
                                        LessonCard(lesson = group2Lesson)
                                    } else {
                                        EmptyLessonCard(period = period, group = "2")
                                    }
                                }
                            }
                        } else {
                            // Урок без группы — на всю ширину
                            LessonCard(lesson = lessonsInPeriod.first())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayHeader(day: Day) {
    Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                    text = day.dayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                    text = day.dateFormatted,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EmptyPeriodCard(period: Int) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
            border =
                    androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text = "${period}. stunda",
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 13.sp
            )
            Text(
                    text = "— Nav stundas —",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EmptyLessonCard(period: Int, group: String) {

    Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
    ) {
        Column(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Text(
                    text = "${period}. stunda",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = "Nav stundas",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
            ) {
                Text(
                        text = "Grupa: $group",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/** Compares teacher lists ignoring word order within each name ("Motivāne Aina" == "Aina Motivāne") */
private fun sameTeachersIgnoringNameOrder(a: List<String>, b: List<String>): Boolean {
    if (a.size != b.size) return false
    val normalize = { name: String -> name.lowercase().split(" ").sorted().joinToString(" ") }
    val aNorm = a.map(normalize).sorted()
    val bNorm = b.map(normalize).sorted()
    return aNorm == bNorm
}

@Composable
fun SubstitutionDetailsDialog(lesson: Lesson, onDismiss: () -> Unit) {
    val subjectChanged = lesson.originalSubject != null && lesson.originalSubject != lesson.subject
    val teacherChanged = lesson.originalTeachers != null &&
            !sameTeachersIgnoringNameOrder(lesson.originalTeachers, lesson.teachers)
    val roomChanged = lesson.originalClassrooms != null && lesson.originalClassrooms != lesson.classrooms

    // Определяем, что именно поменялось
    val onlyRoomChanged = roomChanged && !subjectChanged && !teacherChanged
    val onlyTeacherChanged = teacherChanged && !subjectChanged && !roomChanged

    val isRemoteOnly = lesson.isRemote && !subjectChanged && !teacherChanged && !roomChanged

    val titleText = when (lesson.changeType) {
        LessonChangeType.CANCELLED -> when {
            lesson.isMoved && lesson.movedTo != null -> "🕒 Stunda pārcelta"
            lesson.isRemote -> "🏠 Attālināti"
            else -> "✖ Stunda atcelta"
        }
        LessonChangeType.ADDED -> "✓ Jauna stunda pievienota"
        LessonChangeType.MODIFIED -> when {
            lesson.isMoved -> "🕒 Pārcelta stunda"
            isRemoteOnly -> "🏠 Attālināti"
            onlyRoomChanged -> "🚪 Kabineta maiņa"
            onlyTeacherChanged -> "👨‍🏫 Skolotāja maiņa"
            else -> "✎ Izmaiņas stundā"
        }
        else -> "Informācija"
    }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                        text = titleText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Период и время
                    Text(
                            text = "${lesson.period}. stunda  ${lesson.time}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    when (lesson.changeType) {
                        LessonChangeType.CANCELLED -> {
                            if (lesson.isMoved && lesson.movedTo != null) {
                                // Урок перенесён на другой период
                                val movedToPeriod = lesson.movedTo.replace(Regex("[^0-9]"), "")
                                val movedToText = if (movedToPeriod.isNotEmpty()) "$movedToPeriod. stundu" else lesson.movedTo
                                Text(
                                        text = "Stunda ir pārcelta uz $movedToText",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            } else if (lesson.isRemote) {
                                Text(
                                        text = "Stunda notiek attālināti",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                        text = "Stunda ir atcelta",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (LocalThemeIsDark.current) Color(0xFFEF9A9A) else Color(0xFFD32F2F)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = "Priekšmets: ${lesson.originalSubject ?: lesson.subject}",
                                    fontSize = 14.sp
                            )
                        }
                        LessonChangeType.ADDED -> {
                            if (lesson.isMoved) {
                                Text(
                                        text = if (lesson.movedFrom != null) "Stunda ir pārcelta no ${lesson.movedFrom}" else "Stunda ir pārcelta",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                        text = "Pievienota jauna stunda",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (LocalThemeIsDark.current) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Priekšmets: ${lesson.subject}", fontSize = 14.sp)
                            if (lesson.teachers.isNotEmpty()) {
                                Text(
                                        text = "Skolotājs: ${lesson.teachers.joinToString(", ")}",
                                        fontSize = 14.sp
                                )
                            }
                            if (lesson.classrooms.isNotEmpty()) {
                                Text(
                                        text = "Kabinets: ${lesson.classrooms.joinToString(", ")}",
                                        fontSize = 14.sp
                                )
                            }
                        }
                        LessonChangeType.MODIFIED -> {
                            if (lesson.isMoved) {
                                Text(
                                        text = if (lesson.movedFrom != null) "Stunda ir pārcelta no ${lesson.movedFrom}" else "Stunda ir pārcelta",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (subjectChanged) {
                                ChangeRow(
                                        label = "Priekšmets",
                                        oldValue = lesson.originalSubject ?: "",
                                        newValue = lesson.subject
                                )
                            } else {
                                Text(
                                        text = "Priekšmets: ${lesson.subject}",
                                        fontSize = 14.sp
                                )
                            }

                            if (teacherChanged) {
                                ChangeRow(
                                        label = "Skolotājs",
                                        oldValue = lesson.originalTeachers?.joinToString(", ") ?: "",
                                        newValue = lesson.teachers.joinToString(", ")
                                )
                            }

                            if (roomChanged) {
                                ChangeRow(
                                        label = "Kabinets",
                                        oldValue = lesson.originalClassrooms?.joinToString(", ") ?: "",
                                        newValue = lesson.classrooms.joinToString(", ")
                                )
                            }

                            if (!subjectChanged && !teacherChanged && !roomChanged && !lesson.isMoved) {
                                if (lesson.isRemote) {
                                    val origRoom = (lesson.originalClassrooms ?: lesson.classrooms)
                                            .joinToString(", ")
                                            .let { if (it == "N/A") "Nav norādīts" else it }
                                    ChangeRow(
                                            label = "Kabinets",
                                            oldValue = origRoom,
                                            newValue = "Attālināti"
                                    )
                                } else {
                                    Text(
                                            text = "Stundā ir izmaiņas",
                                            fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        else -> {}
                    }

                    if (!lesson.group.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                                text = "Grupa: ${lesson.group}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Aizvērt")
                }
            }
    )
}

@Composable
fun ChangeRow(label: String, oldValue: String, newValue: String) {
    val oldColor = if (LocalThemeIsDark.current) Color(0xFFEF9A9A) else Color(0xFFD32F2F)
    val newColor = if (LocalThemeIsDark.current) Color(0xFFA5D6A7) else Color(0xFF2E7D32)

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                    text = oldValue,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 14.sp,
                    color = oldColor,
                    fontWeight = FontWeight.Medium
            )
            Text(
                    text = "→",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                    text = newValue,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 14.sp,
                    color = newColor,
                    fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LessonCard(lesson: Lesson) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog && lesson.changeType != LessonChangeType.NONE) {
        SubstitutionDetailsDialog(lesson = lesson, onDismiss = { showDialog = false })
    }

    // Определяем, есть ли реальные изменения помимо remote (для цвета карточки)
    val isOnlyRemote = lesson.isRemote && lesson.changeType == LessonChangeType.MODIFIED &&
            (lesson.originalSubject == null || lesson.originalSubject == lesson.subject) &&
            (lesson.originalTeachers == null || sameTeachersIgnoringNameOrder(lesson.originalTeachers, lesson.teachers)) &&
            (lesson.originalClassrooms == null || lesson.originalClassrooms == lesson.classrooms ||
                    lesson.classrooms.any { it.equals("Attālināti", ignoreCase = true) }) &&
            !lesson.isMoved

    val isDark = LocalThemeIsDark.current

    val backgroundColor =
            when {
                isOnlyRemote -> if (isDark) RemoteCardDark else RemoteCardLight
                lesson.changeType == LessonChangeType.ADDED -> if (isDark) AddedCardDark else AddedCardLight
                lesson.changeType == LessonChangeType.CANCELLED -> if (isDark) CancelledCardDark else CancelledCardLight
                lesson.changeType == LessonChangeType.MODIFIED -> if (isDark) ModifiedCardDark else ModifiedCardLight
                else -> MaterialTheme.colorScheme.surface
            }

    val borderColor =
            when {
                isOnlyRemote -> if (isDark) RemoteBorderDark else RemoteBorderLight
                lesson.changeType == LessonChangeType.ADDED -> if (isDark) AddedBorderDark else AddedBorderLight
                lesson.changeType == LessonChangeType.CANCELLED -> if (isDark) CancelledBorderDark else CancelledBorderLight
                lesson.changeType == LessonChangeType.MODIFIED -> if (isDark) ModifiedBorderDark else ModifiedBorderLight
                else -> Color.Transparent
            }

    // Определяем, есть ли реальные изменения помимо remote
    val hasOtherChanges = lesson.changeType == LessonChangeType.MODIFIED && (
            (lesson.originalSubject != null && lesson.originalSubject != lesson.subject) ||
            (lesson.originalTeachers != null && !sameTeachersIgnoringNameOrder(lesson.originalTeachers, lesson.teachers)) ||
            (lesson.originalClassrooms != null && lesson.originalClassrooms != lesson.classrooms &&
                    !(lesson.isRemote && lesson.classrooms.any { it.equals("Attālināti", ignoreCase = true) })) ||
            lesson.isMoved
    )

    val changeLabel =
            when {
                lesson.isMoved && lesson.movedTo != null -> "🕒 PĀRCELTS"
                lesson.isMoved -> "🕒 PĀRCELTS"
                lesson.isRemote && !hasOtherChanges -> "🏠 ATTĀLINĀTI"
                lesson.changeType == LessonChangeType.CANCELLED -> "✖ ATCELTS"
                lesson.changeType == LessonChangeType.ADDED -> "✓ JAUNS"
                lesson.changeType == LessonChangeType.MODIFIED -> "✎ IZMAIŅAS"
                else -> ""
            }

    val cardModifier = if (lesson.changeType != LessonChangeType.NONE) {
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                .clickable { showDialog = true }
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
    }

    Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border =
                    if (borderColor != Color.Transparent) {
                        androidx.compose.foundation.BorderStroke(2.dp, borderColor)
                    } else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "${lesson.period}. stunda",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                )

                Text(
                        text = lesson.time,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                )
            }

            if (lesson.changeType != LessonChangeType.NONE) {
                Spacer(modifier = Modifier.height(4.dp))
                // Используем Surface с ярким цветом для метки
                Surface(shape = MaterialTheme.shapes.small, color = borderColor) {
                    Text(
                            text = changeLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color =
                                    if (lesson.changeType == LessonChangeType.MODIFIED && !LocalThemeIsDark.current) Color.Black
                                    else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                    text = lesson.subject,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (lesson.teachers.isNotEmpty()) {
                Text(
                        text = "👨‍🏫 ${lesson.teachers.joinToString(", ")}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (lesson.classrooms.isNotEmpty() && !lesson.isRemote) {
                Text(
                        text = "🚪 Kab. ${lesson.classrooms.joinToString(", ")}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!lesson.group.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                ) {
                    Text(
                            text = "Grupa: ${lesson.group}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
