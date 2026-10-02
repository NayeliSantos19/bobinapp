package com.bobinapp

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bobinapp.data.repository.Tema
import com.bobinapp.data.seguridad.Biometria
import com.bobinapp.ui.navegacion.BobinappNavHost
import com.bobinapp.ui.seguridad.LocalPedirIdentidad
import com.bobinapp.ui.seguridad.PantallaBloqueo
import com.bobinapp.ui.theme.BobinappTheme
import com.bobinapp.work.NotificationHelper
import com.bobinapp.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** FragmentActivity (y no ComponentActivity) porque BiometricPrompt la necesita; Compose funciona igual. */
class MainActivity : FragmentActivity() {
    /** Pantalla a abrir cuando la app se lanza desde una notificación. */
    private var destino by mutableStateOf<String?>(null)
    private var bloqueada by mutableStateOf(false)
    private var enSegundoPlanoDesde = 0L
    @Volatile private var datosListos = false
    private lateinit var biometria: Biometria

    private val container get() = (application as BobinappApp).container

    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) WorkScheduler.revisarAlertasAhora(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // La pantalla inicial (logo sobre verde) se instala antes de super.onCreate, como pide la librería.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !datosListos }
        esperarDatosIniciales()

        biometria = Biometria(this)
        val ajustes = container.ajustes
        // Se bloquea al abrir la app desde cero; tras una rotación se respeta el estado anterior.
        bloqueada = ajustes.actual.bloqueo && (savedInstanceState?.getBoolean(K_BLOQUEADA, true) ?: true)

        enableEdgeToEdge()
        destino = intent?.getStringExtra(NotificationHelper.EXTRA_DESTINO)
        if (savedInstanceState == null) pedirPermisoNotificaciones()
        setContent {
            val aj by ajustes.estado.collectAsState()
            val sistemaOscuro = isSystemInDarkTheme()
            val oscuro = when (aj.tema) {
                Tema.SISTEMA -> sistemaOscuro
                Tema.CLARO -> false
                Tema.OSCURO -> true
            }
            // Los íconos de la barra de estado siguen al tema elegido, no solo al del sistema.
            SideEffect {
                val estilo = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            BobinappTheme(oscuro = oscuro) {
                CompositionLocalProvider(LocalPedirIdentidad provides ::pedirIdentidad) {
                    Box(Modifier.fillMaxSize()) {
                        BobinappNavHost(
                            destinoInicial = destino,
                            onDestinoConsumido = { destino = null },
                            onPedirPermisoNotificaciones = ::pedirPermisoNotificaciones,
                        )
                        if (bloqueada) PantallaBloqueo(onDesbloquear = ::desbloquear)
                    }
                }
            }
        }
    }

    /**
     * Mantiene la pantalla inicial hasta que Room entrega el hato (normalmente < 1 s),
     * con un tope de 2 s para que nunca se quede pegada. Además mide el tiempo de arranque.
     */
    private fun esperarDatosIniciales() {
        lifecycleScope.launch {
            withTimeoutOrNull(2_000) { container.hato.observarHato().first() }
            datosListos = true
            if (!arranqueMedido) {
                arranqueMedido = true
                val ms = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                container.analitica.registrar("inicio_app", datos = mapOf("ms" to ms))
            }
        }
    }

    fun pedirIdentidad(titulo: String, onExito: () -> Unit) {
        if (Biometria.disponibilidad(this) != Biometria.Disponibilidad.LISTA) return
        biometria.pedir(titulo, "Usa tu huella, tu rostro o el PIN del teléfono", onExito)
    }

    private fun desbloquear() {
        // Si el usuario quitó el bloqueo de pantalla del teléfono, no hay con qué verificar: no dejarlo afuera.
        if (Biometria.disponibilidad(this) != Biometria.Disponibilidad.LISTA) { bloqueada = false; return }
        biometria.pedir("Desbloquear Bobinapp", "Usa tu huella, tu rostro o el PIN del teléfono") { bloqueada = false }
    }

    override fun onStart() {
        super.onStart()
        val fuera = SystemClock.elapsedRealtime() - enSegundoPlanoDesde
        if (container.ajustes.actual.bloqueo && enSegundoPlanoDesde > 0 && fuera > TIEMPO_PARA_BLOQUEAR_MS) bloqueada = true
    }

    override fun onStop() {
        super.onStop()
        enSegundoPlanoDesde = SystemClock.elapsedRealtime()
        WorkScheduler.enviarTelemetria(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(K_BLOQUEADA, bloqueada)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_DESTINO)?.let { destino = it }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.puedeNotificar(this)) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val K_BLOQUEADA = "bloqueada"
        /** Si la app estuvo en segundo plano más de 1 minuto, se vuelve a pedir la huella. */
        const val TIEMPO_PARA_BLOQUEAR_MS = 60_000L
        /** Solo se mide el primer arranque del proceso (arranque en frío). */
        var arranqueMedido = false
    }
}
