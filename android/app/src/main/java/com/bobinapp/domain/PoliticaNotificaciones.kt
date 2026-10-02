package com.bobinapp.domain

/**
 * Decide qué alertas se notifican. Código puro para poder probarlo sin Android.
 *
 * - Solo alertas urgentes (vencidas o para hoy) de categorías que el usuario no apagó.
 * - Nunca se repite una notificación de la misma alerta (clave estable de AlertEngine).
 * - En silencio nocturno (21:00–5:59) no se notifica nada; las alertas quedan para la siguiente revisión.
 */
object PoliticaNotificaciones {
    val CATEGORIAS = listOf("Reproducción", "Salud", "Manejo")
    private val NOCHE = (21..23) + (0..5)

    data class Decision(val notificar: List<Alerta>, val recordar: Set<String>)

    fun esHoraDeSilencio(hora: Int) = hora in NOCHE

    fun decidir(
        alertas: List<Alerta>,
        yaNotificadas: Set<String>,
        categoriasApagadas: Set<String>,
        silencioNocturno: Boolean,
        hora: Int,
    ): Decision {
        val urgentes = alertas.filter { it.severidad == Severidad.VENCIDA || it.severidad == Severidad.HOY }
        // Solo se recuerdan las que siguen vigentes, así el conjunto no crece sin límite.
        val vigentes = yaNotificadas intersect urgentes.map { it.clave }.toSet()
        if (silencioNocturno && esHoraDeSilencio(hora)) return Decision(emptyList(), vigentes)
        val nuevas = urgentes.filter { it.categoria !in categoriasApagadas && it.clave !in yaNotificadas }
        return Decision(nuevas, vigentes + nuevas.map { it.clave })
    }
}
