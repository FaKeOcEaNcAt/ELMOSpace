package com.profans.elmospace

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BrowsingHistoryEntry(
    val topicId: Long,
    val title: String,
    val author: String,
    val viewCount: Long,
    val lastReadAt: Long,
    val readCount: Int,
    val url: String,
    val isLiked: Boolean
)

object BrowsingHistoryRepository {
    @Volatile
    private var helper: HistoryDatabaseHelper? = null

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

    fun setLiked(context: Context, topicId: Long, isLiked: Boolean) {
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

    fun query(
        context: Context,
        startInclusive: Long? = null,
        endExclusive: Long? = null,
        likedOnly: Boolean = false
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

        return database(context).query(
            TABLE_HISTORY,
            arrayOf(
                COLUMN_TOPIC_ID,
                COLUMN_TITLE,
                COLUMN_AUTHOR,
                COLUMN_VIEW_COUNT,
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
                            lastReadAt = cursor.getLong(4),
                            readCount = cursor.getInt(5),
                            url = cursor.getString(6),
                            isLiked = cursor.getInt(7) == 1
                        )
                    )
                }
            }
        }
    }

    fun clear(context: Context) {
        database(context).delete(TABLE_HISTORY, null, null)
    }

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

    private const val DATABASE_NAME = "browsing_history.db"
    private const val DATABASE_VERSION = 2
    private const val TABLE_HISTORY = "browsing_history"
    private const val COLUMN_TOPIC_ID = "topic_id"
    private const val COLUMN_TITLE = "title"
    private const val COLUMN_AUTHOR = "author"
    private const val COLUMN_VIEW_COUNT = "view_count"
    private const val COLUMN_FIRST_READ_AT = "first_read_at"
    private const val COLUMN_LAST_READ_AT = "last_read_at"
    private const val COLUMN_READ_COUNT = "read_count"
    private const val COLUMN_URL = "url"
    private const val COLUMN_IS_LIKED = "is_liked"
}
