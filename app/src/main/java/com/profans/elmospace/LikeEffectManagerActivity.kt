package com.profans.elmospace

import android.app.AlertDialog
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class LikeEffectManagerActivity : ComponentActivity() {
    private lateinit var assetList: LinearLayout
    private lateinit var actionButton: TextView
    private val selectedForDelete = mutableSetOf<String>()
    private var manageMode = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowLayout.lockPhonePortrait(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_like_effect_manager)
        applyInsets()

        assetList = findViewById(R.id.likeEffectAssetList)
        actionButton = findViewById(R.id.likeEffectManagerAction)
        findViewById<View>(R.id.likeEffectManagerBack).setOnClickListener {
            if (manageMode) {
                exitManageMode()
            } else {
                finishWithTransition()
            }
        }
        actionButton.setOnClickListener {
            if (manageMode) {
                confirmDeleteSelected()
            } else {
                enterManageMode()
            }
        }
        findViewById<View>(R.id.likeEffectAddButton).setOnClickListener { openAddLikeEffect() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (manageMode) {
                    exitManageMode()
                } else {
                    finishWithTransition()
                }
            }
        })

        renderAssets()
    }

    override fun onResume() {
        super.onResume()
        renderAssets()
    }

    private fun renderAssets() {
        assetList.removeAllViews()
        val selectedId = AppPreferences.getLikeEffect(this)
        LikeEffectAssets.options(this).forEachIndexed { index, option ->
            if (index > 0) {
                assetList.addView(createDivider())
            }
            assetList.addView(createAssetRow(option, option.id == selectedId))
        }
    }

    private fun createAssetRow(option: LikeEffectOption, selected: Boolean): View {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_like_effect_asset, assetList, false)
        row.findViewById<ImageView>(R.id.likeEffectAssetPreview).apply {
            if (option.type == LikeEffectAssetType.CUSTOM) {
                setImageURI(Uri.fromFile(LikeEffectCustomAssetRepository.imageFile(this@LikeEffectManagerActivity, option.fileName)))
            } else {
                setImageResource(option.drawableRes)
            }
        }
        row.findViewById<TextView>(R.id.likeEffectAssetName).text = option.displayName
        val deletable = option.type != LikeEffectAssetType.BUILT_IN
        val checkedForDelete = selectedForDelete.contains(option.id)
        row.findViewById<TextView>(R.id.likeEffectAssetMeta).text = buildString {
            append(
                if (option.type == LikeEffectAssetType.BUILT_IN) {
                    getString(R.string.like_effect_asset_builtin)
                } else {
                    getString(R.string.like_effect_asset_custom)
                }
            )
            append(" · ")
            append(
                if (manageMode && !deletable) {
                    getString(R.string.like_effect_asset_builtin_locked)
                } else {
                    getString(R.string.like_effect_asset_tap_to_select)
                }
            )
        }
        row.findViewById<TextView>(R.id.likeEffectAssetStatus).text = when {
            manageMode && checkedForDelete -> getString(R.string.like_effect_asset_selected_mark)
            manageMode && deletable -> "○"
            manageMode -> ""
            selected -> getString(R.string.like_effect_asset_selected)
            else -> ""
        }
        row.alpha = when {
            manageMode && !deletable -> 0.48f
            selected -> 1f
            else -> 0.92f
        }
        row.setOnClickListener {
            if (manageMode) {
                if (!deletable) return@setOnClickListener
                if (!selectedForDelete.add(option.id)) {
                    selectedForDelete.remove(option.id)
                }
            } else {
                AppPreferences.setLikeEffect(this, option.id)
            }
            renderAssets()
        }
        return row
    }

    private fun enterManageMode() {
        manageMode = true
        selectedForDelete.clear()
        actionButton.setText(R.string.like_effect_delete)
        renderAssets()
    }

    private fun exitManageMode() {
        manageMode = false
        selectedForDelete.clear()
        actionButton.setText(R.string.like_effect_manage)
        renderAssets()
    }

    private fun confirmDeleteSelected() {
        val deletableIds = selectedForDelete.filter { id ->
            LikeEffectAssets.findSelection(this, id).type != LikeEffectAssetType.BUILT_IN
        }
        if (deletableIds.isEmpty()) {
            Toast.makeText(this, R.string.like_effect_no_deletable_selected, Toast.LENGTH_SHORT)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.like_effect_delete_confirm_title)
            .setMessage(getString(R.string.like_effect_delete_confirm_message, deletableIds.size))
            .setPositiveButton(R.string.like_effect_delete) { _, _ ->
                LikeEffectCustomAssetRepository.delete(this, deletableIds)
                if (AppPreferences.getLikeEffect(this) in deletableIds) {
                    AppPreferences.setLikeEffect(this, LikeEffectAssets.DEFAULT_ID)
                }
                exitManageMode()
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }

    private fun openAddLikeEffect() {
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, LikeEffectAddActivity::class.java), options.toBundle())
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = (16 * resources.displayMetrics.density).toInt()
            }
            setBackgroundColor(ContextCompat.getColor(this@LikeEffectManagerActivity, R.color.nav_divider))
        }
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.likeEffectManagerRoot)
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
}
