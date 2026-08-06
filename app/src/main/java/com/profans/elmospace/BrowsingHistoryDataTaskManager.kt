package com.profans.elmospace

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BrowsingHistoryDataTaskManager {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private val listeners = mutableSetOf<(State) -> Unit>()
    private val lock = Any()
    @Volatile
    private var state: State = State.Idle
    @Volatile
    private var preparedImport: PreparedImport? = null

    fun addListener(listener: (State) -> Unit) {
        synchronized(lock) { listeners += listener }
        listener(state)
    }

    fun removeListener(listener: (State) -> Unit) {
        synchronized(lock) { listeners -= listener }
    }

    fun isTaskRunning() = active.get()

    fun defaultExportFileName(startMillis: Long): String =
        "ELMOSPACEBrowseHistory" +
            SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date(startMillis)) +
            ".zip"

    fun startExport(context: Context, targetUri: Uri) {
        if (!active.compareAndSet(false, true)) {
            publish(State.Error("当前已有浏览数据任务正在进行"))
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            val tempDir = File(appContext.cacheDir, "browse_history_export/${UUID.randomUUID()}")
            val snapshot = File(tempDir, BrowsingHistoryRepository.DATABASE_NAME)
            val zipFile = File(tempDir, "export.zip")
            try {
                publish(State.Progress("正在导出浏览记录", "正在准备数据库", true))
                tempDir.mkdirs()
                BrowsingHistoryRepository.exportDatabaseSnapshot(appContext, snapshot)
                publish(State.Progress("正在导出浏览记录", "正在压缩数据库", true))
                ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(BrowsingHistoryRepository.DATABASE_NAME))
                    snapshot.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
                publish(State.Progress("正在导出浏览记录", "正在写入导出文件", true))
                appContext.contentResolver.openOutputStream(targetUri)?.use { output ->
                    zipFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("无法写入导出文件")
                publish(State.ExportSuccess(targetUri, nextEventId()))
            } catch (_: SecurityException) {
                publish(State.Error("导出失败：没有写入所选位置的权限"))
            } catch (_: Exception) {
                publish(State.Error("导出失败：无法完成浏览记录备份"))
            } finally {
                tempDir.deleteRecursively()
                active.set(false)
            }
        }
    }

    fun startPrepareImport(context: Context, sourceUri: Uri) {
        if (!active.compareAndSet(false, true)) {
            publish(State.Error("当前已有浏览数据任务正在进行"))
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            val taskId = UUID.randomUUID().toString()
            val taskDir = File(importCacheRoot(appContext), taskId)
            val sourceDir = File(taskDir, "source")
            val extractedDir = File(taskDir, "extracted")
            val sourceZip = File(sourceDir, "imported_backup.zip")
            try {
                preparedImport = null
                sourceDir.mkdirs()
                extractedDir.mkdirs()
                File(taskDir, "task_state.json").writeText("""{"state":"running"}""")
                copyImportZipToCache(appContext.contentResolver, sourceUri, sourceZip)
                validateZipSize(sourceZip)
                val dbFile = unzipAndFindDatabase(sourceZip, extractedDir)
                validateDatabase(dbFile)
                val importedEntries = readEntries(dbFile)
                val localEntries = BrowsingHistoryRepository.query(appContext).associateBy { it.topicId }
                val stats = analyze(localEntries, importedEntries)
                preparedImport = PreparedImport(taskDir, dbFile, importedEntries, stats)
                publish(State.ImportPreview(stats))
            } catch (error: InvalidBackupException) {
                taskDir.deleteRecursively()
                publish(State.Error(error.userMessage))
            } catch (_: SecurityException) {
                taskDir.deleteRecursively()
                publish(State.Error("导入失败：无法读取所选文件"))
            } catch (_: Exception) {
                taskDir.deleteRecursively()
                publish(State.Error("导入失败：无法处理该浏览数据备份"))
            } finally {
                active.set(false)
            }
        }
    }

    fun applyPreparedImport(context: Context, mode: ImportMode) {
        val prepared = preparedImport ?: run {
            publish(State.Error("导入失败：没有可用的导入任务"))
            return
        }
        if (!active.compareAndSet(false, true)) {
            publish(State.Error("当前已有浏览数据任务正在进行"))
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            try {
                validateDatabase(prepared.databaseFile)
                publish(State.Progress("正在导入浏览记录", "正在准备数据库事务", true))
                val result = when (mode) {
                    ImportMode.REPLACE -> {
                        publish(State.Progress("正在导入浏览记录", "正在清理原有浏览记录", true))
                        BrowsingHistoryRepository.replaceAll(appContext, prepared.entries) { done, total ->
                            publishImportProgress("正在导入备份记录", done, total)
                        }
                        BrowsingHistoryRepository.ImportApplyResult(
                            added = prepared.entries.size,
                            updated = 0,
                            duplicateSkipped = 0,
                            diffSkipped = 0,
                            failed = 0
                        )
                    }
                    ImportMode.ADD -> {
                        publish(State.Progress("正在导入浏览记录", "正在检查重复记录", true))
                        BrowsingHistoryRepository.insertNewOnly(appContext, prepared.entries) { done, total ->
                            publishImportProgress("正在添加新记录", done, total)
                        }
                    }
                    ImportMode.MERGE -> {
                        publish(State.Progress("正在导入浏览记录", "正在比较浏览记录", true))
                        BrowsingHistoryRepository.merge(appContext, prepared.entries) { done, total ->
                            publishImportProgress("正在合并浏览数据", done, total)
                        }
                    }
                }
                publish(State.Progress("正在导入浏览记录", "正在校验导入结果", true))
                BrowsingHistoryRepository.withLockedDatabase(appContext) { database ->
                    database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                        if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                            throw InvalidBackupException("导入后数据库校验失败")
                        }
                    }
                }
                prepared.taskDir.deleteRecursively()
                preparedImport = null
                publish(State.ImportSuccess(result, nextEventId()))
            } catch (_: Exception) {
                publish(State.Error("导入失败：未修改或已回滚本地浏览记录"))
            } finally {
                active.set(false)
            }
        }
    }

    fun clearHistory(context: Context) {
        if (!active.compareAndSet(false, true)) {
            publish(State.Error("当前已有浏览数据任务正在进行"))
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            try {
                publish(State.Progress("正在清除浏览记录", "正在清空浏览历史数据", true))
                BrowsingHistoryRepository.clear(appContext)
                publish(State.ClearSuccess(nextEventId()))
            } catch (_: Exception) {
                publish(State.Error("清除失败：无法清除浏览记录"))
            } finally {
                active.set(false)
            }
        }
    }

    fun discardPreparedImport() {
        preparedImport?.taskDir?.deleteRecursively()
        preparedImport = null
        publish(State.Idle)
    }

    fun consumeOneShotState(eventId: Long) {
        val current = state
        if (current is State.OneShot && current.eventId == eventId) {
            publish(State.Idle)
        }
    }

    fun importCacheRoot(context: Context) = File(context.cacheDir, "browse_history_import")

    fun cleanupOldImportCaches(context: Context) {
        if (isTaskRunning()) return
        if (preparedImport != null) return
        importCacheRoot(context).listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively()
        }
    }

    private fun copyImportZipToCache(
        resolver: ContentResolver,
        sourceUri: Uri,
        target: File
    ) {
        val totalBytes = queryOpenableSize(resolver, sourceUri).takeIf { it in 1..MAX_ZIP_BYTES }
        publish(State.Progress("正在准备导入", "正在复制导入文件", totalBytes == null))
        var copied = 0L
        resolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (copied > MAX_ZIP_BYTES) {
                        throw InvalidBackupException("该压缩包存在异常，无法导入")
                    }
                    totalBytes?.let {
                        val percent = ((copied * 100) / it).toInt().coerceIn(0, 100)
                        publish(State.Progress("正在准备导入", "正在复制导入文件 $percent%", false, percent))
                    }
                }
            }
        } ?: throw IOException("无法读取导入文件")
    }

    private fun queryOpenableSize(resolver: ContentResolver, uri: Uri): Long =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        } ?: -1L

    private fun validateZipSize(file: File) {
        publish(State.Progress("正在准备导入", "正在校验压缩包", true))
        if (!file.exists() || file.length() <= 0L || file.length() > MAX_ZIP_BYTES) {
            throw InvalidBackupException("该压缩包存在异常，无法导入")
        }
        ZipFile(file).use { zip ->
            if (zip.size() <= 0 || zip.size() > MAX_ZIP_ENTRIES) {
                throw InvalidBackupException("该压缩包存在异常，无法导入")
            }
        }
    }

    private fun unzipAndFindDatabase(zipFile: File, extractedDir: File): File {
        publish(State.Progress("正在准备导入", "正在解压导入文件", true))
        val rootPath = extractedDir.canonicalFile.toPath()
        val seen = mutableSetOf<String>()
        var count = 0
        var total = 0L
        var databaseFile: File? = null
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > MAX_ZIP_ENTRIES) throw InvalidBackupException("该压缩包存在异常，无法导入")
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.contains("../") || name.count { it == '/' } > MAX_ZIP_DEPTH) {
                    throw InvalidBackupException("该压缩包存在异常，无法导入")
                }
                val outputFile = File(extractedDir, name).canonicalFile
                if (!outputFile.toPath().startsWith(rootPath)) {
                    throw InvalidBackupException("该压缩包存在异常，无法导入")
                }
                if (!seen.add(outputFile.canonicalPath)) {
                    throw InvalidBackupException("该压缩包存在异常，无法导入")
                }
                outputFile.parentFile?.mkdirs()
                var written = 0L
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        total += read
                        if (written > MAX_SINGLE_EXTRACTED_FILE_BYTES || total > MAX_TOTAL_EXTRACTED_BYTES) {
                            throw InvalidBackupException("该压缩包存在异常，无法导入")
                        }
                    }
                }
                if (outputFile.name == BrowsingHistoryRepository.DATABASE_NAME) {
                    if (databaseFile != null) throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
                    databaseFile = outputFile
                }
                publish(State.Progress("正在准备导入", "正在解压文件 $count", true))
                zip.closeEntry()
            }
        }
        return databaseFile ?: throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
    }

    private fun validateDatabase(databaseFile: File) {
        publish(State.Progress("正在准备导入", "正在校验数据库", true))
        if (databaseFile.name != BrowsingHistoryRepository.DATABASE_NAME) {
            throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
        }
        val database = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
            if (integrity != "ok") throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (version !in BrowsingHistoryRepository.MIN_SUPPORTED_DATABASE_VERSION..BrowsingHistoryRepository.DATABASE_VERSION) {
                throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
            }
            var topicIdIsPrimaryKey = false
            val columns = database.rawQuery(
                "PRAGMA table_info(${BrowsingHistoryRepository.TABLE_HISTORY})",
                null
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        val columnName = cursor.getString(1)
                        add(columnName)
                        if (columnName == BrowsingHistoryRepository.COLUMN_TOPIC_ID) {
                            topicIdIsPrimaryKey = cursor.getInt(5) > 0
                        }
                    }
                }
            }
            if (!columns.containsAll(BrowsingHistoryRepository.REQUIRED_COLUMNS)) {
                throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
            }
            if (!topicIdIsPrimaryKey) {
                throw InvalidBackupException("该文件不是有效的 ELMOSPACE 浏览记录备份")
            }
        } finally {
            database.close()
        }
    }

    private fun readEntries(databaseFile: File): List<BrowsingHistoryEntry> {
        publish(State.Progress("正在准备导入", "正在分析浏览记录", true))
        val database = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            return database.query(
                BrowsingHistoryRepository.TABLE_HISTORY,
                BrowsingHistoryRepository.ENTRY_COLUMNS,
                null,
                null,
                null,
                null,
                "${BrowsingHistoryRepository.COLUMN_LAST_READ_AT} DESC"
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
        } finally {
            database.close()
        }
    }

    private fun analyze(
        localEntries: Map<Long, BrowsingHistoryEntry>,
        importedEntries: List<BrowsingHistoryEntry>
    ): ImportPreviewStats {
        var newRecords = 0
        var duplicates = 0
        var differences = 0
        importedEntries.forEach { entry ->
            val local = localEntries[entry.topicId]
            when {
                local == null -> newRecords++
                local == entry -> duplicates++
                else -> differences++
            }
        }
        return ImportPreviewStats(
            backupRecords = importedEntries.size,
            localRecords = localEntries.size,
            newRecords = newRecords,
            duplicates = duplicates,
            differences = differences
        )
    }

    private fun publishImportProgress(label: String, done: Int, total: Int) {
        val percent = if (total <= 0) 100 else ((done * 100L) / total).toInt().coerceIn(0, 100)
        publish(State.Progress("正在导入浏览记录", "$label\n已处理 $done / $total", false, percent))
    }

    private fun publish(newState: State) {
        state = newState
        val snapshot = synchronized(lock) { listeners.toList() }
        mainHandler.post { snapshot.forEach { it(newState) } }
    }

    private fun nextEventId() = System.nanoTime()

    private class InvalidBackupException(val userMessage: String) : RuntimeException(userMessage)

    data class ImportPreviewStats(
        val backupRecords: Int,
        val localRecords: Int,
        val newRecords: Int,
        val duplicates: Int,
        val differences: Int
    )

    enum class ImportMode { REPLACE, ADD, MERGE }

    sealed class State {
        interface OneShot {
            val eventId: Long
        }

        data object Idle : State()
        data class Progress(
            val title: String,
            val message: String,
            val indeterminate: Boolean,
            val percent: Int = 0
        ) : State()
        data class ExportSuccess(
            val uri: Uri,
            override val eventId: Long
        ) : State(), OneShot
        data class ImportPreview(val stats: ImportPreviewStats) : State()
        data class ImportSuccess(
            val result: BrowsingHistoryRepository.ImportApplyResult,
            override val eventId: Long
        ) : State(), OneShot
        data class ClearSuccess(
            override val eventId: Long
        ) : State(), OneShot
        data class Error(
            val message: String,
            override val eventId: Long = System.nanoTime()
        ) : State(), OneShot
    }

    private data class PreparedImport(
        val taskDir: File,
        val databaseFile: File,
        val entries: List<BrowsingHistoryEntry>,
        val stats: ImportPreviewStats
    )

    private const val MAX_ZIP_BYTES = 64L * 1024L * 1024L
    private const val MAX_SINGLE_EXTRACTED_FILE_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_EXTRACTED_BYTES = 96L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 8
    private const val MAX_ZIP_DEPTH = 3
}
