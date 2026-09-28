package com.bobinapp.di

import android.content.Context
import com.bobinapp.data.ConnectivityObserver
import com.bobinapp.data.fotos.FotosAnimales
import com.bobinapp.data.local.BobinappDatabase
import com.bobinapp.data.remote.ApiProvider
import com.bobinapp.data.repository.AjustesStore
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.data.repository.RazasRepository
import com.bobinapp.data.repository.SyncRepository
import com.bobinapp.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inyección de dependencias manual: un solo lugar donde se construye el grafo de objetos.
 * Se eligió en vez de Hilt para mantener el proyecto sin generación de código extra.
 */
class AppContainer(context: Context) {
    val contexto: Context = context.applicationContext
    private val app = contexto
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: BobinappDatabase = BobinappDatabase.crear(app)
    val ajustes = AjustesStore(app)
    val apiProvider = ApiProvider(ajustes)
    val razas = RazasRepository(app)
    val conectividad = ConnectivityObserver(app)
    val fotos = FotosAnimales(app)
    val hato = HatoRepository(db, fotos, alCambiar = {
        WorkScheduler.sincronizarAhora(app)
        WorkScheduler.revisarAlertasAhora(app)
    })
    val sync = SyncRepository(db, apiProvider, ajustes, fotos)

    /** La primera vez que se abre la app, carga el hato de ejemplo para que no se vea vacía. */
    fun sembrarDemoSiHaceFalta() {
        scope.launch {
            if (!ajustes.demoSembrado && db.animalDao().contar() == 0) hato.cargarDemo()
            ajustes.demoSembrado = true
            WorkScheduler.revisarAlertasAhora(app)
        }
    }
}
