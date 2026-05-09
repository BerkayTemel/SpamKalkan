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
        if (!SpamManager.isBlockKeywords(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val sender = message.originatingAddress ?: ""
            val body = message.messageBody ?: ""

            if (SpamManager.containsSpamKeyword(context, body)) {
                Log.d("SpamKalkan", "SPAM SMS tespit edildi: $sender")
                LogManager.addSmsLog(context, sender, body)

                // Spam olarak işaretle - type 3 = spam Android'de
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, sender)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, 3) // 3 = draft kullanarak gizle
                    }
                    context.contentResolver.insert(Uri.parse("content://sms"), values)
                } catch (e: Exception) {
                    Log.e("SpamKalkan", "SMS kaydetme hatası: ${e.message}")
                }

                // Broadcast'i durdur - varsayılan SMS uygulamasıysak çalışır
                try { abortBroadcast() } catch (e: Exception) {}
                return
            }
        }
    }
}
