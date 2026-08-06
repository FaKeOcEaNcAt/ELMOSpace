package com.profans.elmospace

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class BrowsingHistoryEntry(
    val topicId: Long,
    val title: String,
    val author: String,
    val viewCount: Long,
    val firstReadAt: Long,
    val lastReadAt: Long,
    val readCount: Int,
    val url: String,
    val isLiked: Boolean
)

object BrowsingHistoryRepository {
    @Volatile
    private var helper: HistoryDatabaseHelper? = null
    private val databaseLock = Any()

    fun record(
        context: Context,
        topicId: Long,
        title: String,
        author: String,
        viewCount: Long,
        url: String,
        isLiked: Boolean = false,
        readAt: Long = System.currentTimeMillis()
    ) {
        synchronized(databaseLock) {
            val database = database(context)
            database.beginTransaction()
            try {
                val existingReadCount = database.query(
                    TABLE_HISTORY,
                    arrayOf(COLUMN_READ_COUNT),
                    "$COLUMN_TOPIC_ID = ?",
                    arrayOf(topicId.toString()),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }

                val values = ContentValues().apply {
                    put(COLUMN_TOPIC_ID, topicId)
                    put(COLUMN_TITLE, title)
                    put(COLUMN_AUTHOR, author)
                    put(COLUMN_VIEW_COUNT, viewCount)
                    put(COLUMN_LAST_READ_AT, readAt)
                    put(COLUMN_READ_COUNT, existingReadCount + 1)
                    put(COLUMN_URL, url)
                    if (existingReadCount == 0) {
                        put(COLUMN_FIRST_READ_AT, readAt)
                        put(COLUMN_IS_LIKED, if (isLiked) 1 else 0)
                    } else if (isLiked) {
                        put(COLUMN_IS_LIKED, 1)
                    }
                }
                if (existingReadCount == 0) {
                    database.insertOrThrow(TABLE_HISTORY, null, values)
                } else {
                    database.update(
                        TABLE_HISTORY,
                        values,
                        "$COLUMN_TOPIC_ID = ?",
                        arrayOf(topicId.toString())
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    fun setLiked(context: Context, topicId: Long, isLiked: Boolean) {
        synchronized(databaseLock) {
            val values = ContentValues().apply {
                put(COLUMN_IS_LIKED, if (isLiked) 1 else 0)
            }
            database(context).update(
                TABLE_HISTORY,
                values,
                "$COLUMN_TOPIC_ID = ?",
                arrayOf(topicId.toString())
            )
        }
    }

    fun query(
        context: Context,
        startInclusive: Long? = null,
        endExclusive: Long? = null,
        likedOnly: Boolean = false,
        searchQuery: String? = null
    ): List<BrowsingHistoryEntry> {
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        startInclusive?.let {
            clauses += "$COLUMN_LAST_READ_AT >= ?"
            arguments += it.toString()
        }
        endExclusive?.let {
            clauses += "$COLUMN_LAST_READ_AT < ?"
            arguments += it.toString()
        }
        if (likedOnly) clauses += "$COLUMN_IS_LIKED = 1"
        val normalizedQuery = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        normalizedQuery?.let {
            clauses += "($COLUMN_TITLE LIKE ? ESCAPE '\\' OR $COLUMN_AUTHOR LIKE ? ESCAPE '\\')"
            val pattern = "%${escapeLikePattern(it)}%"
            arguments += pattern
            arguments += pattern
        }

        return database(context).query(
            TABLE_HISTORY,
            arrayOf(
                COLUMN_TOPIC_ID,
                COLUMN_TITLE,
                COLUMN_AUTHOR,
                COLUMN_VIEW_COUNT,
                COLUMN_FIRST_READ_AT,
                COLUMN_LAST_READ_AT,
                COLUMN_READ_COUNT,
                COLUMN_URL,
                COLUMN_IS_LIKED
            ),
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            arguments.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null,
            null,
            "$COLUMN_LAST_READ_AT DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BrowsingHistoryEntry(
                            topicId = cursor.getLong(0),
                            title = cursor.getString(1),
                            author = cursor.getString(2),
                            viewCount = cursor.getLong(3),
                            firstReadAt = cursor.getLong(4),
                            lastReadAt = cursor.getLong(5),
                            readCount = cursor.getInt(6),
                            url = cursor.getString(7),
                            isLiked = cursor.getInt(8) == 1
                        )
                    )
                }
            }
        }
    }

    fun clear(context: Context) {
        synchronized(databaseLock) {
            val database = database(context)
            database.beginTransaction()
            try {
                database.delete(TABLE_HISTORY, null, null)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    fun databaseFile(context: Context): File =
        context.applicationContext.getDatabasePath(DATABASE_NAME)

    fun exportDatabaseSnapshot(context: Context, targetFile: File) {
        synchronized(databaseLock) {
            val database = database(context)
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            if (database.inTransaction()) error("数据库事务尚未结束")
            helper?.close()
            helper = null
            val source = databaseFile(context)
            source.inputStream().use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    fun replaceAll(
        context: Context,
        entries: List<BrowsingHistoryEntry>,
        onProgress: ((Int, Int) -> Unit)? = null
    ) {
        synchronized(databaseLock) {
            val database = database(context)
            database.beginTransaction()
            try {
                database.delete(TABLE_HISTORY, null, null)
                entries.forEachIndexed { index, entry ->
                    database.insertOrThrow(TABLE_HISTORY, null, entry.toContentValues())
                    onProgress?.invoke(index + 1, entries.size)
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    fun insertNewOnly(
        context: Context,
        entries: List<BrowsingHistoryEntry>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportApplyResult =
        applyEntries(context, entries, mergeExisting = false, onProgress = onProgress)

    fun merge(
        context: Context,
        entries: List<BrowsingHistoryEntry>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportApplyResult =
        applyEntries(context, entries, mergeExisting = true, onProgress = onProgress)

    fun withLockedDatabase(context: Context, block: (SQLiteDatabase) -> Unit) {
        synchronized(databaseLock) {
            block(database(context))
        }
    }

    private fun applyEntries(
        context: Context,
        entries: List<BrowsingHistoryEntry>,
        mergeExisting: Boolean,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportApplyResult {
        synchronized(databaseLock) {
            val database = database(context)
            var added = 0
            var updated = 0
            var duplicateSkipped = 0
            var diffSkipped = 0
            database.beginTransaction()
            try {
                entries.forEachIndexed { index, entry ->
                    val existing = queryByTopicId(database, entry.topicId)
                    if (existing == null) {
                        database.insertOrThrow(TABLE_HISTORY, null, entry.toContentValues())
                        added++
                    } else if (mergeExisting) {
                        val merged = existing.mergeWith(entry)
                        if (merged == existing) {
                            duplicateSkipped++
                        } else {
                            database.update(
                                TABLE_HISTORY,
                                merged.toContentValues(),
                                "$COLUMN_TOPIC_ID = ?",
                                arrayOf(merged.topicId.toString())
                            )
                            updated++
                        }
                    } else if (existing == entry) {
                        duplicateSkipped++
                    } else {
                        diffSkipped++
                    }
                    onProgress?.invoke(index + 1, entries.size)
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            return ImportApplyResult(added, updated, duplicateSkipped, diffSkipped, 0)
        }
    }

    private fun queryByTopicId(database: SQLiteDatabase, topicId: Long): BrowsingHistoryEntry? =
        database.query(
            TABLE_HISTORY,
            ENTRY_COLUMNS,
            "$COLUMN_TOPIC_ID = ?",
            arrayOf(topicId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toEntry()
        }

    private fun BrowsingHistoryEntry.mergeWith(other: BrowsingHistoryEntry): BrowsingHistoryEntry =
        copy(
            title = other.title.ifBlank { title },
            author = other.author.ifBlank { author },
            viewCount = maxOf(viewCount, other.viewCount),
            firstReadAt = minOf(firstReadAt, other.firstReadAt),
            lastReadAt = maxOf(lastReadAt, other.lastReadAt),
            readCount = maxOf(readCount, other.readCount),
            url = other.url.ifBlank { url },
            isLiked = isLiked || other.isLiked
        )

    private fun BrowsingHistoryEntry.toContentValues() = ContentValues().apply {
        put(COLUMN_TOPIC_ID, topicId)
        put(COLUMN_TITLE, title)
        put(COLUMN_AUTHOR, author)
        put(COLUMN_VIEW_COUNT, viewCount)
        put(COLUMN_FIRST_READ_AT, firstReadAt)
        put(COLUMN_LAST_READ_AT, lastReadAt)
        put(COLUMN_READ_COUNT, readCount)
        put(COLUMN_URL, url)
        put(COLUMN_IS_LIKED, if (isLiked) 1 else 0)
    }

    private fun android.database.Cursor.toEntry() = BrowsingHistoryEntry(
        topicId = getLong(0),
        title = getString(1),
        author = getString(2),
        viewCount = getLong(3),
        firstReadAt = getLong(4),
        lastReadAt = getLong(5),
        readCount = getInt(6),
        url = getString(7),
        isLiked = getInt(8) == 1
    )

    data class ImportApplyResult(
        val added: Int,
        val updated: Int,
        val duplicateSkipped: Int,
        val diffSkipped: Int,
        val failed: Int
    )

    const val DATABASE_NAME = "browsing_history.db"
    const val DATABASE_VERSION = 2
    const val MIN_SUPPORTED_DATABASE_VERSION = 2
    const val TABLE_HISTORY = "browsing_history"
    const val COLUMN_TOPIC_ID = "topic_id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_AUTHOR = "author"
    const val COLUMN_VIEW_COUNT = "view_count"
    const val COLUMN_FIRST_READ_AT = "first_read_at"
    const val COLUMN_LAST_READ_AT = "last_read_at"
    const val COLUMN_READ_COUNT = "read_count"
    const val COLUMN_URL = "url"
    const val COLUMN_IS_LIKED = "is_liked"
    val REQUIRED_COLUMNS = setOf(
        COLUMN_TOPIC_ID,
        COLUMN_TITLE,
        COLUMN_AUTHOR,
        COLUMN_VIEW_COUNT,
        COLUMN_FIRST_READ_AT,
        COLUMN_LAST_READ_AT,
        COLUMN_READ_COUNT,
        COLUMN_URL,
        COLUMN_IS_LIKED
    )
    val ENTRY_COLUMNS = arrayOf(
        COLUMN_TOPIC_ID,
        COLUMN_TITLE,
        COLUMN_AUTHOR,
        COLUMN_VIEW_COUNT,
        COLUMN_FIRST_READ_AT,
        COLUMN_LAST_READ_AT,
        COLUMN_READ_COUNT,
        COLUMN_URL,
        COLUMN_IS_LIKED
    )

    private fun database(context: Context): SQLiteDatabase {
        val current = helper
        if (current != null) return current.writableDatabase
        return synchronized(this) {
            val initialized = helper ?: HistoryDatabaseHelper(context.applicationContext).also {
                helper = it
            }
            initialized.writableDatabase
        }
    }

    private fun escapeLikePattern(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '\\', '%', '_' -> append('\\')
                }
                append(char)
            }
        }

    private class HistoryDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_HISTORY (
                    $COLUMN_TOPIC_ID INTEGER PRIMARY KEY,
                    $COLUMN_TITLE TEXT NOT NULL,
                    $COLUMN_AUTHOR TEXT NOT NULL,
                    $COLUMN_VIEW_COUNT INTEGER NOT NULL,
                    $COLUMN_FIRST_READ_AT INTEGER NOT NULL,
                    $COLUMN_LAST_READ_AT INTEGER NOT NULL,
                    $COLUMN_READ_COUNT INTEGER NOT NULL,
                    $COLUMN_URL TEXT NOT NULL,
                    $COLUMN_IS_LIKED INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX history_last_read_at ON $TABLE_HISTORY($COLUMN_LAST_READ_AT DESC)"
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                database.execSQL(
                    "ALTER TABLE $TABLE_HISTORY ADD COLUMN " +
                        "$COLUMN_IS_LIKED INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }

}
