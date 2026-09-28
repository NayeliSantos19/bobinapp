package com.bobinapp.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bobinapp.MainActivity
import com.bobinapp.R
import com.bobinapp.domain.Alerta

object NotificationHelper {
    private const val CANAL_ALERTAS = "alertas_hato"
    private const val ID_RESUMEN = 1001
    const val EXTRA_DESTINO = "destino"

    fun crearCanales(context: Context) {
        val canal = NotificationChannel(
            CANAL_ALERTAS, context.getString(R.string.canal_alertas), NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.canal_alertas_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun puedeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificarAlertas(context: Context, alertas: List<Alerta>) {
        if (alertas.isEmpty() || !puedeNotificar(context)) return
        val abrir = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DESTINO, "alertas")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CANAL_ALERTAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (alertas.size == 1) {
            val a = alertas.first()
            builder.setContentTitle(a.titulo).setContentText(a.detalle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.detalle))
        } else {
            val estilo = NotificationCompat.InboxStyle()
            alertas.take(6).forEach { estilo.addLine("${it.titulo} · ${it.detalle}") }
            builder.setContentTitle("${alertas.size} alertas del hato para hoy")
                .setContentText(alertas.joinToString(" · ") { it.titulo })
                .setStyle(estilo)
                .setNumber(alertas.size)
        }
        try {
            NotificationManagerCompat.from(context).notify(ID_RESUMEN, builder.build())
        } catch (e: SecurityException) {
            // El usuario retiró el permiso entre la verificación y el aviso: no hay nada que hacer.
        }
    }
}
