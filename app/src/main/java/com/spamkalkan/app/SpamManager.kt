package com.spamkalkan.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SpamManager {

    private const val PREF_NAME = "spam_kalkan_prefs"
    private const val KEY_NUMBERS = "spam_numbers"
    private const val KEY_KEYWORDS = "spam_keywords"
    private const val KEY_PREFIXES = "spam_prefixes"
    private const val KEY_PROTECTION = "protection_on"
    private const val KEY_BLOCK_UNKNOWN = "block_unknown"
    private const val KEY_SILENCE_BANKS = "silence_banks"
    private const val KEY_BLOCK_PREFIXES = "block_prefixes"
    private const val KEY_BLOCK_KEYWORDS = "block_keywords"
    private const val KEY_BLOCK_INTL = "block_international"
    private const val KEY_SELECTED_PREFIXES = "selected_prefixes"
    private const val KEY_SUBSCRIBED = "is_subscribed"

    private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/spamkalkan/databases/(default)/documents/config/spam_lists?key=AIzaSyCor27zYiK6PyxPgh54y_AbaleZAVBJkXE"

    val BANK_PREFIXES = listOf("0850", "0444", "04442", "04443", "04444", "04445", "04446", "04447", "04448", "04449")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isProtectionOn(context: Context) = prefs(context).getBoolean(KEY_PROTECTION, true)
    fun isBlockUnknown(context: Context) = prefs(context).getBoolean(KEY_BLOCK_UNKNOWN, false)
    fun isSilenceBanks(context: Context) = prefs(context).getBoolean(KEY_SILENCE_BANKS, true)
    fun isBlockPrefixes(context: Context) = prefs(context).getBoolean(KEY_BLOCK_PREFIXES, true)
    fun isBlockKeywords(context: Context) = prefs(context).getBoolean(KEY_BLOCK_KEYWORDS, true)
    fun isBlockInternational(context: Context) = prefs(context).getBoolean(KEY_BLOCK_INTL, false)
    fun isSubscribed(context: Context) = prefs(context).getBoolean(KEY_SUBSCRIBED, false)

    fun setProtection(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_PROTECTION, value).apply()
    fun setBlockUnknown(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_BLOCK_UNKNOWN, value).apply()
    fun setSilenceBanks(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SILENCE_BANKS, value).apply()
    fun setBlockPrefixes(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_BLOCK_PREFIXES, value).apply()
    fun setBlockKeywords(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_BLOCK_KEYWORDS, value).apply()
    fun setBlockInternational(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_BLOCK_INTL, value).apply()
    fun setSubscribed(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SUBSCRIBED, value).apply()

    fun getSpamNumbers(context: Context): List<String> = jsonToList(prefs(context).getString(KEY_NUMBERS, "[]") ?: "[]")
    fun getSpamKeywords(context: Context): List<String> = jsonToList(prefs(context).getString(KEY_KEYWORDS, "[]") ?: "[]")

    fun getSelectedPrefixes(context: Context): List<String> {
        val json = prefs(context).getString(KEY_SELECTED_PREFIXES, "[\"+90850\",\"+90444\"]") ?: "[\"+90850\",\"+90444\"]"
        return jsonToList(json)
    }

    fun setSelectedPrefixes(context: Context, prefixes: List<String>) {
        prefs(context).edit().putString(KEY_SELECTED_PREFIXES, listToJson(prefixes)).apply()
    }

    private fun jsonToList(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    private fun listToJson(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    fun classifyNumber(context: Context, number: String): CallResult {
        if (!isProtectionOn(context)) return CallResult.ALLOW
        val n = number.replace(Regex("[\\s\\-()]"), "")
        val spamNumbers = getSpamNumbers(context)
        val selectedPrefixes = getSelectedPrefixes(context)

        if (spamNumbers.any { it.replace(Regex("[\\s\\-()]"), "") == n }) return CallResult.BLOCK_SPAM

        if (isBlockPrefixes(context)) {
            val normalized = selectedPrefixes.map { it.replace("+90", "0") }
            if (normalized.any { n.startsWith(it) }) return CallResult.BLOCK_PREFIX
        }

        if (isBlockInternational(context) && n.startsWith("+") && !n.startsWith("+90")) return CallResult.SILENCE_INTL

        if (isSilenceBanks(context) && BANK_PREFIXES.any { n.startsWith(it) }) return CallResult.SILENCE_BANK

        if (isBlockUnknown(context)) return CallResult.SILENCE_UNKNOWN

        return CallResult.ALLOW
    }

    fun containsSpamKeyword(context: Context, message: String): Boolean {
        if (!isBlockKeywords(context)) return false
        val keywords = getSpamKeywords(context)
        val lower = message.lowercase()
        return keywords.any { lower.contains(it.lowercase()) }
    }

    suspend fun updateFromFirebase(context: Context) {
        try {
            val url = URL(FIRESTORE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val fields = json.getJSONObject("fields")

            fun parseArray(fieldName: String): List<String> {
                return try {
                    val arr = fields.getJSONObject(fieldName).getJSONObject("arrayValue").getJSONArray("values")
                    (0 until arr.length()).map { arr.getJSONObject(it).getString("stringValue") }
                } catch (e: Exception) { emptyList() }
            }

            val numbers = parseArray("numbers")
            val keywords = parseArray("keywords")
            val prefixes = parseArray("prefixes")

            prefs(context).edit()
                .putString(KEY_NUMBERS, listToJson(numbers))
                .putString(KEY_KEYWORDS, listToJson(keywords))
                .putString(KEY_PREFIXES, listToJson(prefixes))
                .apply()

            android.util.Log.d("SpamKalkan", "Firebase güncellendi: ${numbers.size} numara, ${keywords.size} kelime")
        } catch (e: Exception) {
            android.util.Log.e("SpamKalkan", "Firebase hatası: ${e.message}")
        }
    }
}

enum class CallResult { ALLOW, BLOCK_SPAM, BLOCK_PREFIX, SILENCE_BANK, SILENCE_INTL, SILENCE_UNKNOWN }
