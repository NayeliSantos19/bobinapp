# Bobinapp

[![CI](https://github.com/NayeliSantos19/bobinapp/actions/workflows/ci.yml/badge.svg)](https://github.com/NayeliSantos19/bobinapp/actions/workflows/ci.yml)

App Android para la gestión de ganado bovino pensada para el campo: funciona sin señal, lleva la trazabilidad y genealogía de cada res, muestra analíticas del hato, avisa de celos, partos y dosis pendientes, e identifica la raza de un animal con una foto.

| | |
|---|---|
| **App** | Kotlin · Jetpack Compose · Material 3 · Room (SQLite) · WorkManager · Retrofit · kotlinx.serialization |
| **API** | Node.js 22 · Express · PostgreSQL 16 · zod |
| **Pruebas** | JUnit (reglas de negocio), pruebas instrumentadas de Room, pruebas de integración de la API contra PostgreSQL real |
| **Despliegue** | Render (blueprint `render.yaml`) o Docker Compose |
| **CI** | GitHub Actions (`.github/workflows/ci.yml`): pruebas de la API contra PostgreSQL y compilación + pruebas de la app en cada push |

![Panel de analíticas](docs/capturas/panel.png)

<table>
<tr>
<td><img src="docs/capturas/ficha-animal.png" alt="Ficha del animal"></td>
<td><img src="docs/capturas/genealogia.png" alt="Árbol genealógico"></td>
</tr>
<tr>
<td><img src="docs/capturas/alertas.png" alt="Alertas"></td>
<td><img src="docs/capturas/raza-detalle.png" alt="Ficha de raza"></td>
</tr>
<tr>
<td><img src="docs/capturas/reporte-pdf.png" alt="Reporte PDF de trazabilidad"></td>
<td><img src="docs/capturas/movil-panel.png" alt="Vista en teléfono" width="260"></td>
</tr>
<tr>
<td colspan="2"><img src="docs/capturas/modo-oscuro.png" alt="Modo oscuro"></td>
</tr>
</table>

<sub>Capturas del prototipo web (`prototipo-web/`), que comparte diseño y reglas con la app Android.</sub>

## Funcionalidades

### 1. Modo sin conexión con sincronización
Todo se guarda primero en Room y entra a una cola de cambios. WorkManager sube la cola en cuanto hay red, aunque la app esté cerrada, y descarga los cambios de otros dispositivos. Los conflictos se resuelven con *last-writer-wins* y un cursor del servidor evita perder cambios por relojes mal configurados.
→ `data/repository/SyncRepository.kt`, `work/Workers.kt`, `backend/src/routes/sync.js`

### 2. Trazabilidad y genealogía
Registro de nacimientos, árbol genealógico de 3 generaciones (con CTE recursiva en SQLite), descendencia, historial médico, vacunas, reproducción, pesajes y producción de leche. Toros externos y pajillas de semen quedan registrados como padre.
Desde la ficha se genera un **reporte PDF de trazabilidad** (foto, genealogía, pesajes con ganancia diaria, sanidad, reproducción y leche) listo para compartir por WhatsApp o correo en una venta o certificación. Se dibuja con `PdfDocument` nativo, sin librerías, y se comparte con `FileProvider`.
→ `data/local/dao/AnimalDao.kt`, `data/reportes/ReporteTrazabilidad.kt`, `ui/hato/`

### 3. Panel de analíticas
Indicadores de leche diaria, ganancia de peso, gasto en farmacia (con comparación contra el periodo anterior), gráficos de producción, evolución de peso por animal, gasto mensual y composición del hato. Gráficos propios dibujados con Canvas, con selección por toque.
→ `ui/panel/`, `ui/components/Graficos.kt`, consultas de agregación en `EventoDao.kt`

### 4. Alertas y notificaciones
Motor de reglas puro (`domain/AlertEngine.kt`) para celos, inseminación, diagnóstico de preñez, partos, secado, dosis, retiro de leche, destete y pesajes. `AlertWorker` lo ejecuta en segundo plano y envía notificaciones solo de lo nuevo y urgente.

### 5. Fotos de cada animal
Cada res puede tener su foto, tomada con la cámara a resolución completa o elegida de la galería. Se endereza según EXIF, se reduce a 1280 px y se guarda en el teléfono; se ve en la lista del hato, en la ficha y en el árbol genealógico. La foto también se sincroniza: viaja aparte de los datos (`PUT/GET /api/fotos/:id`) para no inflar cada descarga, y si se tomó sin señal se sube al volver la conexión.
→ `data/fotos/`, `ui/components/Fotos.kt`, `backend/src/routes/fotos.js`

### Modo oscuro
Automático (sigue al teléfono), claro u oscuro, a elección en **Ajustes → Apariencia**. Los colores de cada modo están definidos como tokens en `ui/theme/Theme.kt`, y la barra de estado se adapta al modo elegido.

### 6. Catálogo e identificación de razas
33 razas (cebú, europeas, sintéticas y criollas) con ficha completa y foto de referencia. El escáner envía la foto a la API, que usa un modelo de visión y responde con la raza, la confianza, los rasgos observados y alternativas, y muestra un ejemplar típico de la raza para comparar.

Las fotos de referencia se agregan en `fotos-razas/` y se preparan con `python herramientas/preparar_fotos_razas.py` (instrucciones y enlaces de búsqueda en [fotos-razas/LEEME.md](fotos-razas/LEEME.md)). Mientras falte la foto de una raza, la app muestra sus colores de pelaje.

### 7. Seguridad, privacidad y soporte
- **Pantalla inicial** con la API oficial SplashScreen: logo sobre el verde de la marca, que se mantiene solo hasta que el hato está cargado (tope de 2 s). La app mide su propio tiempo de arranque.
- **Token cifrado** con AES-256-GCM y una llave del Android Keystore; **bloqueo opcional con huella, rostro o PIN** (BiometricPrompt); solo HTTPS en la versión publicada; copia de seguridad sin credenciales.
- **API endurecida:** límite de peticiones por IP, cabeceras de seguridad y comparación de claves en tiempo constante.
- **Notificaciones configurables:** un canal de Android por categoría (reproducción, salud, manejo), interruptores propios y silencio nocturno. La lógica de qué notificar es pura y está probada (`PoliticaNotificaciones`).
- **Ayuda y soporte:** guía rápida, preguntas frecuentes, resumen de privacidad y "Reportar un problema", que abre el correo con los datos técnicos ya escritos.
- **Analítica propia y anónima**, sin Google: pantallas, acciones clave, tiempo de arranque y errores (tipo y línea). Se guarda en una cola local y se envía en lotes con WorkManager. El usuario puede apagarla y borrar lo enviado. El resumen sale de `GET /api/telemetria/resumen`.
→ `data/seguridad/`, `data/analitica/`, `ui/ayuda/`, `backend/src/routes/telemetria.js`, [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md)

Más detalle y diagramas en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md). Tipografías (Archivo y Karla) y licencias en [docs/LICENCIAS.md](docs/LICENCIAS.md).

## Estructura

```
Bobinapp/
├── android/                 Proyecto Android Studio
│   └── app/src/
│       ├── main/java/com/bobinapp/
│       │   ├── data/        Room, Retrofit, repositorios, datos de ejemplo
│       │   ├── domain/      Reglas de negocio puras (alertas, cálculos)
│       │   ├── work/        WorkManager y notificaciones
│       │   ├── di/          Contenedor de dependencias
│       │   └── ui/          Pantallas Compose por funcionalidad
│       ├── test/            Pruebas unitarias JVM
│       └── androidTest/     Pruebas de Room y del cifrado en dispositivo
├── backend/                 API REST + migraciones SQL + pruebas
├── docs/                    Arquitectura, diagramas y flujo de CI
├── fotos-razas/             Fotos de referencia por raza (entrada del script)
├── herramientas/            Script para preparar las fotos de las razas
├── prototipo-web/           Prototipo HTML que sirvió de maqueta
└── docker-compose.yml       API + PostgreSQL con un comando
```

## Cómo ejecutarlo

### Requisitos
- Android Studio (Ladybug o más reciente) con JDK 17
- Docker Desktop, o Node.js 22 + PostgreSQL 16

### 1. Levantar la API
```bash
# Opción A: Docker
docker compose up --build

# Opción B: local
cd backend
cp .env.example .env        # ajusta DATABASE_URL
npm install
npm start                   # aplica las migraciones y escucha en :3000
```
Comprueba con `http://localhost:3000/api/salud`.

Para el escáner de razas, define `ANTHROPIC_API_KEY` en `.env` (o como variable de entorno para Docker).

### 2. Ejecutar la app
1. Abre la carpeta `android/` en Android Studio y espera a que Gradle sincronice.
2. Ejecuta en un emulador. La app trae 13 animales de ejemplo para explorarla sin configurar nada.
3. Para sincronizar: **Ajustes → Crear finca y conectar**. En el emulador la API es `http://10.0.2.2:3000/`; en un teléfono real usa la IP de tu computadora en la misma red wifi.

### Probar el modo sin conexión
1. Conecta la finca y registra un animal: el indicador de arriba pasa a "Sincronizado".
2. Activa el modo avión y registra un pesaje: el indicador dice "Sin señal · 1 en cola".
3. Cierra la app y quita el modo avión: WorkManager sube el cambio solo. Verifícalo en `GET /api/sync/cambios`.

## Publicar la API en internet (Render)

El repositorio trae un *blueprint* (`render.yaml`) que crea la base PostgreSQL y la API juntas.

1. En [render.com](https://render.com): **New → Blueprint**, elige el repositorio y confirma.
2. Cuando lo pida, pega tu `ANTHROPIC_API_KEY` (solo hace falta para el escáner).
3. Al terminar, abre `https://<tu-servicio>.onrender.com/api/salud`. Las migraciones se aplican solas al arrancar.
4. En la app: **Ajustes → Dirección de la API** con esa URL y **Crear finca y conectar**.

Notas del plan gratuito: la API se duerme tras 15 minutos sin uso (la primera petición tarda cerca de un minuto) y la base gratuita caduca a los 30 días. Las fotos se guardan en PostgreSQL, así que no se necesita disco aparte.

¿Otra base (Neon, Supabase)? Pon su URL en `DATABASE_URL` y `DATABASE_SSL=true`.

## Código fuente

Repositorio: [github.com/NayeliSantos19/bobinapp](https://github.com/NayeliSantos19/bobinapp)

```bash
git clone https://github.com/NayeliSantos19/bobinapp.git
```

## Pruebas
```bash
# API (necesita un PostgreSQL de pruebas)
cd backend
DATABASE_URL=postgres://usuario:clave@localhost:5432/bobinapp_test npm test

# App: reglas de negocio (JVM)
cd android
./gradlew testDebugUnitTest

# App: consultas de Room (emulador conectado)
./gradlew connectedDebugAndroidTest
```

## Endpoints de la API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/salud` | Estado de la API y la base |
| POST | `/api/fincas` | Crea una finca y devuelve su token (solo se muestra una vez) |
| GET | `/api/fincas/yo` | Datos de la finca del token |
| POST | `/api/sync/push` | Sube animales y eventos (LWW por `actualizadoEn`) |
| GET | `/api/sync/cambios?desde=&limite=` | Descarga cambios desde un cursor, paginado |
| POST | `/api/identificar` | Identifica la raza a partir de una imagen en base64 |
| PUT | `/api/fotos/:animalId` | Sube la foto de un animal (JPEG/PNG/WebP, máx. 3 MB, cabecera `X-Foto-Actualizada-En`) |
| GET | `/api/fotos/:animalId` | Descarga la foto de un animal |
| GET | `/api/razas` | Catálogo de razas |
| POST | `/api/telemetria` | Lote de eventos de uso anónimos (sin token, máx. 200 por lote) |
| DELETE | `/api/telemetria/:instalacion` | Borra las estadísticas de una instalación |
| GET | `/api/telemetria/resumen?dias=7` | Resumen de uso y errores (requiere `ADMIN_TOKEN`) |

Las rutas de datos de la finca requieren `Authorization: Bearer <token>`. No lo requieren `/api/salud`, `/api/razas`, `POST /api/fincas` ni la telemetría anónima, y el resumen de telemetría usa su propia clave de administrador.

## Próximos pasos
- Cuentas de usuario con varios trabajadores por finca y permisos.
- Identificación por arete electrónico (RFID/NFC).
