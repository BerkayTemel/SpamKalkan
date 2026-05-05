package com.spamkalkan.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object LogManager {

    private const val PREF_NAME = "spam_kalkan_logs"
    private const val KEY_LOGS = "call_logs"
    private const val MAX_LOGS = 200

    data class LogEntry(
        val number: String,
        val type: String,
        val time: String,
        val isCall: Boolean
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun addLog(context: Context, number: String, result: CallResult) {
        val type = when (result) {
            CallResult.BLOCK_SPAM -> "spam"
            CallResult.BLOCK_PREFIX -> "prefix"
            CallResult.SILENCE_BANK -> "bank"
            CallResult.SILENCE_INTL -> "international"
            CallResult.SILENCE_UNKNOWN -> "unknown"
            CallResult.ALLOW -> return
        }
        addEntry(context, number, type, true)
    }

    fun addSmsLog(context: Context, number: String, body: String) {
        addEntry(context, number, "sms_spam", false)
    }

    private fun addEntry(context: Context, number: String, type: String, isCall: Boolean) {
        val logs = getLogs(context).toMutableList()
        val time = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val entry = JSONObject().apply {
            put("number", number)
            put("type", type)
            put("time", time)
            put("isCall", isCall)
        }
        logs.add(0, entry)
        if (logs.size > MAX_LOGS) logs.removeAt(logs.size - 1)

        val arr = JSONArray()
        logs.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    fun getLogs(context: Context): List<JSONObject> {
        val json = prefs(context).getString(KEY_LOGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun clearLogs(context: Context) {
        prefs(context).edit().putString(KEY_LOGS, "[]").apply()
    }

    fun getStats(context: Context): Map<String, Int> {
        val logs = getLogs(context)
        return mapOf(
            "total" to logs.size,
            "spam" to logs.count { it.getString("type") == "spam" || it.getString("type") == "prefix" },
            "bank" to logs.count { it.getString("type") == "bank" },
            "sms" to logs.count { it.getString("type") == "sms_spam" },
            "intl" to logs.count { it.getString("type") == "international" }
        )
    }
}
