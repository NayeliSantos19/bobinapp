package com.bobinapp.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bobinapp.MainActivity
import com.bobinapp.R
import com.bobinapp.domain.Alerta

object NotificationHelper {
    private const val CANAL_VIEJO = "alertas_hato"
    private const val ID_BASE = 1001
    const val EXTRA_DESTINO = "destino"

    /** Un canal por categoría: así el usuario también puede apagarlas desde los ajustes de Android. */
    private data class Canal(val id: String, val categoria: String, val nombre: String, val descripcion: String)

    private val CANALES = listOf(
        Canal("alertas_reproduccion", "Reproducción", "Reproducción", "Celos, inseminación, diagnóstico de preñez, partos y secado"),
        Canal("alertas_salud", "Salud", "Salud y medicamentos", "Dosis de vacunas y tratamientos, y retiro de leche y carne"),
        Canal("alertas_manejo", "Manejo", "Manejo", "Destetes y pesajes pendientes"),
    )

    private fun canalDe(categoria: String) = CANALES.firstOrNull { it.categoria == categoria } ?: CANALES.last()

    fun crearCanales(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(CANAL_VIEJO)
        nm.createNotificationChannelGroup(NotificationChannelGroup("alertas", context.getString(R.string.canal_alertas)))
        CANALES.forEach { c ->
            nm.createNotificationChannel(
                NotificationChannel(c.id, c.nombre, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = c.descripcion
                    group = "alertas"
                },
            )
        }
    }

    /** Abre la pantalla de notificaciones de Android para esta app. */
    fun abrirAjustesDelSistema(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun puedeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Una notificación por categoría, con el detalle de cada alerta. */
    fun notificarAlertas(context: Context, alertas: List<Alerta>) {
        if (alertas.isEmpty() || !puedeNotificar(context)) return
        val abrir = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DESTINO, "alertas")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alertas.groupBy { canalDe(it.categoria) }.forEach { (canal, grupo) ->
            val builder = NotificationCompat.Builder(context, canal.id)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
            if (grupo.size == 1) {
                val a = grupo.first()
                builder.setContentTitle(a.titulo).setContentText(a.detalle)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(a.detalle))
            } else {
                val estilo = NotificationCompat.InboxStyle()
                grupo.take(6).forEach { estilo.addLine("${it.titulo} · ${it.detalle}") }
                if (grupo.size > 6) estilo.setSummaryText("y ${grupo.size - 6} más")
                builder.setContentTitle("${grupo.size} alertas de ${canal.nombre.lowercase()}")
                    .setContentText(grupo.joinToString(" · ") { it.titulo })
                    .setStyle(estilo)
                    .setNumber(grupo.size)
            }
            try {
                NotificationManagerCompat.from(context).notify(ID_BASE + CANALES.indexOf(canal), builder.build())
            } catch (e: SecurityException) {
                // El usuario retiró el permiso entre la verificación y el aviso: no hay nada que hacer.
            }
        }
    }
}
