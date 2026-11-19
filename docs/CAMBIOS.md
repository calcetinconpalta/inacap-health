# Cambios · versión 1.3

## App
- **Nueva cara: diseño calmado y clínico.** Neutros fríos, acento verde azulado para la información de salud y el rojo INACAP solo para la marca y las acciones principales. Inicio con fecha y saludo, tarjetas con borde suave, íconos en cada sección y tipografía más clara.
- **Modo noche activable y desactivable**: luna/sol en Inicio, *Perfil → Apariencia* (Igual que el teléfono / Claro / Noche) y también en el modo equipo. Se recuerda en el teléfono; la barra de estado cambia con el modo.
- **Relojes por Bluetooth**: *Salud → Mi reloj → Buscar mi reloj* se conecta directo a relojes y bandas que transmiten la frecuencia cardíaca (perfil Bluetooth estándar 0x180D): pulso en vivo, batería, aviso si no está en contacto con la piel y «Guardar en mi historial» (promedio de los últimos latidos). Recuerda el último reloj para reconectar.
  - **Guía por marca** (Xiaomi/Mi Band, Amazfit, Samsung, Pixel/Fitbit, Garmin, Huawei, Polar/Coros): cuál camino sirve para cada una (Bluetooth o Health Connect) y en qué menú se activa.
  - Las marcas que no transmiten por Bluetooth estándar (p. ej. Samsung) siguen entrando por Health Connect.
- **Modo equipo** (tutor par, profesional de salud, docente supervisor): «Soy del equipo» en la bienvenida. Bandeja de **Solicitudes** con contador, **Chats** con estudiantes y **Cuenta**. El rol lo decide el servidor; la sesión expira a los 30 min sin uso o a las 12 h; en modo equipo la app no guarda ni envía datos de estudiante.
  - Profesional: ve las derivaciones, cambia su estado y **responde por chat** (atención confidencial: solo la persona y el profesional). El estudiante lo ve en *Apoyo profesional → Ver mensajes*.
- **Menos detalles técnicos para el estudiante**: las pantallas ya no nombran servidores ni tecnologías («Qué se guarda», Seguridad, Cuenta). Los errores se explican en lenguaje simple; el diagnóstico técnico solo aparece en compilaciones de prueba.
- **Código anónimo oculto** (••••••••) con botón *Mostrar/Ocultar* y se vuelve a ocultar solo a los 15 s.
- **Términos v3**: política de privacidad con proveedores en forma genérica (con derecho a pedir el detalle), atención profesional y reloj por Bluetooth. La app pide aceptar de nuevo.
- Se quitó el acceso de demostración «Videollamadas y diagnóstico automático».

## Panel web (dashboard)
- **Administrador**: pestañas **Equipo** (dar o quitar acceso y cambiar rol por correo, último acceso) y **Seguridad** (rechazos en 24 h, IPs bloqueadas con bloqueo/desbloqueo manual, registro de auditoría).
- **Profesional de salud**: pestañas *Solicitudes* (derivaciones cifradas + «Responder por chat») y *Atención* (chats confidenciales, cerrar/reabrir).
- Sesión que se cierra sola a los 15 min sin uso o a las 8 h; limpieza de texto antes de enviar; modo noche.
- **Listo para Cloudflare Pages**: `scripts/generar-config.mjs` crea `config.js` y `_headers` (CSP estricta, HSTS, anti-iframe) desde variables de entorno; `wrangler.toml`. Sin scripts en línea.

## Secretos y seguridad
- **`.env`** en la raíz (plantilla `.env.example`) con todas las claves y secretos; en `.gitignore`. La app y el panel lo leen; ambos se niegan a usar una clave secreta de Supabase.
- Servidor (`schema.sql` v3): límites por persona y por IP con bloqueo automático, validación y limpieza en el servidor, auditoría, RLS de la atención profesional.
- Guía completa: `docs/DESPLIEGUE_Y_SEGURIDAD.md` (Cloudflare, expiración de sesión en Supabase, límites de Auth, restricciones de API keys, CORS).

## Pruebas
- Nuevas: `RelojBluetoothTest` (decodificación del pulso Bluetooth, 6), `EquipoTest` (sesión, rol del servidor, saneamiento, 7). Total app: 66 pruebas, 0 fallos.
- SQL: 5 suites (incluida `05_v3_seguridad_y_atencion.sql`). Panel: prueba en navegador con la CSP real.

---

# Cambios · versión 1.2.2

