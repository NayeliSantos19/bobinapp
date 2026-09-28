package com.bobinapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        setContent {
            BobinappTheme {
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
