package com.tracediary.app.data.ai

import android.util.Log

internal object AiPerformanceLog {
    private const val TAG = "TraceDiaryAiPerf"

    fun debug(message: () -> String) {
        Log.d(TAG, message())
    }
}