- **Corregido: los datos podían perderse sin aviso.** Si la nube rechazaba el envío por configuración (falta `schema.sql`, inicio anónimo desactivado, reglas de Firestore sin publicar, clave incorrecta), tras 3 intentos los datos se descartaban en silencio, y un 401 se reintentaba sin fin. Ahora los datos **se conservan** hasta que la configuración esté bien.
- **Perfil → Cuenta → Sincronización**: «Sincronizar ahora» envía en el momento y dice exactamente qué pasó; «Probar conexión» revisa Supabase y Firestore por separado y explica qué falta configurar. Privacidad también muestra el último problema.
- Pruebas nuevas: `ExplicacionErrorTest` y 3 casos en `SyncManagerTest` (58 en total).

# Cambios · versión 1.2.1

- **Corregido: no se podía volver a Inicio.** En las subpantallas (Historial, Calculadoras, Pulso, Privacidad…) la barra inferior desaparecía y la única salida era la flecha "atrás". Ahora la barra se ve en toda la app (salvo la bienvenida), la casita siempre vuelve a Inicio y se marca la pestaña correcta. Prueba: `NavegacionTest` (4 casos).
- **Cuenta con correo (opcional).** Después de aceptar los términos se ofrece crear una cuenta, iniciar sesión o seguir sin cuenta; también desde *Perfil → Cuenta*.
  - Crear cuenta convierte la cuenta anónima en una con correo (mismo id: no se pierde nada). Funciona con o sin confirmación de correo.
  - Iniciar sesión en otro teléfono recupera IMC, agua, pasos, pulso y puntajes SCOFF (el nombre, los datos clínicos y el peso nunca se suben, así que no se recuperan).
  - Recuperar contraseña por correo (página `dashboard/restablecer.html`), cerrar sesión (borra los datos del teléfono, quedan en la cuenta) y eliminar todo (borra también la cuenta).
  - Pruebas: `CuentaTest` (6 casos) y SQL (`04_…`: la cuenta con correo se elimina; la del personal nunca).
- Compatible con las claves nuevas de Supabase (`sb_publishable_…`) en la app y en el dashboard (supabase-js 2.118.0).
- La app se identifica ante Firebase con su paquete y certificado (necesario si la API key está restringida a Android).
- Claves configuradas: `local.properties` (Supabase + Firebase Android) y `dashboard/config.js` (Supabase + Firebase web).

---

# Cambios · versión 1.2 (todo el backlog)

Todas las historias del backlog quedaron implementadas. Ya no queda ninguna pantalla "No implementado en esta versión", salvo *Videollamadas y diagnóstico automático*, que el equipo descartó a propósito (fuera de alcance).

