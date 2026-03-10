package com.example.tilemaster

import android.content.Context

class HighScoreManager(context: Context) {

    private val prefs = context.getSharedPreferences("high_scores", Context.MODE_PRIVATE)

    fun getHighScores(): List<Long> {
        val raw = prefs.getString(KEY_SCORES, null) ?: return emptyList()
        return raw.split(",")
            .mapNotNull { it.toLongOrNull() }
            .sorted()
            .take(MAX_ENTRIES)
    }

    fun addScore(timeMillis: Long): Boolean {
        val scores = getHighScores().toMutableList()
        scores.add(timeMillis)
        scores.sort()
        val top = scores.take(MAX_ENTRIES)
        prefs.edit().putString(KEY_SCORES, top.joinToString(",")).apply()
        return timeMillis == top.first()
    }

    companion object {
        private const val KEY_SCORES = "scores"
        private const val MAX_ENTRIES = 5
    }
}
