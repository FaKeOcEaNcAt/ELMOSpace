package com.profans.elmospace

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

class LikeEffectAddActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var nameInput: EditText
    private lateinit var chooseButton: View
    private lateinit var saveButton: View
    private var selectedUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var selectedDisplayName: String = ""
    private var selectionGeneration = 0
    private var isSaving = false
    private val imageExecutor = Executors.newSingleThreadExecutor()

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val previousSuggestion = selectedDisplayName.substringBeforeLast('.').take(30)
        val shouldUpdateName = nameInput.text.isNullOrBlank() ||
            nameInput.text.toString() == previousSuggestion
        selectedUri = uri
        selectedDisplayName = queryDisplayName(uri)
        preview.setImageDrawable(null)
        selectedBitmap?.recycle()
        selectedBitmap = null
        saveButton.isEnabled = false
        if (shouldUpdateName) {
            nameInput.setText(selectedDisplayName.substringBeforeLast('.').take(30))
        }
        val generation = ++selectionGeneration
        imageExecutor.execute {
            val result = loadSelectedImage(uri)
            runOnUiThread {
                if (isDestroyed || generation != selectionGeneration) {
                    if (result is ImageLoadResult.Ready) result.bitmap.recycle()
                    return@runOnUiThread
                }
                when (result) {
                    is ImageLoadResult.Ready -> {
                        selectedBitmap = result.bitmap
                        preview.setImageBitmap(result.bitmap)
                        saveButton.isEnabled = true
                    }
                    ImageLoadResult.FileTooLarge -> showImageError(R.string.like_effect_file_too_large)
                    ImageLoadResult.DecodeFailed -> showImageError(R.string.like_effect_decode_failed)
                    ImageLoadResult.Failed -> showImageError(R.string.like_effect_save_failed)
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowLayout.lockPhonePortrait(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_like_effect_add)
        applyInsets()

        preview = findViewById(R.id.likeEffectAddPreview)
        nameInput = findViewById(R.id.likeEffectNameInput)
        chooseButton = findViewById(R.id.likeEffectChooseImage)
        saveButton = findViewById(R.id.likeEffectSave)
        AppAccentColor.tintOutlinedButton(chooseButton, this)
        AppAccentColor.tintOutlinedButton(saveButton, this)
        saveButton.isEnabled = false

        findViewById<View>(R.id.likeEffectAddBack).setOnClickListener { finishWithTransition() }
        chooseButton.setOnClickListener { chooseImage() }
        saveButton.setOnClickListener { saveSelectedImage() }
        findViewById<View>(R.id.likeEffectCompressTool).setOnClickListener {
            openExternalTool(COMPRESS_IMAGE_URL)
        }
        findViewById<View>(R.id.likeEffectRemoveBgTool).setOnClickListener {
            openExternalTool(REMOVE_BACKGROUND_URL)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithTransition()
        })
    }

    private fun chooseImage() {
        if (isSaving) return
        if (!LikeEffectCustomAssetRepository.canAdd(this)) {
            Toast.makeText(this, R.string.like_effect_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }
        imagePicker.launch(arrayOf("image/png", "image/webp"))
    }

    private fun saveSelectedImage() {
        val bitmap = selectedBitmap
        if (selectedUri == null || bitmap == null) {
            Toast.makeText(this, R.string.like_effect_pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (isSaving) return
        if (!LikeEffectCustomAssetRepository.canAdd(this)) {
            Toast.makeText(this, R.string.like_effect_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }

        val name = nameInput.text?.toString().orEmpty().ifBlank {
            selectedDisplayName.substringBeforeLast('.')
        }
        isSaving = true
        saveButton.isEnabled = false
        chooseButton.isEnabled = false
        imageExecutor.execute {
            val result = runCatching {
                val fileName = "custom_${System.currentTimeMillis()}.png"
                val output = LikeEffectCustomAssetRepository.imageFile(this, fileName)
                try {
                    output.outputStream().use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            error("compress failed")
                        }
                    }
                    if (Thread.currentThread().isInterrupted) error("save cancelled")
                    LikeEffectCustomAssetRepository.add(this, name, fileName)
                    SaveResult.Success
                } catch (error: Throwable) {
                    output.delete()
                    throw error
                }
            }.getOrElse { SaveResult.Failed }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                isSaving = false
                chooseButton.isEnabled = true
                saveButton.isEnabled = true
                when (result) {
                    SaveResult.Success -> {
                        Toast.makeText(this, R.string.like_effect_save_success, Toast.LENGTH_SHORT).show()
                        finishWithTransition()
                    }
                    SaveResult.Failed -> Toast.makeText(
                        this,
                        R.string.like_effect_save_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadSelectedImage(uri: Uri): ImageLoadResult {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    if (Thread.currentThread().isInterrupted) return ImageLoadResult.Failed
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_SOURCE_BYTES) return ImageLoadResult.FileTooLarge
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return ImageLoadResult.Failed
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                bounds.outWidth > MAX_SOURCE_EDGE || bounds.outHeight > MAX_SOURCE_EDGE ||
                bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS ||
                bounds.outMimeType !in SUPPORTED_IMAGE_MIME_TYPES
            ) {
                return ImageLoadResult.DecodeFailed
            }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_EDGE) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return ImageLoadResult.DecodeFailed
            val normalized = normalizeBitmap(decoded)
            if (normalized !== decoded) decoded.recycle()
            ImageLoadResult.Ready(normalized)
        } catch (_: Exception) {
            ImageLoadResult.Failed
        } catch (_: OutOfMemoryError) {
            ImageLoadResult.Failed
        }
    }

    private fun showImageError(message: Int) {
        selectedUri = null
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun normalizeBitmap(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_IMAGE_EDGE) return bitmap
        val scale = MAX_IMAGE_EDGE.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun queryDisplayName(uri: Uri): String {
        val fallback = "自定义表情包"
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    fallback
                }
            } ?: fallback
        }.getOrDefault(fallback)
    }

    override fun onDestroy() {
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun openExternalTool(url: String) {
        try {
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.settings_enter,
                R.anim.activity_hold
            )
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), options.toBundle())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.likeEffectAddRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithTransition() {
        finish()
        overridePendingTransition(R.anim.activity_hold, R.anim.settings_exit)
    }

    private sealed interface SaveResult {
        data object Success : SaveResult
        data object Failed : SaveResult
    }

    private sealed interface ImageLoadResult {
        data class Ready(val bitmap: Bitmap) : ImageLoadResult
        data object FileTooLarge : ImageLoadResult
        data object DecodeFailed : ImageLoadResult
        data object Failed : ImageLoadResult
    }

    private companion object {
        private const val MAX_SOURCE_BYTES = 3 * 1024 * 1024
        private const val MAX_IMAGE_EDGE = 768
        private const val MAX_SOURCE_EDGE = 16_384
        private const val MAX_SOURCE_PIXELS = 100_000_000L
        private val SUPPORTED_IMAGE_MIME_TYPES = setOf("image/png", "image/webp")
        private const val COMPRESS_IMAGE_URL =
            "https://www.iloveimg.com/zh-cn/compress-image"
        private const val REMOVE_BACKGROUND_URL =
            "https://www.iloveimg.com/zh-cn/remove-background"
    }
}