| HU | Qué se hizo | Criterios de aceptación | Cómo se verificó |
|---|---|---|---|
| **HU-01** Cifrado E2E y base | Base local cifrada con **SQLCipher (AES-256)**; la clave es aleatoria y está protegida por el **Android Keystore**; las bases sin cifrar de la v1.x se convierten solas. Tokens de sesión cifrados con AES-GCM (Keystore). Solo HTTPS, sin certificados de usuario (`network_security_config`). Consentimiento y borrado (v1.1) | Datos cifrados en el teléfono y en tránsito ✔ · consentimiento explícito ✔ · eliminar datos ✔ | Compila con SQLCipher; detección de base sin cifrar probada. **Cifrado real: falta probarlo en un teléfono** (Robolectric no tiene Keystore) |
| **HU-02** Sin conexión | Bandeja `sync_pendiente` + WorkManager (v1.1) | Funciona sin internet ✔ · se sincroniza al volver ✔ · sin duplicados ✔ | 8 pruebas contra un servidor falso |
| **HU-08** Bloqueo | PIN (hash PBKDF2-SHA256 con sal, 120 000 iteraciones) y huella o rostro (BiometricPrompt). Se bloquea al abrir y al volver tras 0, 1, 5 o 15 min. Espera creciente tras 5 intentos fallidos. Sin capturas ni vista en recientes con PIN activo (`FLAG_SECURE`) | Bloqueo configurable ✔ · expira la sesión ✔ · sin acceso a datos con la app bloqueada ✔ | 5 pruebas (hash, PIN triviales, esperas, expiración) |
| **HU-03** IMC y agua | (v1.0) | ✔ | — |
| **HU-04** SCOFF | (v1.0) | ✔ | — |
| **HU-05** Pulso con cámara | CameraX + linterna; brillo rojo de cada cuadro → quita la tendencia, suaviza, detecta latidos y usa la mediana de los intervalos. Indica la calidad de la señal y reinicia si se retira el dedo. Las imágenes nunca se guardan | Guía en pantalla ✔ · resultado marcado como estimación ✔ · se guarda en el historial ✔ | 5 pruebas con señales sintéticas (50–130 lpm, error ≤ 4 lpm; con solo ruido no inventa un valor). **Falta probarlo con un dedo real** |
| **HU-14** Historial | Nueva pestaña **Actividad**: pasos de 7 días con la meta y mediciones de pulso con su fuente (cámara o reloj) | Ordenado por fecha ✔ · solo visible para la persona ✔ · incluye HU-03 y HU-05 ✔ | Captura de pantalla |
| **HU-09** Recordatorios | (v1.0; en la v1.1 se corrigió el reinicio y la reprogramación) | ✔ | — |
| **HU-10** Metas sin calorías | Contador de pasos (sensor `STEP_COUNTER`, detecta reinicios del teléfono), lectura cada 15 min, meta de 4 000 a 10 000 pasos en Inicio. No hay ningún conteo calórico en la app | Sin calorías ✔ · progreso en pasos, movimiento y agua ✔ | Pruebas del cálculo de pasos |
| **HU-11** Educativo | (v1.0) | ✔ | — |
| **HU-06** Derivación | La persona puede dejar su contacto: se **cifra de extremo a extremo** (RSA-OAEP-3072 + AES-256-GCM) con la clave pública de Bienestar. El equipo de Bienestar lo descifra en su navegador (dashboard) y gestiona el estado | Visible tras la alerta ✔ · no se envía nada sin consentimiento ✔ | Ida y vuelta en la JVM + **interoperabilidad: lo que cifra Kotlin lo descifra WebCrypto** + pruebas SQL |
| **HU-12** Resumen PDF | PDF A4 con IMC, agua, pasos y pulso por fecha (SCOFF y nombre opcionales; nunca patologías). Se comparte con FileProvider | Por fecha ✔ · solo a solicitud ✔ | Armado de datos probado. **El dibujo del PDF no se pudo probar aquí** (Robolectric no implementa `PdfDocument`) |
| **HU-07** Health Connect | Vinculación opcional y solo de lectura: pasos y frecuencia cardíaca promedio por día al historial unificado; volver a importar no duplica. Pantalla de política para Health Connect | Opcional ✔ · historial unificado ✔ | Compila. **Falta probarlo en un teléfono con Health Connect** |
| **HU-13** Orientación entre pares | Chat estudiante ↔ tutor par, con consentimiento propio. Los roles `tutor`, `docente`, `bienestar` y `admin` entran al dashboard (JWT de Supabase). El docente supervisa con bitácora. Los casos con alerta SCOFF se derivan y no se atienden entre pares; los mensajes con señales de riesgo muestran líneas de ayuda (Salud Responde, \*4141) | Solo educativa ✔ · excluye TCA graves ✔ · supervisión docente registrada ✔ | 17 verificaciones SQL de roles, chat y derivaciones; dashboard probado con cada rol |

**Otros cambios**
- Base local v3: columna `fuente` y tabla `pasos_dia` (migración 2→3, probada en Room junto con la 1→2).
- Términos y política **versión 2**: los usuarios los aceptan de nuevo al actualizar.
- Dashboard con pestañas por rol, métricas de pulso, pasos, orientación y derivaciones, y modo demostración para cada rol.
- Pantallas nuevas: *Seguridad* (estado real del cifrado) y *Cuenta y sincronización* (roles y envíos pendientes).

**Para probar en un teléfono (no se pudo hacer aquí):** cifrado SQLCipher y Keystore, huella, cámara con linterna, sensor de pasos, Health Connect y PDF.

---

# Cambios · versión 1.1 (base de datos, dashboard, términos y correcciones)

## 1. Bases de datos
- **Local (Room) v1 → v2 con migración** (`AppDatabase.MIGRATION_1_2`): conserva todos los datos existentes.
  - `perfil`: + `terminosVersion`, `terminosAceptadosEn`, `compartirNube`.
  - `registro_biometrico` y `resultado_scoff`: + `uuid` (id global para sincronizar sin duplicados).
  - Tablas nuevas: `consentimiento` (historial de aceptaciones/revocaciones) y `sync_pendiente` (bandeja de salida offline).
  - Los esquemas se exportan en `app/schemas/` para revisar futuras migraciones.
- **SQL en la nube (Supabase/PostgreSQL)**: `backend/supabase/schema.sql`: 7 tablas, seguridad por fila, funciones de borrado, retención y dashboard. Probado en PostgreSQL 16 (aislamiento entre usuarios, solo-admin, borrado en cascada).
- **NoSQL en la nube (Firestore)**: colección `eventos` con reglas en `backend/firebase/firestore.rules`.
- **Sincronización offline-first** (`data/sync/`): todo pasa por `sync_pendiente`; `SyncWorker` (WorkManager) envía al haber internet y reintenta con espera creciente. Cumple HU-02 (sin conexión, sin duplicados).
- **Anonimización** (`Anonimizacion.kt`): nunca se envían nombre, patologías, alergias, medicamentos, peso, estatura ni respuestas del SCOFF. Edad como rango. Probado con pruebas unitarias.

