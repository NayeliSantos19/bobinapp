package com.bobinapp.data.demo

import androidx.room.withTransaction
import com.bobinapp.data.local.BobinappDatabase
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.SyncEstado
import com.bobinapp.data.local.entity.TipoEvento
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Hato de ejemplo con 13 animales y un año de historial, con fechas relativas a hoy para que
 * siempre haya alertas, gráficos y genealogía que mostrar. Marcado esDemo = true: nunca se sube.
 */
object DemoSeeder {

    suspend fun sembrar(db: BobinappDatabase, hoy: LocalDate = LocalDate.now()) {
        val (animales, eventos) = generar(hoy)
        db.withTransaction {
            db.animalDao().upsertTodos(animales)
            db.eventoDao().upsertTodos(eventos)
        }
    }

    fun generar(hoy: LocalDate): Pair<List<AnimalEntity>, List<EventoEntity>> {
        var semilla = 11L
        fun azar(): Double { semilla = semilla * 16807 % 2147483647; return semilla / 2147483647.0 }
        fun d(n: Int): LocalDate = hoy.plusDays(n.toLong())
        fun id() = UUID.randomUUID().toString()

        val animales = mutableListOf<AnimalEntity>()
        val eventos = mutableListOf<EventoEntity>()
        fun animal(arete: String, nombre: String, sexo: Sexo, raza: String?, dias: Int, madre: AnimalEntity? = null,
                   padre: AnimalEntity? = null, padreExterno: String? = null, razaTexto: String? = null, castrado: Boolean = false) =
            AnimalEntity(
                id = id(), arete = arete, nombre = nombre, sexo = sexo, razaId = raza, razaTexto = razaTexto,
                nacimiento = d(dias), madreId = madre?.id, padreId = padre?.id, padreExterno = padreExterno,
                castrado = castrado, syncEstado = SyncEstado.SINCRONIZADO, esDemo = true,
            ).also { animales += it }

        fun ev(a: AnimalEntity, tipo: TipoEvento, dia: Int, bloque: EventoEntity.() -> EventoEntity = { this }) {
            eventos += EventoEntity(id = id(), animalId = a.id, tipo = tipo, fecha = d(dia), syncEstado = SyncEstado.SINCRONIZADO, esDemo = true).bloque()
        }
        fun pesos(a: AnimalEntity, desde: Int, hasta: Int, paso: Int, w0: Double, w1: Double) {
            var t = desde
            while (t <= hasta) {
                val k = (t - desde).toDouble() / maxOf(1, hasta - desde)
                val kg = (w0 + (w1 - w0) * k + (azar() - .5) * w0 * .025).roundToInt().toDouble()
                ev(a, TipoEvento.PESO, t) { copy(kg = kg) }
                t += paso
            }
        }
        fun leche(a: AnimalEntity, desde: Int, hasta: Int, l0: Double, l1: Double) {
            for (t in desde..hasta) {
                val k = (t - desde).toDouble() / maxOf(1, hasta - desde)
                val l = ((l0 + (l1 - l0) * k + (azar() - .5) * 2) * 10).roundToInt() / 10.0
                ev(a, TipoEvento.LECHE, t) { copy(litros = l) }
            }
        }

        val nube = animal("SV-0102", "Nube", Sexo.H, "pardo_suizo", -4050, padreExterno = "Toro Pardo Suizo «Duque»")
        val tornado = animal("SV-0417", "Tornado", Sexo.M, "brahman", -1880, padreExterno = "Toro Brahman reg. 88213")
        val lucero = animal("SV-0233", "Lucero", Sexo.H, "pardo_suizo", -2200, madre = nube, padreExterno = "Semen Brown Swiss «Payssli»")
        val perla = animal("SV-0561", "Perla", Sexo.H, "pardo_suizo", -125, madre = lucero, padreExterno = "Semen Brown Swiss «Payssli»")
        val canela = animal("SV-0298", "Canela", Sexo.H, "girolando", -1700)
        val pinto = animal("SV-0572", "Pinto", Sexo.M, null, -72, madre = canela, padre = tornado, razaTexto = "Girolando × Brahman")
        val estrella = animal("SV-0344", "Estrella", Sexo.H, "jersey", -1450)
        val luna = animal("SV-0389", "Luna", Sexo.H, "holstein", -1100)
        val rayo = animal("SV-0540", "Rayo", Sexo.M, null, -236, madre = luna, padre = tornado, razaTexto = "Holstein × Brahman")
        val mariposa = animal("SV-0187", "Mariposa", Sexo.H, "brahman_rojo", -2600)
        val chispa = animal("SV-0466", "Chispa", Sexo.H, null, -640, madre = mariposa, padre = tornado, razaTexto = "Brahman rojo × Brahman")
        val sultan = animal("SV-0503", "Sultán", Sexo.M, "brangus", -430, padreExterno = "Toro Brangus «Hércules»")
        val bravo = animal("SV-0451", "Bravo", Sexo.M, "simbrah", -680, castrado = true)

        pesos(nube, -360, 0, 90, 610.0, 596.0); pesos(tornado, -360, 0, 90, 842.0, 880.0)
        pesos(lucero, -360, 0, 90, 565.0, 548.0); pesos(canela, -360, 0, 90, 470.0, 482.0)
        pesos(estrella, -360, 0, 90, 410.0, 402.0); pesos(luna, -360, 0, 90, 590.0, 575.0)
        pesos(mariposa, -360, 0, 90, 515.0, 548.0); pesos(perla, -125, 0, 30, 38.0, 148.0)
        pesos(pinto, -72, 0, 15, 34.0, 96.0); pesos(rayo, -236, 0, 30, 40.0, 206.0)
        pesos(chispa, -360, 0, 30, 214.0, 332.0); pesos(sultan, -360, 0, 30, 124.0, 382.0)
        pesos(bravo, -410, -50, 30, 250.0, 428.0)

        ev(lucero, TipoEvento.PARTO, -125) { copy(criaId = perla.id) }; leche(lucero, -124, 0, 17.5, 13.0)
        ev(lucero, TipoEvento.CELO, -60)
        ev(lucero, TipoEvento.INSEMINACION, -50) { copy(toro = "Semen Pardo Suizo «Jongleur»", tecnico = "J. Martínez") }
        ev(lucero, TipoEvento.PALPACION, -12) { copy(resultado = ResultadoPalpacion.PRENADA) }
        ev(canela, TipoEvento.PARTO, -72) { copy(criaId = pinto.id) }; leche(canela, -71, 0, 15.5, 13.2)
        ev(canela, TipoEvento.CELO, -19) { copy(nota = "Celo corto, no se sirvió") }
        ev(estrella, TipoEvento.PARTO, -200) { copy(nota = "Cría macho vendida al destete") }; leche(estrella, -150, 0, 11.5, 9.0)
        ev(estrella, TipoEvento.CELO, 0) { copy(nota = "Detectada montando a las 6:00") }
        ev(luna, TipoEvento.PARTO, -236) { copy(criaId = rayo.id) }; leche(luna, -150, 0, 19.0, 14.5)
        ev(luna, TipoEvento.CELO, -39)
        ev(luna, TipoEvento.INSEMINACION, -38) { copy(toro = "Semen Holstein «Doorman»", tecnico = "J. Martínez") }
        ev(luna, TipoEvento.TRATAMIENTO, -3) {
            copy(diagnostico = "Mastitis clínica, cuarto posterior derecho", producto = "Cefquinoma intramamaria",
                dosis = "1 jeringa cada 12 h", retiroDias = 5, proximaFecha = d(0), costo = 18.5)
        }
        ev(mariposa, TipoEvento.PARTO, -640) { copy(criaId = chispa.id) }
        ev(mariposa, TipoEvento.INSEMINACION, -265) { copy(toro = "${tornado.arete} · ${tornado.nombre}", toroId = tornado.id) }
        ev(mariposa, TipoEvento.PALPACION, -220) { copy(resultado = ResultadoPalpacion.PRENADA) }
        ev(sultan, TipoEvento.TRATAMIENTO, -40) { copy(diagnostico = "Neumonía", producto = "Oxitetraciclina LA", dosis = "20 ml IM", retiroDias = 28, costo = 12.0) }
        ev(perla, TipoEvento.VACUNA, -6) { copy(producto = "Brucelosis RB51", dosis = "2 ml SC", costo = 3.5) }

        // Plan sanitario del hato (solo animales con más de 90 días en la fecha de aplicación)
        for (a in animales.toList()) {
            fun nacido(dia: Int) = ChronoUnit.DAYS.between(a.nacimiento, d(dia)) >= 90
            if (nacido(-330)) ev(a, TipoEvento.VACUNA, -330) { copy(producto = "Triple bovina", dosis = "5 ml SC", proximaFecha = d(-150), costo = 0.85) }
            if (nacido(-150)) ev(a, TipoEvento.VACUNA, -150) { copy(producto = "Triple bovina", dosis = "5 ml SC", proximaFecha = d(30), costo = 0.85) }
            if (nacido(-300)) ev(a, TipoEvento.VACUNA, -300) { copy(producto = "Rabia paralítica bovina", dosis = "2 ml IM", proximaFecha = d(65), costo = 1.25) }
            for (t in listOf(-350, -260, -170, -80)) {
                if (nacido(t)) ev(a, TipoEvento.VACUNA, t) { copy(producto = "Ivermectina 1%", dosis = "1 ml/50 kg", proximaFecha = d(t + 90), costo = 1.6) }
            }
            if (nacido(-200)) ev(a, TipoEvento.VACUNA, -200) { copy(producto = "Vitaminas ADE", dosis = "5 ml IM", costo = 1.1) }
        }
        return animales to eventos
    }
}
