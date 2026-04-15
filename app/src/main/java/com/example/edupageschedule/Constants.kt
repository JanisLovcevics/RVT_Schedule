package com.example.edupageschedule

object Constants {
    // Storage
    const val SCHEDULE_FILE = "schedule.json"
    const val SUBSTITUTIONS_FILE = "substitutions.json"
    
    // Default values
    const val DEFAULT_YEAR = 2025
    const val DEFAULT_DAYS_AHEAD = 14
    const val DEFAULT_SCHOOL = "pikcrvt"
    const val DEFAULT_CLASS = "dp1-3"
    
    // Debug
    const val DEBUG = false
    
    // Day names (Latvian)
    val DAY_NAMES = listOf(
        "Pirmdiena", 
        "Otrdiena", 
        "Trešdiena", 
        "Ceturtdiena", 
        "Piektdiena", 
        "Sestdiena", 
        "Svētdiena"
    )
}
