# Ficha de Google Play (borrador para completar)

> Esto es **material de preparación**, no una garantía de aprobación. Revisa las políticas **vigentes** en Play Console antes de enviar: no se pudieron consultar en línea desde el entorno de generación.

* **Nombre (≤30):** PronoScore360: Análisis Deportivo
* **Descripción corta (≤80):** Estadísticas y probabilidades de fútbol y NBA con modelos reales. Sin apuestas.
* **Descripción completa:**
  > PronoScore360 es una plataforma de **análisis deportivo estadístico**. Consulta calendarios de fútbol y NBA, forma reciente, clasificaciones y probabilidades estimadas con modelos estadísticos (Poisson/Dixon-Coles y simulaciones Monte Carlo), con explicación, cantidad de datos usados y fecha de actualización. Si no hay datos suficientes, te lo decimos.
  > • Plan Gratis: 5 partidos analizados al día, probabilidades básicas y marcador estimado.
  > • Plan Pro: todos los mercados (goles, córners, tarjetas, líneas NBA), top de marcadores, filtros, selecciones con probabilidad conjunta, historial completo y rendimiento del modelo.
  > **Importante:** las probabilidades son estimaciones y no garantizan resultados. PronoScore360 **no permite apuestas, no acepta dinero para apostar ni se conecta con casas de apuestas**. Contenido informativo para mayores de edad.
* **Categoría:** Deportes (alternativa: Noticias y revistas). **Etiquetas:** estadísticas deportivas, fútbol, baloncesto.
* **Icono:** `android/app/src/main/res/drawable/ic_launcher_foreground.xml` (adaptativo, original). Para Play necesitas además un PNG 512×512 (exportar desde `web/public/logo.svg`).
* **Gráfico de funciones 1024×500 y capturas (mín. 2, recomendado 4-8 por formato de teléfono): PENDIENTES.** Referencia visual de la web en `docs/screenshots/`; haz las capturas desde la app Android real en modo **producción/datos reales** (no mostrar datos DEMO como reales).
* **Política de privacidad:** URL pública requerida → `https://app.tudominio.com/privacy` (borrador incluido; revisar legalmente). **Eliminación de cuenta:** URL pública `https://app.tudominio.com/delete-account` + ruta dentro de la app (Perfil → Eliminar mi cuenta, implementada).
* **Seguridad de los datos (borrador de respuestas, validar con el código final y los SDK):**
  | Dato | ¿Se recopila? | Finalidad | Compartido |
  |---|---|---|---|
  | Correo, nombre | Sí | Cuenta, funcionalidad de la app | No |
  | ID de usuario / token de dispositivo (FCM) | Sí | Cuenta, notificaciones | Google (FCM) como procesador |
  | Historial de compras/suscripción | Sí | Funcionalidad (verificación) | Google Play |
  | Actividad en la app (partidos analizados, favoritos) | Sí | Funcionalidad | No |
  | Ubicación, contactos, fotos, datos financieros (tarjetas) | No | — | — |
  Cifrado en tránsito: sí (HTTPS). Eliminación de datos: sí (en app y web).
* **Clasificación de contenido (IARC):** contesta con honestidad: contenido deportivo/estadístico, **sin apuestas con dinero real**; declara si la app muestra información o pronósticos relacionados con apuestas (la herramienta pregunta por «simulated gambling» y «real-money gambling»). Edad mínima sugerida: 18+ por temática de pronósticos (decisión tuya tras el cuestionario).
* **Anuncios:** no contiene. **Compras en la app:** sí (suscripción).
* **Aspectos de política a revisar (no asumir):**
  1. *Pagos:* bienes/servicios digitales dentro de la app → Google Play Billing (implementado). No dirigir a pagos externos dentro de la app salvo programas permitidos en tu región.
  2. *Suscripciones:* mostrar precio, periodo, renovación automática y cómo cancelar con claridad antes de comprar (la pantalla de Planes lo hace; revisar textos).
  3. *Juegos de azar con dinero real / contenido relacionado con apuestas:* Play restringe apps que faciliten apuestas; una app puramente informativa **debe dejar claro que no permite apostar** y evitar enlaces/afiliación a casas de apuestas. Revisa la política vigente y el comportamiento de tus metadatos (ficha, capturas, nombre) para no inducir a apostar ni prometer ganancias.
  4. *Datos de usuario y eliminación de cuenta:* formulario de Seguridad de los datos y URL de eliminación (arriba).
  5. *Contenido generado/licencias:* datos deportivos y logos requieren licencia del proveedor.
  6. *Metadatos engañosos:* no usar términos como "pronósticos seguros", "gana siempre", "100 % de acierto".
* **Pruebas:** interna → cerrada → producción (revisar requisitos vigentes de testers/duración para cuentas nuevas).
