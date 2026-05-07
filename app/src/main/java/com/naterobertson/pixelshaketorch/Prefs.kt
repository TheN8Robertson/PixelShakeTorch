package com.naterobertson.pixelshaketorch

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "pixelshaketorch"

    const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
    const val KEY_SHAKE_THRESHOLD_G = "shake_threshold_g"

    const val DEFAULT_THRESHOLD_G = 2.7f
    const val MIN_THRESHOLD_G = 1.5f
    const val MAX_THRESHOLD_G = 8.0f
    const val THRESHOLD_STEP_G = 0.1f

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun threshold(prefs: SharedPreferences): Float =
        prefs.getFloat(KEY_SHAKE_THRESHOLD_G, DEFAULT_THRESHOLD_G)
}
