package com.bobinapp.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/** Las fechas se guardan como texto ISO (YYYY-MM-DD) para que ordenen y comparen bien en SQL. */
class Converters {
    @TypeConverter
    fun fechaATexto(fecha: LocalDate?): String? = fecha?.toString()

    @TypeConverter
    fun textoAFecha(texto: String?): LocalDate? = texto?.let(LocalDate::parse)
}
