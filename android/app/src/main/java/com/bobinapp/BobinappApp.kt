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
        NotificationHelper.crearCanales(this)
        WorkScheduler.programarTareasPeriodicas(this)
        container.sembrarDemoSiHaceFalta()
    }
}
