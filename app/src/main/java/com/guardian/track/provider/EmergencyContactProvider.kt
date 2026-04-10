package com.guardian.track.provider

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.guardian.track.data.local.AppDatabase
import androidx.room.Room

/**
 * EmergencyContactProvider — exposes emergency contacts to other apps.
 *
 * URI contract (from spec):
 *   content://com.guardian.track.provider/emergency_contacts       → all contacts
 *   content://com.guardian.track.provider/emergency_contacts/{id}  → single contact
 *
 * Security: the Manifest declares readPermission="com.guardian.track.READ_EMERGENCY_CONTACTS"
 * which has protectionLevel="signature". Only apps signed with our certificate can query.
 *
 * ContentProvider runs on the main thread by default. For this simple case we use
 * a synchronous DAO method. For production, consider using a thread pool.
 *
 * WHY NOT inject with Hilt here?
 * ContentProviders are created by the OS before Application.onCreate() runs,
 * so Hilt's component isn't ready yet. We initialize Room manually here.
 */
class EmergencyContactProvider : ContentProvider() {

    private lateinit var db: AppDatabase

    companion object {
        const val AUTHORITY = "com.guardian.track.provider"
        const val TABLE = "emergency_contacts"

        // Column names — _id is the standard Android ContentProvider primary key name
        const val COL_ID = "_id"
        const val COL_NAME = "name"
        const val COL_PHONE = "phone_number"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$TABLE")

        // UriMatcher maps URIs to integer codes for easy switching
        private const val CODE_ALL = 1
        private const val CODE_SINGLE = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, TABLE, CODE_ALL)
            addURI(AUTHORITY, "$TABLE/#", CODE_SINGLE)  // # matches any number
        }
    }

    override fun onCreate(): Boolean {
        // Build Room manually — Hilt not available at this point
        db = Room.databaseBuilder(
            context!!.applicationContext,
            AppDatabase::class.java,
            "guardian_db"
        ).allowMainThreadQueries()  // acceptable here since provider already runs off main in practice
            .build()
        return true
    }

    /**
     * Returns a Cursor over emergency contacts.
     * MatrixCursor is an in-memory cursor — simple and efficient for small datasets.
     */
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

    /** MIME type for the content — required by ContentProvider contract. */
    override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        CODE_ALL -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$TABLE"
        CODE_SINGLE -> "vnd.android.cursor.item/vnd.$AUTHORITY.$TABLE"
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }

    // We don't expose insert/update/delete to external apps (no write permission declared)
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
