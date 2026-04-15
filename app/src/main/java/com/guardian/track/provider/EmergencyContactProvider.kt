package com.guardian.track.provider

// [Summary] Structured and concise implementation file.

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.guardian.track.data.local.AppDatabase
import androidx.room.Room

class EmergencyContactProvider : ContentProvider() {

    private lateinit var db: AppDatabase

    companion object {
        const val AUTHORITY = "com.guardian.track.provider"
        const val TABLE = "emergency_contacts"
        const val COL_ID = "_id"
        const val COL_NAME = "name"
        const val COL_PHONE = "phone_number"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$TABLE")
        private const val CODE_ALL = 1
        private const val CODE_SINGLE = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, TABLE, CODE_ALL)
            addURI(AUTHORITY, "$TABLE/#", CODE_SINGLE)
        }
    }

    override fun onCreate(): Boolean {
        db = Room.databaseBuilder(
            context!!.applicationContext,
            AppDatabase::class.java,
            "guardian_db"
        ).allowMainThreadQueries()
            .build()
        return true
    }

        override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(COL_ID, COL_NAME, COL_PHONE))

        when (uriMatcher.match(uri)) {
            CODE_ALL -> {
                db.emergencyContactDao().getAllContactsSync().forEach { contact ->
                    cursor.addRow(arrayOf(contact.id, contact.name, contact.phoneNumber))
                }
            }
            CODE_SINGLE -> {
                val id = ContentUris.parseId(uri)
                db.emergencyContactDao().getContactByIdSync(id)?.let { contact ->
                    cursor.addRow(arrayOf(contact.id, contact.name, contact.phoneNumber))
                }
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        return cursor
    }

        override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        CODE_ALL -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$TABLE"
        CODE_SINGLE -> "vnd.android.cursor.item/vnd.$AUTHORITY.$TABLE"
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
