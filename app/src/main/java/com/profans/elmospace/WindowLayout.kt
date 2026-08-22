package com.profans.elmospace

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration

object WindowLayout {
    private const val TABLET_MIN_WIDTH_DP = 600
    private const val EXPANDED_MIN_WIDTH_DP = 840

    fun isTabletLayout(context: Context): Boolean =
        context.resources.configuration.screenWidthDp >= TABLET_MIN_WIDTH_DP

    fun isTabletDevice(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP

    fun isExpandedLayout(context: Context): Boolean =
        context.resources.configuration.screenWidthDp >= EXPANDED_MIN_WIDTH_DP

    fun isTabletLandscapeLayout(context: Context): Boolean {
        val configuration = context.resources.configuration
        return isTabletDevice(context) &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun lockPhonePortrait(activity: Activity) {
        activity.requestedOrientation = if (isTabletDevice(activity)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
