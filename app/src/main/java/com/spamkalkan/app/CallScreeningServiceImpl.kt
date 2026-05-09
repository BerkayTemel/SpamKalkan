package com.spamkalkan.app

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        Log.d("SpamKalkan", "Gelen arama: $number")

        val result = SpamManager.classifyNumber(applicationContext, number)
        Log.d("SpamKalkan", "Sonuç: $result")

        val response = when (result) {
            CallResult.BLOCK_SPAM, CallResult.BLOCK_PREFIX -> {
                LogManager.addLog(applicationContext, number, result)
                CallResponse.Builder()
                    .setRejectCall(true)
                    .setDisallowCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .setSilenceCall(true)
                    .build()
            }
            CallResult.SILENCE_BANK, CallResult.SILENCE_INTL, CallResult.SILENCE_UNKNOWN -> {
                LogManager.addLog(applicationContext, number, result)
                CallResponse.Builder()
                    .setRejectCall(false)
                    .setDisallowCall(false)
                    .setSilenceCall(true)
                    .setSkipNotification(true)
                    .build()
            }
            CallResult.ALLOW -> {
                CallResponse.Builder()
                    .setRejectCall(false)
                    .setDisallowCall(false)
                    .setSilenceCall(false)
                    .build()
            }
        }
        respondToCall(callDetails, response)
    }
}
