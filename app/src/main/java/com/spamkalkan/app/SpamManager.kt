package com.spamkalkan.app

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

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

    val BANK_NUMBERS = listOf(
        "02121234567", "02164444444", "08501234567",
        "+902121234567", "+902164444444", "+908501234567",
        "08503240330", "08504441444", "08503244444",
        "08504440444", "08503280606", "08504440606",
        "08503240444", "08504445588", "08503244050",
        "08504441242", "08503248484", "08504440400",
        "08503246767", "08504447474", "08503241717",
        "08504441000", "08503249898", "08504449898",
        "08503245353", "08504443535", "08503242525",
        "08504442525", "08503240606", "08504440909",
        "08503244848", "08504445353", "08503243636",
        "08504443636", "08503247878", "08504447878",
        "08503241212", "08504441212", "08503248989",
        "08504448989", "08503241616", "08504441616",
        "08503242121", "08504442121", "08503243434",
        "08504443434", "08503244747", "08504444747",
        "08503245656", "08504445656"
    )

    val BANK_PREFIXES = listOf(
        "0850324", "0850440", "0212444", "0216444",
        "04442", "04443", "04444", "04445", "04446",
        "04447", "04448", "04449"
    )

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

    fun getSpamNumbers(context: Context): List<String> {
        val json = prefs(context).getString(KEY_NUMBERS, "[]") ?: "[]"
        return jsonToList(json)
    }

    fun getSpamKeywords(context: Context): List<String> {
        val json = prefs(context).getString(KEY_KEYWORDS, "[]") ?: "[]"
        return jsonToList(json)
    }

    fun getSpamPrefixes(context: Context): List<String> {
        val json = prefs(context).getString(KEY_PREFIXES, "[\"+90850\",\"+90444\"]") ?: "[\"+90850\",\"+90444\"]"
        return jsonToList(json)
    }

    fun getSelectedPrefixes(context: Context): List<String> {
        val json = prefs(context).getString(KEY_SELECTED_PREFIXES, "[\"+90850\",\"+90444\"]") ?: "[\"+90850\",\"+90444\"]"
        return jsonToList(json)
    }

    fun setSelectedPrefixes(context: Context, prefixes: List<String>) {
        prefs(context).edit().putString(KEY_SELECTED_PREFIXES, listToJson(prefixes)).apply()
    }

    private fun jsonToList(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

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

        // 1. Spam listesi
        if (spamNumbers.any { it.replace(Regex("[\\s\\-()]"), "") == n }) {
            return CallResult.BLOCK_SPAM
        }

        // 2. Ön ek filtresi
        if (isBlockPrefixes(context)) {
            if (selectedPrefixes.any { n.startsWith(it.replace("+90", "0")) || n.startsWith(it) }) {
                return CallResult.BLOCK_PREFIX
            }
        }

        // 3. Yurtdışı
        if (isBlockInternational(context)) {
            if (!n.startsWith("+90") && !n.startsWith("0") && n.startsWith("+")) {
                return CallResult.SILENCE_INTL
            }
        }

        // 4. Banka
        if (isSilenceBanks(context)) {
            if (BANK_NUMBERS.any { it.replace(Regex("[\\s\\-()]"), "") == n } ||
                BANK_PREFIXES.any { n.startsWith(it) }) {
                return CallResult.SILENCE_BANK
            }
        }

        // 5. Tanımadığın
        if (isBlockUnknown(context)) {
            return CallResult.SILENCE_UNKNOWN
        }

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
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("config").document("spam_lists").get().await()
            val numbers = doc.get("numbers") as? List<*> ?: emptyList<Any>()
            val keywords = doc.get("keywords") as? List<*> ?: emptyList<Any>()
            val prefixes = doc.get("prefixes") as? List<*> ?: emptyList<Any>()

            prefs(context).edit()
                .putString(KEY_NUMBERS, listToJson(numbers.map { it.toString() }))
                .putString(KEY_KEYWORDS, listToJson(keywords.map { it.toString() }))
                .putString(KEY_PREFIXES, listToJson(prefixes.map { it.toString() }))
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

enum class CallResult {
    ALLOW, BLOCK_SPAM, BLOCK_PREFIX, SILENCE_BANK, SILENCE_INTL, SILENCE_UNKNOWN
}
