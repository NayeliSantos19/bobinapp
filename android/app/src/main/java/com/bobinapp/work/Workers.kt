package com.bobinapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bobinapp.BobinappApp
import com.bobinapp.data.repository.ResultadoSync
import com.bobinapp.domain.AlertEngine
import com.bobinapp.domain.Severidad
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import retrofit2.HttpException

/**
 * Sube la cola de cambios y descarga los del servidor.
 * WorkManager solo lo ejecuta cuando hay red (constraint CONNECTED): si el vaquero registró datos
 * en el potrero sin señal, el trabajo queda en espera y corre solo al recuperar conexión,
 * incluso con la app cerrada.
 */
class SyncWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BobinappApp).container
        return try {
            when (container.sync.sincronizar()) {
                ResultadoSync.SinFinca -> Result.success()
                is ResultadoSync.Ok -> Result.success()
            }
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            // 4xx: problema de datos o de token, reintentar no lo arregla. 5xx: sí vale reintentar.
            if (e.code() >= 500) Result.retry() else Result.failure()
        }
    }
}

/** Revisa el hato en segundo plano y notifica las alertas urgentes nuevas. */
class AlertWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BobinappApp).container
        val ajustes = container.ajustes
        val alertas = AlertEngine.calcular(container.hato.todosConEventos(), LocalDate.now())
            .filter { it.clave !in ajustes.actual.descartadas }
        val urgentes = alertas.filter { it.severidad == Severidad.VENCIDA || it.severidad == Severidad.HOY }
        val yaNotificadas = ajustes.alertasNotificadas
        val nuevas = urgentes.filter { it.clave !in yaNotificadas }
        if (nuevas.isNotEmpty()) NotificationHelper.notificarAlertas(applicationContext, nuevas)
        // Solo recordamos las que siguen vigentes, así el conjunto no crece sin límite.
        ajustes.alertasNotificadas = urgentes.map { it.clave }.toSet()
        return Result.success()
    }
}

object WorkScheduler {
    private const val SYNC_INMEDIATA = "sync-inmediata"
    private const val SYNC_PERIODICA = "sync-periodica"
    private const val ALERTAS_PERIODICAS = "alertas-periodicas"
    private const val ALERTAS_AHORA = "alertas-ahora"

    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Se llama después de cada escritura local. Si no hay red, queda en espera hasta que vuelva. */
    fun sincronizarAhora(context: Context) {
        val peticion = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: si ya hay una sincronización corriendo, la nueva se encadena detrás
        // para no perder cambios hechos mientras la primera subía.
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_INMEDIATA, ExistingWorkPolicy.APPEND_OR_REPLACE, peticion)
    }

    fun revisarAlertasAhora(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ALERTAS_AHORA, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AlertWorker>().build(),
        )
    }

    fun programarTareasPeriodicas(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SYNC_PERIODICA, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(conRed).build(),
        )
        wm.enqueueUniquePeriodicWork(
            ALERTAS_PERIODICAS, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    fun observarSync(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SYNC_INMEDIATA)
}
