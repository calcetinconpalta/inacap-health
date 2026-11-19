package com.inacap.health.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.inacap.health.MainActivity
import com.inacap.health.R
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tipo = intent.getStringExtra("tipo") ?: return

        crearCanalNotificaciones(context)

        val horaActual = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (horaActual in 8..21) {
            mostrarNotificacion(context, tipo)
        }

        val sharedPrefs = context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
        val activoKey = if (tipo == ReminderScheduler.TIPO_AGUA) "recordatorio_agua_activo" else "recordatorio_mov_activo"
        val isActivo = sharedPrefs.getBoolean(activoKey, false)
        if (isActivo) {
            ReminderScheduler.programar(context, tipo)
        }
    }

    private fun crearCanalNotificaciones(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "recordatorios"
            val name = "Recordatorios"
            val descriptionText = "Canal para recordatorios de salud y descanso"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun mostrarNotificacion(context: Context, tipo: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (tipo == ReminderScheduler.TIPO_AGUA) 2001 else 2002,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (titulo, texto) = if (tipo == ReminderScheduler.TIPO_AGUA) {
            "Recordatorio de agua" to "Un buen momento para tomar un vaso de agua"
        } else {
            "Pausa de movimiento" to "Estírate y muévete unos minutos, tu cuerpo lo agradecerá"
        }

        val notificationId = if (tipo == ReminderScheduler.TIPO_AGUA) 1001 else 1002

        val builder = NotificationCompat.Builder(context, "recordatorios")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