## 2. Dashboard (`dashboard/`)
Panel web para administradores: usuarios, actividad diaria, IMC, SCOFF, edad, derivaciones (SQL); eventos, instalaciones activas, pantallas, acciones y errores (NoSQL); consentimientos, revocaciones, borrados, retención e inventario de datos. Incluye modo de demostración, modo oscuro y vista de tabla para cada gráfico.

Dentro de la app, *Perfil → Privacidad y mis datos* muestra al usuario qué se guarda y dónde, el estado del envío, su historial de consentimientos, y le permite revocar o borrar todo.

## 3. Términos y condiciones
- `legal/TextosLegales.kt` (versión 1) y `docs/TERMINOS_Y_CONDICIONES.md` (generado del mismo texto).
- Onboarding: aceptación obligatoria de términos y de mayoría de edad/autorización, y consentimiento **opcional** para la nube.
- Si se sube `TextosLegales.VERSION`, todos los usuarios deben aceptar de nuevo (sin perder sus datos).
- Nueva pantalla *Términos y privacidad* (ruta `terminos`), accesible desde la bienvenida y desde Perfil.

## 4. Errores corregidos
| # | Error | Efecto | Corrección |
|---|---|---|---|
| 1 | `java.time` con `minSdk 24`, con la advertencia silenciada (`@Suppress("NewApi")`) | El Historial se cerraba en Android 7.x | Core library desugaring; se quitó el `Suppress` |
| 2 | `aceptarConsentimiento` leía `perfil.value`, que en el onboarding es `null` | Al volver a aceptar se borraban nombre, edad, alergias, etc. | Se lee el perfil directo desde la base |
| 3 | Inicio del día calculado una sola vez | El contador de agua no volvía a 0 a medianoche y la ventana de 7 días no avanzaba | Flujo que se actualiza al cambiar de día |
| 4 | Meta de agua guardada solo en memoria | Tras "Eliminar todos mis datos" seguía la meta anterior | Se observa SharedPreferences |
| 5 | `HealthApp` reprogramaba los recordatorios en cada inicio | Abrir la app seguido posponía los avisos indefinidamente | Solo se programan si no hay una alarma pendiente |
| 6 | Alarmas perdidas al reiniciar el teléfono | Recordatorios dejaban de llegar | `BootReceiver` (BOOT_COMPLETED) |
| 7 | `allowBackup=true` sin reglas | Datos de salud copiados al respaldo de Google, contradiciendo "solo en tu teléfono" | `allowBackup=false` y reglas que excluyen base y preferencias |
| 8 | Color de error por defecto de Material (rojo) | Campos con error en rojo, contra la guía de diseño | `error` del tema en gris oscuro |
| 9 | Derivación usaba `remember` | Al girar el teléfono se perdía el comprobante y cambiaba el código | `rememberSaveable` |
| 10 | Sin migraciones de Room | Cualquier cambio de tablas cerraba la app al actualizar | Migración 1→2 y esquemas exportados |
| 11 | En la segunda pantalla del onboarding, "Atrás" del sistema cerraba la app | Mala experiencia | `BackHandler` vuelve al paso 1 |

## 5. Pruebas (v1.1)
| Qué | Cómo correrlo | Resultado |
|---|---|---|
| App: 22 pruebas JVM (anonimización, formato Firestore, cálculos, **migración v1→v2 con Room real**, **sincronización completa contra un servidor falso**: sin consentimiento no sale nada, orden usuarios→registros, sin datos personales, sin conexión conserva pendientes, reenvío sin duplicados, revocación borra en ambas bases) | `./gradlew testDebugUnitTest` | 22/22 ✔ |
| Reglas de Firestore: 10 pruebas en el emulador oficial | `cd backend/firebase && npm install && npm test` | 10/10 ✔ |
| Esquema SQL: RLS, solo-admin, historial inmutable, borrado y purga | ver `backend/supabase/test/README.md` | ✔ |
| Lint de Android | `./gradlew lintDebug` | 0 errores |

Capturas de las pantallas nuevas (renderizadas con Robolectric): `docs/capturas-app.png`. Dashboard: `docs/dashboard-demo.png`.

**No probado:** la app en un teléfono o emulador real, y la conexión con proyectos reales de Supabase/Firebase (se necesitan sus cuentas).
