# Política de privacidad de Bobinapp

*Última actualización: 1 de octubre de 2026*

Bobinapp es una app para llevar el registro de un hato ganadero. Esta política explica qué datos maneja, dónde se guardan y cómo controlarlos. La misma información está resumida dentro de la app en **Ajustes → Ayuda → Tus datos y tu privacidad**.

## 1. Datos del hato
- **Qué:** animales (arete, nombre, raza, sexo, nacimiento, padres), eventos (pesajes, leche, vacunas, tratamientos, reproducción, bajas) y fotos de los animales.
- **Dónde:** en el teléfono, en la base de datos de la app. Si conectas una finca, también en el servidor de la finca para sincronizar los teléfonos.
- **Para qué:** únicamente para mostrarte tu hato, calcular alertas e indicadores, y sincronizar tus teléfonos.
- **Quién los ve:** solo los teléfonos conectados a tu finca. Cada finca está separada de las demás en el servidor.

## 2. Acceso y seguridad
- El acceso a la finca usa un token aleatorio de 256 bits. En el servidor solo se guarda su huella SHA-256; en el teléfono se guarda cifrado con AES-256-GCM y una llave del **Android Keystore**, que no puede salir del teléfono.
- La versión publicada de la app solo se conecta al servidor por **HTTPS**.
- Bloqueo opcional con **huella, rostro o PIN** del teléfono (Ajustes → Seguridad).
- El servidor limita la cantidad de peticiones para frenar abusos y responde con cabeceras de seguridad estándar.
- La copia de seguridad de Android incluye el hato, pero no el token ni las estadísticas pendientes.

## 3. Estadísticas de uso anónimas
Para saber qué mejorar, la app puede enviar estadísticas **anónimas**:
- pantallas que se abren y acciones como "generó un reporte PDF" o "usó el escáner" (con su nivel de confianza);
- cuánto tarda la app en abrir;
- errores: el tipo de error y el archivo y la línea del código donde ocurrió;
- versión de la app y de Android.

Se identifican con un número aleatorio por instalación, que no está ligado a tu finca, tu nombre ni tu teléfono. **Nunca** se envían nombres, aretes, notas, fotos, ubicación ni la IP (el servidor no la guarda). No se usan servicios de terceros como Google Analytics. No hay publicidad ni venta de datos.

Puedes apagarlas en **Ajustes → Privacidad**, y con **"Borrar mis estadísticas del servidor"** se eliminan las ya enviadas y se genera un número nuevo.

## 4. Escáner de razas
La foto que escaneas se envía al servidor de la finca, que la pasa a un modelo de visión (Anthropic) solo para identificar la raza. El servidor no la guarda. Solo se usa si tocas "Identificar".

## 5. Notificaciones
Las alertas se calculan en el propio teléfono. No se envían datos a terceros para notificarte. Puedes elegir qué tipos recibir y activar el silencio nocturno en Ajustes → Notificaciones.

## 6. Borrar tus datos
- **En el teléfono:** desinstalar la app borra todo.
- **En el servidor:** el administrador de la finca puede pedir el borrado completo de la finca.
- **Estadísticas:** Ajustes → Privacidad → Borrar mis estadísticas del servidor.

## 7. Contacto
Para preguntas sobre privacidad, escribe desde **Ajustes → Ayuda → Reportar un problema**.
