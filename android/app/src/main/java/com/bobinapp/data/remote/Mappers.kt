package com.bobinapp.data.remote

import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.SyncEstado
import com.bobinapp.data.local.entity.TipoEvento
import java.time.LocalDate

fun AnimalEntity.aDto() = AnimalDto(
    id = id, arete = arete, nombre = nombre, sexo = sexo.name, razaId = razaId, razaTexto = razaTexto,
    nacimiento = nacimiento.toString(), madreId = madreId, padreId = padreId, padreExterno = padreExterno,
    castrado = castrado, estado = estado.name.lowercase(), notas = notas, fotoActualizadaEn = fotoActualizadaEn,
    eliminado = eliminado, actualizadoEn = actualizadoEn,
)

fun AnimalDto.aEntidad() = AnimalEntity(
    id = id, arete = arete, nombre = nombre, sexo = Sexo.valueOf(sexo), razaId = razaId, razaTexto = razaTexto,
    nacimiento = LocalDate.parse(nacimiento), madreId = madreId, padreId = padreId, padreExterno = padreExterno,
    castrado = castrado, estado = EstadoAnimal.valueOf(estado.uppercase()), notas = notas,
    fotoActualizadaEn = fotoActualizadaEn, eliminado = eliminado, actualizadoEn = actualizadoEn, syncEstado = SyncEstado.SINCRONIZADO, esDemo = false,
)

fun EventoEntity.aDto() = EventoDto(
    id = id, animalId = animalId, tipo = tipo.name, fecha = fecha.toString(), kg = kg, litros = litros,
    producto = producto, dosis = dosis, diagnostico = diagnostico, proximaFecha = proximaFecha?.toString(),
    retiroDias = retiroDias, costo = costo, toro = toro, toroId = toroId, tecnico = tecnico,
    resultado = resultado?.name, criaId = criaId, nota = nota, eliminado = eliminado, actualizadoEn = actualizadoEn,
)

fun EventoDto.aEntidad() = EventoEntity(
    id = id, animalId = animalId, tipo = TipoEvento.valueOf(tipo), fecha = LocalDate.parse(fecha), kg = kg,
    litros = litros, producto = producto, dosis = dosis, diagnostico = diagnostico,
    proximaFecha = proximaFecha?.let(LocalDate::parse), retiroDias = retiroDias, costo = costo, toro = toro,
    toroId = toroId, tecnico = tecnico, resultado = resultado?.let(ResultadoPalpacion::valueOf), criaId = criaId,
    nota = nota, eliminado = eliminado, actualizadoEn = actualizadoEn, syncEstado = SyncEstado.SINCRONIZADO,
)
