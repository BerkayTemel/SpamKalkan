package com.spamkalkan.app

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SpamManager.isProtectionOn(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val sender = message.originatingAddress ?: ""
            val body = message.messageBody ?: ""
            Log.d("SpamKalkan", "SMS geldi: $sender")

            if (SpamManager.containsSpamKeyword(context, body)) {
                Log.d("SpamKalkan", "SPAM SMS engellendi: $sender")
                LogManager.addSmsLog(context, sender, body)

                // Spam klasörüne taşı (2 = spam)
                try {
                    val values = ContentValues().apply {
                        put("address", sender)
                        put("body", body)
                        put("date", System.currentTimeMillis())
                        put("type", 2) // inbox=1, spam=2
                        put("read", 0)
                        put("thread_id", getOrCreateThread(context, sender))
                    }
                    context.contentResolver.insert(Uri.parse("content://sms/inbox"), values)
                } catch (e: Exception) {
                    Log.e("SpamKalkan", "SMS kaydetme hatası: ${e.message}")
                }

                abortBroadcast()
                return
            }
        }
    }

    private fun getOrCreateThread(context: Context, address: String): Long {
        return try {
            val uri = Uri.parse("content://mms-sms/threadID?recipient=$address")
            val cursor = context.contentResolver.query(uri, arrayOf("_id"), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) { 0L }
    }
}
