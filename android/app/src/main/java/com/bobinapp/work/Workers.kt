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
import com.bobinapp.domain.PoliticaNotificaciones
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
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
        val aj = container.ajustes.actual
        val alertas = AlertEngine.calcular(container.hato.todosConEventos(), LocalDate.now())
            .filter { it.clave !in aj.descartadas }
        val decision = PoliticaNotificaciones.decidir(
            alertas = alertas,
            yaNotificadas = container.ajustes.alertasNotificadas,
            categoriasApagadas = aj.notifApagadas,
            silencioNocturno = aj.silencioNocturno,
            hora = LocalTime.now().hour,
        )
        if (decision.notificar.isNotEmpty()) NotificationHelper.notificarAlertas(applicationContext, decision.notificar)
        container.ajustes.alertasNotificadas = decision.recordar
        return Result.success()
    }
}

/** Envía la analítica anónima pendiente. Solo corre con red y nunca molesta si falla. */
class TelemetriaWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
    override suspend fun doWork(): Result {
        val analitica = (applicationContext as BobinappApp).container.analitica
        return try {
            if (analitica.enviar()) Result.success() else Result.retry()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            Result.success()
        }
    }
}

object WorkScheduler {
    private const val SYNC_INMEDIATA = "sync-inmediata"
    private const val SYNC_PERIODICA = "sync-periodica"
    private const val ALERTAS_PERIODICAS = "alertas-periodicas"
    private const val ALERTAS_AHORA = "alertas-ahora"
    private const val TELEMETRIA = "telemetria"
    private const val TELEMETRIA_AHORA = "telemetria-ahora"

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
        wm.enqueueUniquePeriodicWork(
            TELEMETRIA, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TelemetriaWorker>(12, TimeUnit.HOURS).setConstraints(conRed).build(),
        )
    }

    /** Se llama cuando la app pasa a segundo plano: buen momento para enviar sin estorbar. */
    fun enviarTelemetria(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            TELEMETRIA_AHORA, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TelemetriaWorker>().setConstraints(conRed)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build(),
        )
    }

    fun observarSync(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SYNC_INMEDIATA)
}
