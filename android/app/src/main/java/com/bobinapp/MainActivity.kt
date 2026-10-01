package com.bobinapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobinapp.data.repository.Tema
import com.bobinapp.ui.navegacion.BobinappNavHost
import com.bobinapp.ui.theme.BobinappTheme
import com.bobinapp.work.NotificationHelper
import com.bobinapp.work.WorkScheduler

class MainActivity : ComponentActivity() {
    /** Pantalla a abrir cuando la app se lanza desde una notificación. */
    private var destino by mutableStateOf<String?>(null)

    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) WorkScheduler.revisarAlertasAhora(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        destino = intent?.getStringExtra(NotificationHelper.EXTRA_DESTINO)
        if (savedInstanceState == null) pedirPermisoNotificaciones()
        val ajustes = (application as BobinappApp).container.ajustes
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
                BobinappNavHost(
                    destinoInicial = destino,
                    onDestinoConsumido = { destino = null },
                    onPedirPermisoNotificaciones = ::pedirPermisoNotificaciones,
                )
            }
        }
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
}
