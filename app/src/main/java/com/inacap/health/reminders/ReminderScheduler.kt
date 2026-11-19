package com.inacap.health.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {
    const val TIPO_AGUA = "agua"
    const val TIPO_MOV = "mov"

    fun programar(context: Context, tipo: String) {
        val sharedPrefs = context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
        val activoKey = if (tipo == TIPO_AGUA) "recordatorio_agua_activo" else "recordatorio_mov_activo"
        val isActivo = sharedPrefs.getBoolean(activoKey, false)
        if (!isActivo) {
            cancelar(context, tipo)
            return
        }

        val intervalMillis = if (tipo == TIPO_AGUA) {
            val horas = sharedPrefs.getInt("recordatorio_agua_horas", 2)
            horas * 60 * 60 * 1000L
        } else {
            val min = sharedPrefs.getInt("recordatorio_mov_min", 60)
            min * 60 * 1000L
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("tipo", tipo)
        }
        val requestCode = if (tipo == TIPO_AGUA) 1001 else 1002
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + intervalMillis
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Programa los recordatorios activos que no tengan una alarma pendiente. */
    fun reprogramarSiFalta(context: Context) {
        val sharedPrefs = context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean("recordatorio_agua_activo", false) && !estaProgramado(context, TIPO_AGUA)) {
            programar(context, TIPO_AGUA)
        }
        if (sharedPrefs.getBoolean("recordatorio_mov_activo", false) && !estaProgramado(context, TIPO_MOV)) {
            programar(context, TIPO_MOV)
        }
    }

    private fun estaProgramado(context: Context, tipo: String): Boolean {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("tipo", tipo)
        }
        val requestCode = if (tipo == TIPO_AGUA) 1001 else 1002
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    fun cancelar(context: Context, tipo: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("tipo", tipo)
        }
        val requestCode = if (tipo == TIPO_AGUA) 1001 else 1002
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
