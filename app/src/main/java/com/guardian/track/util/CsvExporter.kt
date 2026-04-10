package com.guardian.track.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.guardian.track.data.local.entity.IncidentEntity
import com.guardian.track.model.toFormattedDate
import com.guardian.track.model.toFormattedTime
import java.io.IOException

/**
 * Exports incident history to a CSV file in the public Documents folder.
 *
 * Uses MediaStore (Scoped Storage API) — required on Android 10+.
 * We don't need WRITE_EXTERNAL_STORAGE permission with MediaStore on API 29+.
 *
 * The file can be found in: Documents/guardian_incidents_<timestamp>.csv
 */
object CsvExporter {

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

                // Header row
                writer.write("Date,Time,Type,Latitude,Longitude,Synced\n")

                // Data rows
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
