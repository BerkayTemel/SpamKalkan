package com.spamkalkan.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            Log.d("SpamKalkan", "SMS geldi: $sender - $body")

            if (SpamManager.containsSpamKeyword(context, body)) {
                Log.d("SpamKalkan", "SPAM SMS engellendi: $sender")
                LogManager.addSmsLog(context, sender, body)
                abortBroadcast()
                return
            }
        }
    }
}
