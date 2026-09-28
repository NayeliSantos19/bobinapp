package com.bobinapp.data.remote

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato con el backend (carpeta /backend del repositorio). */
interface BobinappApi {
    @POST("api/fincas")
    suspend fun crearFinca(@Body cuerpo: CrearFincaRequest): CrearFincaResponse

    @POST("api/sync/push")
    suspend fun push(@Body cuerpo: PushRequest): PushResponse

    @GET("api/sync/cambios")
    suspend fun cambios(@Query("desde") desde: Long, @Query("limite") limite: Int = 500): CambiosResponse

    @POST("api/identificar")
    suspend fun identificar(@Body cuerpo: IdentificarRequest): IdentificacionDto

    /** 204 = guardada · 409 = el servidor ya tiene una más nueva · 404 = el animal aún no llegó al servidor. */
    @PUT("api/fotos/{animalId}")
    suspend fun subirFoto(
        @Path("animalId") animalId: String,
        @Header("X-Foto-Actualizada-En") version: Long,
        @Body imagen: RequestBody,
    ): Response<Unit>

    @GET("api/fotos/{animalId}")
    suspend fun bajarFoto(@Path("animalId") animalId: String): Response<ResponseBody>
}

@Serializable
data class AnimalDto(
    val id: String,
    val arete: String,
    val nombre: String? = null,
    val sexo: String,
    val razaId: String? = null,
    val razaTexto: String? = null,
    val nacimiento: String,
    val madreId: String? = null,
    val padreId: String? = null,
    val padreExterno: String? = null,
    val castrado: Boolean = false,
    val estado: String = "activa",
    val notas: String? = null,
    val fotoActualizadaEn: Long? = null,
    val eliminado: Boolean = false,
    val actualizadoEn: Long,
    val version: Long? = null,
)

@Serializable
data class EventoDto(
    val id: String,
    val animalId: String,
    val tipo: String,
    val fecha: String,
    val kg: Double? = null,
    val litros: Double? = null,
    val producto: String? = null,
    val dosis: String? = null,
    val diagnostico: String? = null,
    val proximaFecha: String? = null,
    val retiroDias: Int? = null,
    val costo: Double? = null,
    val toro: String? = null,
    val toroId: String? = null,
    val tecnico: String? = null,
    val resultado: String? = null,
    val criaId: String? = null,
    val nota: String? = null,
    val eliminado: Boolean = false,
    val actualizadoEn: Long,
    val version: Long? = null,
)

@Serializable
data class PushRequest(val animales: List<AnimalDto>, val eventos: List<EventoDto>)

@Serializable
data class PushResponse(val aplicados: Aplicados, val conflictos: Conflictos) {
    @Serializable
    data class Aplicados(val animales: Int, val eventos: Int)

    @Serializable
    data class Conflictos(val animales: List<String> = emptyList(), val eventos: List<String> = emptyList())
}

@Serializable
data class CambiosResponse(
    val animales: List<AnimalDto>,
    val eventos: List<EventoDto>,
    val cursor: Long,
    val hayMas: Boolean,
)

@Serializable
data class CrearFincaRequest(val nombre: String)

@Serializable
data class CrearFincaResponse(val finca: FincaDto, val token: String)

@Serializable
data class FincaDto(val id: String, val nombre: String)

@Serializable
data class IdentificarRequest(val imagenBase64: String, val mediaType: String = "image/jpeg")

@Serializable
data class IdentificacionDto(
    val esBovino: Boolean = true,
    val razaId: String? = null,
    val razaNombre: String = "",
    val confianza: Int = 0,
    val esCruce: Boolean = false,
    val cruceDe: List<String> = emptyList(),
    val rasgos: List<String> = emptyList(),
    val alternativas: List<Alternativa> = emptyList(),
    val nota: String = "",
) {
    @Serializable
    data class Alternativa(val razaId: String? = null, val razaNombre: String = "", val confianza: Int = 0)
}
