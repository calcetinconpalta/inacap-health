package com.inacap.health.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al reiniciar el teléfono Android borra las alarmas: aquí se vuelven a programar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.reprogramarSiFalta(context)
        }
    }
}
