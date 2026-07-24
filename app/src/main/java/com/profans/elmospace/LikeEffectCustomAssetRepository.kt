package com.profans.elmospace

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LikeEffectCustomAssetRepository {
    private const val DIR_NAME = "like_effects"
    private const val METADATA_FILE = "metadata.json"
    const val MAX_CUSTOM_ASSETS = 20

    fun directory(context: Context): File {
        return File(context.filesDir, DIR_NAME).apply { mkdirs() }
    }

    fun imageFile(context: Context, fileName: String): File {
        return File(directory(context), fileName)
    }

    fun list(context: Context): List<LikeEffectOption> {
        val metadata = metadataFile(context)
        if (!metadata.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(metadata.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val displayName = item.optString("displayName")
                    val fileName = item.optString("fileName")
                    if (
                        id.isNotBlank() &&
                        displayName.isNotBlank() &&
                        fileName.isNotBlank() &&
                        imageFile(context, fileName).exists()
                    ) {
                        add(
                            LikeEffectOption(
                                id = id,
                                displayName = displayName,
                                type = LikeEffectAssetType.CUSTOM,
                                fileName = fileName
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun canAdd(context: Context): Boolean {
        return list(context).size < MAX_CUSTOM_ASSETS
    }

    fun add(context: Context, displayName: String, fileName: String): LikeEffectOption {
        val safeName = displayName.trim().ifBlank { "自定义表情包" }.take(30)
        val option = LikeEffectOption(
            id = "custom_${System.currentTimeMillis()}",
            displayName = safeName,
            type = LikeEffectAssetType.CUSTOM,
            fileName = fileName
        )
        val options = list(context).toMutableList().apply { add(option) }
        writeMetadata(context, options)
        return option
    }

    fun delete(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val deleteSet = ids.toSet()
        val kept = mutableListOf<LikeEffectOption>()
        list(context).forEach { option ->
            if (option.id in deleteSet) {
                imageFile(context, option.fileName).delete()
            } else {
                kept.add(option)
            }
        }
        writeMetadata(context, kept)
    }

    private fun writeMetadata(context: Context, options: List<LikeEffectOption>) {
        val array = JSONArray()
        options.forEach { option ->
            array.put(
                JSONObject()
                    .put("id", option.id)
                    .put("displayName", option.displayName)
                    .put("fileName", option.fileName)
            )
        }
        metadataFile(context).writeText(array.toString(2), Charsets.UTF_8)
    }

    private fun metadataFile(context: Context): File {
        return File(directory(context), METADATA_FILE)
    }
}
