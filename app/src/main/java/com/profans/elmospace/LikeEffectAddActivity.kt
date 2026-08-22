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
import java.io.File
import java.util.Locale

class LikeEffectAddActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var nameInput: EditText
    private var selectedUri: Uri? = null
    private var selectedDisplayName: String = ""

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        selectedUri = uri
        selectedDisplayName = queryDisplayName(uri)
        preview.setImageURI(uri)
        if (nameInput.text.isNullOrBlank()) {
            nameInput.setText(selectedDisplayName.substringBeforeLast('.').take(30))
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
        AppAccentColor.tintOutlinedButton(findViewById(R.id.likeEffectChooseImage), this)
        AppAccentColor.tintOutlinedButton(findViewById(R.id.likeEffectSave), this)

        findViewById<View>(R.id.likeEffectAddBack).setOnClickListener { finishWithTransition() }
        findViewById<View>(R.id.likeEffectChooseImage).setOnClickListener { chooseImage() }
        findViewById<View>(R.id.likeEffectSave).setOnClickListener { saveSelectedImage() }
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
        if (!LikeEffectCustomAssetRepository.canAdd(this)) {
            Toast.makeText(this, R.string.like_effect_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }
        imagePicker.launch(arrayOf("image/png", "image/webp"))
    }

    private fun saveSelectedImage() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, R.string.like_effect_pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (!LikeEffectCustomAssetRepository.canAdd(this)) {
            Toast.makeText(this, R.string.like_effect_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }

        val result = runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("empty image")
            if (bytes.size > MAX_SOURCE_BYTES) {
                return@runCatching SaveResult.FileTooLarge
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@runCatching SaveResult.DecodeFailed
            val normalized = normalizeBitmap(bitmap)
            val fileName = "custom_${System.currentTimeMillis()}.png"
            val output = LikeEffectCustomAssetRepository.imageFile(this, fileName)
            output.outputStream().use { stream ->
                if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    error("compress failed")
                }
            }
            if (normalized !== bitmap) normalized.recycle()
            bitmap.recycle()
            LikeEffectCustomAssetRepository.add(
                this,
                nameInput.text?.toString().orEmpty().ifBlank {
                    selectedDisplayName.substringBeforeLast('.')
                },
                fileName
            )
            SaveResult.Success(output)
        }.getOrElse {
            SaveResult.Failed
        }

        when (result) {
            is SaveResult.Success -> {
                Toast.makeText(this, R.string.like_effect_save_success, Toast.LENGTH_SHORT).show()
                finishWithTransition()
            }
            SaveResult.FileTooLarge -> Toast.makeText(
                this,
                R.string.like_effect_file_too_large,
                Toast.LENGTH_SHORT
            ).show()
            SaveResult.DecodeFailed -> Toast.makeText(
                this,
                R.string.like_effect_decode_failed,
                Toast.LENGTH_SHORT
            ).show()
            SaveResult.Failed -> Toast.makeText(
                this,
                R.string.like_effect_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
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
        data class Success(val file: File) : SaveResult
        data object FileTooLarge : SaveResult
        data object DecodeFailed : SaveResult
        data object Failed : SaveResult
    }

    private companion object {
        private const val MAX_SOURCE_BYTES = 3 * 1024 * 1024
        private const val MAX_IMAGE_EDGE = 768
        private const val COMPRESS_IMAGE_URL =
            "https://www.iloveimg.com/zh-cn/compress-image"
        private const val REMOVE_BACKGROUND_URL =
            "https://www.iloveimg.com/zh-cn/remove-background"
    }
}
