package com.guardian.track.util

// [Summary] Structured and concise implementation file.

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.guardian.track.data.local.entity.IncidentEntity
import com.guardian.track.model.toFormattedDate
import com.guardian.track.model.toFormattedTime
import java.io.IOException

object CsvExporter {
        val fileName = "emergency_detector_incidents_${System.currentTimeMillis()}.csv"
    fun export(context: Context, incidents: List<IncidentEntity>): Boolean {
        val fileName = "guardian_incidents_${System.currentTimeMillis()}.csv"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        return try {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                ?: return false

            resolver.openOutputStream(uri)?.use { stream ->
                val writer = stream.bufferedWriter()
                writer.write("Date,Time,Type,Latitude,Longitude,Synced\n")
                incidents.forEach { incident ->
                    writer.write(
                        "${incident.timestamp.toFormattedDate()}," +
                        "${incident.timestamp.toFormattedTime()}," +
                        "${incident.type}," +
                        "${incident.latitude}," +
                        "${incident.longitude}," +
                        "${incident.isSynced}\n"
                    )
                }
                writer.flush()
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}
