package com.example.edupageschedule

import android.content.Context
import com.google.gson.Gson
import java.io.File

object ScheduleStorage {
    private val gson = Gson()

    fun saveSchedule(context: Context, schedule: Schedule): Boolean {
        return saveAtomic(context, Constants.SCHEDULE_FILE, schedule)
    }

    fun loadSchedule(context: Context): Schedule? {
        return load(context, Constants.SCHEDULE_FILE, Schedule::class.java)
    }

    fun saveSubstitutions(context: Context, substitutions: SubstitutionData): Boolean {
        return saveAtomic(context, Constants.SUBSTITUTIONS_FILE, substitutions)
    }

    fun loadSubstitutions(context: Context): SubstitutionData? {
        return load(context, Constants.SUBSTITUTIONS_FILE, SubstitutionData::class.java)
    }

    private fun <T> saveAtomic(context: Context, fileName: String, data: T): Boolean {
        return try {
            val json = gson.toJson(data)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            val targetFile = File(context.filesDir, fileName)

            // Write to temp file
            tempFile.writeText(json)

            // Atomic rename
            if (targetFile.exists()) {
                if (!targetFile.delete()) {
                    if (Constants.DEBUG) println("Failed to delete target file $fileName")
                }
            }

            if (tempFile.renameTo(targetFile)) {
                true
            } else {
                // Fallback if rename fails
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun <T> load(context: Context, fileName: String, classOfT: Class<T>): T? {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return null

            val json = file.readText()
            gson.fromJson(json, classOfT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
