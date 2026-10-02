package com.bobinapp

import android.app.Application
import com.bobinapp.di.AppContainer
import com.bobinapp.work.NotificationHelper
import com.bobinapp.work.WorkScheduler

class BobinappApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registrarCierresInesperados()
        NotificationHelper.crearCanales(this)
        WorkScheduler.programarTareasPeriodicas(this)
        container.sembrarDemoSiHaceFalta()
    }

    /**
     * Si la app se cierra por un error, se anota el tipo y el lugar (sin datos del usuario)
     * para saber qué falla. Después se deja que Android haga lo de siempre.
     */
    private fun registrarCierresInesperados() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching { container.analitica.registrarCierreInesperado(error) }
            anterior?.uncaughtException(hilo, error)
        }
    }
}
