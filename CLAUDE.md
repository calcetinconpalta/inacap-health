# INACAP Health — guía para Claude Code

App Android de autocuidado para estudiantes (Kotlin + Jetpack Compose). **Orienta, no diagnostica.**
Proyecto académico INACAP. La interfaz y los comentarios van en español.

Este archivo resume el proyecto y, al final, la **lista de tareas acordadas** con el usuario para la próxima versión.
Trabaja en el repositorio clonado en el computador del usuario (Android Studio): puedes compilar e instalar en su teléfono por USB.

---

## Cómo compilar y probar

```bash
./gradlew testDebugUnitTest lintDebug      # pruebas unitarias (Robolectric) + lint
./gradlew assembleDebug                     # genera el APK de prueba
./gradlew installDebug                      # instala en el teléfono conectado por USB
```

- Backend SQL (opcional, necesita un PostgreSQL local): ver `backend/supabase/test/README.md` (suites `00`–`05`).
- Reglas de Firestore: `cd backend/firebase && npm install && npm test` (emulador local).
- Panel web: `node scripts/generar-config.mjs` genera `dashboard/config.js` y `dashboard/_headers` desde `.env`.

**Regla de oro:** después de cada cambio, corre `testDebugUnitTest` y `lintDebug` antes de dar algo por terminado. Si tocas una pantalla, saca una captura o instálala en el teléfono y pídele al usuario que la vea.

## Dónde van las claves (nunca a git)

- `local.properties` (raíz): claves de la app (`SUPABASE_URL`, `SUPABASE_ANON_KEY`/`SUPABASE_PUBLISHABLE_KEY`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `BIENESTAR_CLAVE_PUBLICA`). Tiene prioridad sobre `.env`.
- `.env` (raíz): todas las claves y secretos. Plantilla: `.env.example`. La compilación **falla** si encuentra una clave `sb_secret_…`.
- `dashboard/config.js`: solo claves públicas del panel (generado, no editar a mano).
- Los tres están en `.gitignore`. Nunca los subas ni los imprimas completos.

## Convenciones del proyecto (respétalas)

- **Idioma:** todo el texto visible y los comentarios, en español de Chile.
- **Color:** el rojo INACAP es solo para la marca y las acciones principales, **nunca** para errores o alertas (el tema redefine `error` para evitarlo). Hay modo claro y modo noche (`ui/theme/Theme.kt`, `Apariencia`).
- **Nada de nombres técnicos en pantallas de estudiante:** no menciones "Supabase", "Firebase", "servidor", "RLS", etc. Usa lenguaje simple. El detalle técnico solo se muestra si `BuildConfig.DEBUG` (ver `ExplicacionError` y `ui/components/DetalleError.kt`).
- **Consultas parametrizadas siempre:** Room con `:parametro`; al servidor los datos van como JSON (`insertar`/`upsert`/`rpc` con `JSONObject`), nunca concatenados. La frontera de seguridad real es **RLS en el servidor**: cada quien solo ve y escribe lo suyo; el personal, según su rol.
- **El rol lo decide el servidor,** no la app. La app nunca asume permisos por su cuenta.
- **Privacidad:** nombre, patologías, alergias, medicamentos, peso y estatura **nunca** salen del teléfono. A la nube solo van datos anonimizados y solo si la persona activó "Compartir datos anonimizados".
- **Correo del usuario** (`therealnacho25@gmail.com`) es privado: no lo mandes a servicios externos.

## Arquitectura (resumen)

- `app/` — la app. `data/` (Room + entidades), `data/remote/` (clientes Supabase/Firestore, `Http`, `ExplicacionError`), `data/sync/` (`SyncManager`, bandeja offline `sync_pendiente` + WorkManager), `salud/` (pulso por cámara, pasos, Health Connect, reloj BLE), `seguridad/` (PIN/biometría), `equipo/` (modo equipo), `cuenta/` (cuenta con correo), `legal/` (`TextosLegales`), `ui/`.
- `backend/supabase/schema.sql` — SQL (tablas, RLS, funciones por rol, límites de uso, bloqueo por IP, auditoría). Ejecutable varias veces sin romper nada.
- `backend/firebase/firestore.rules` — reglas NoSQL (eventos de uso).
- `dashboard/` — panel web por roles (se publica en Cloudflare Pages).

## Roles (ya existen en el servidor; la app los usa)

| Rol | Qué hace |
|---|---|
| Estudiante | Usa la app (anónimo o con correo, como prefiera). Puede pedir orientación y apoyo. |
| Tutor par | **Monitorea** los chats: revisa que no pase nada grave. Toma y responde consultas. No es uno por sede; tantos como amerite la escala. |
| Bienestar | **Estudiantes de medicina en práctica supervisada** que atienden al alumno: chats confidenciales y derivaciones cifradas. **No** son profesionales titulados. |
| Docente supervisor | Supervisa a los de Bienestar y a los tutores: ve todas las conversaciones y la bitácora. |
| Administrador | Estadísticas agregadas, gestión del equipo y seguridad. |

> **Importante para el texto de la app:** a Bienestar no se le llama "profesional de salud" ni "médico" en lo que ve el estudiante (son estudiantes de medicina en práctica, supervisados por docentes). El rol en el servidor sigue llamándose `bienestar`; cambia solo el texto visible. Revisar las pantallas y `legal/TextosLegales.kt` para ajustar ese tratamiento.

## Limitaciones conocidas (no son bugs; decláralas con honestidad)

- **Reloj en tiempo real:** solo hay pulso en vivo por Bluetooth y si el reloj tiene activada la transmisión de frecuencia cardíaca. Marcas como Samsung/Xiaomi guardan en su app y los datos llegan por Health Connect con minutos de atraso. El tiempo real propietario (Samsung/Garmin SDK) queda fuera de alcance.
- **PIN y biometría** son un bloqueo de pantalla, no de cifrado: la base se cifra con una clave del Android Keystore que no depende del PIN. La biometría usa `BIOMETRIC_WEAK` sin `CryptoObject`.
- **Segundo plano:** Xiaomi/Huawei/Honor matan procesos en segundo plano; el conteo de pasos y la sincronización pueden pausarse si la app no está excluida del ahorro de batería.

## Estado de git

- `main` en GitHub va en `cb10036`. Hay **2 commits locales sin subir**: 1.3.1 (reasignación de id en sync) y 1.3.2 (aceptar todo + errores de red). Súbelos (`git push`) antes de empezar lo nuevo.
- Versión actual en `app/build.gradle.kts`: `versionCode = 6`, `versionName = "1.3.2"`. La próxima tanda de abajo sube a **1.3.3**.
- Los commits van a nombre de Naxo, con mensajes cortos y simples, sin firmas ni coautores.

---

# Tareas acordadas con el usuario (próxima versión 1.3.3)

Hazlas en este orden. Cada una termina con sus pruebas y, si toca UI, una verificación en el teléfono.

### 1. Inicio de sesión único con detección de rol  *(prioridad alta)*
**Problema actual:** hay un flujo separado "Soy del equipo" solo en la bienvenida; tras salir del modo equipo no hay forma de volver (hay que borrar la app). Si un correo de tutor inicia sesión por la pantalla de estudiante, queda como alumno y al mandar mensajes el servidor responde `42501 / row-level security policy for table "conversaciones"`.
**Qué hacer:**
- Una sola pantalla de **Iniciar sesión** (correo + contraseña). El estudiante **sigue pudiendo usar la app sin cuenta** (anónimo); el inicio de sesión es opcional.
- Tras autenticar, consultar el rol en el servidor (`rpc("rol_actual")`):
  - rol de equipo (tutor/bienestar/docente/admin) → abre el **modo equipo** correspondiente.
  - sin rol → sesión de **estudiante**.
  - **Nunca** dejar que una cuenta de equipo quede como alumno.
- "Acceso del equipo" / volver a iniciar sesión debe estar **siempre disponible en Perfil**, no solo en la bienvenida.
- Al cerrar sesión del equipo, volver a la pantalla de inicio de sesión, no a la de estudiante.
- Mensaje claro si alguien entra con correo de equipo esperando ser alumno (y viceversa), sin el error técnico.
- Pruebas: cuenta de equipo no queda como alumno; logout del equipo reabre el acceso; estudiante anónimo intacto.

### 2. Botón atrás del teléfono  *(prioridad alta)*
**Problema:** en el modo equipo y en chats, el botón atrás cierra la app en algunos teléfonos (Xiaomi/Honor). En el Motorola G51 del usuario no pasa, es dependiente del teléfono.
**Qué hacer:** manejar el botón atrás con `BackHandler`: desde un chat vuelve a la lista; desde otra pestaña del modo equipo vuelve a Solicitudes; solo desde la raíz sale de la app. Revisar también Orientación (chat abierto → lista).

### 3. Interfaz de datos por rol dentro de la app
Llevar a la app lo que hoy está en el panel web, cada quien viendo solo lo suyo (lo decide el servidor):
- **Tutor par:** sus solicitudes y chats + una vista de **monitoreo** con cifras sin nombres (consultas abiertas, por tema, cuántas esperan respuesta).
- **Bienestar (estudiantes de medicina en práctica):** solicitudes de apoyo que le llegan + sus **chats confidenciales** (solo él y la persona). Supervisados por los docentes.
- **Docente supervisor:** todas las conversaciones de orientación + bitácora.
- **Administrador:** estadísticas agregadas, equipo y seguridad (IPs, auditoría), resumido.
Reusar la lógica del panel (`dashboard/`) y las funciones del servidor que ya existen.

### 4. Enviar el PDF del resumen a Bienestar
Hoy el PDF se genera en el teléfono y se comparte con el menú de Android; **no** se guarda en el servidor.
**Qué hacer:**
- Botón "Enviar a Bienestar" que abre la app de correo del **propio estudiante** con el PDF adjunto y el destinatario **ya puesto**.
- El destinatario es **fijo**: un correo del equipo de Bienestar configurable (`BIENESTAR_EMAIL` en `.env`/`strings.xml`, lo llena el usuario). **El estudiante no lo elige** (evita saturar la bandeja; el tutor solo monitorea, no recibe PDFs).
- No enviar desde el servidor (evita usarlo para correo masivo).
- Límite de envíos por persona (reusar el patrón de límites que ya hay).

### 5. Pasos en vivo con el sensor
**Problema:** la app lee el sensor de a ratos (cada ~15 min o al abrir Inicio), no mientras caminas, así que el contador parece no moverse.
**Qué hacer:** mientras la pantalla de Inicio está abierta, escuchar el sensor (`registerListener`, `TYPE_STEP_COUNTER`) y actualizar en vivo; al salir, dejar de escuchar. Combinar con los pasos de Health Connect (ya existe `combinar`, que toma el mayor). Avisar sobre la exclusión del ahorro de batería en teléfonos que matan el segundo plano.

### 6. Reloj: traer datos solos y mostrarlos
- Sincronizar el reloj **automáticamente** (al abrir la app y cada cierto tiempo), sin que el usuario toque "Sincronizar ahora".
- En Inicio, mostrar "Pulso del reloj: X lpm, hace N min" y los pasos del reloj cuando existan.
- Mantener la honestidad sobre el tiempo real (ver limitaciones).

### 7. Espera del PIN a prueba de cambio de hora  *(seguridad)*
**Problema:** la espera tras fallos usa `System.currentTimeMillis()`; cambiar la hora del teléfono la anula y permite probar PINs de 4 dígitos.
**Qué hacer:** medir la espera con un reloj monótono (`SystemClock.elapsedRealtime()`), que no se puede cambiar; y persistir la espera pendiente para que reiniciar el teléfono tampoco la salte. Ver `seguridad/Pin.kt` y `BloqueoManager.kt`. Prueba unitaria incluida.

### 8. Límite de tamaño en los valores de Firestore  *(seguridad)*
**Problema:** `firestore.rules` limita la cantidad de campos de `props` (10) pero no el tamaño de cada valor; alguien con la clave pública podría escribir textos enormes y gastar la cuota.
**Qué hacer:** aceptar solo los nombres de campo que la app realmente envía y limitar el tamaño de cada valor. Probar en el emulador (`backend/firebase && npm test`): si se escapa un campo legítimo, esos eventos dejan de llegar.

### 9. Texto honesto sobre el bloqueo
En la pantalla **Seguridad**, decir con claridad que el PIN y la huella bloquean la pantalla, y que los datos están cifrados aparte con una clave del teléfono (sin tecnicismos de más).

### 10. Cerrar la versión
Cuando 1–9 estén hechas y probadas: subir a `versionCode = 7`, `versionName = "1.3.3"` y agregar la sección 1.3.3 en `docs/CAMBIOS.md` describiendo estos cambios.

## Fuera de alcance por ahora (no hacer todavía)
- **Compilación release** reducida/ofuscada (R8) + firma: necesita reglas para SQLCipher/Health Connect/WorkManager y pruebas en teléfono real. Dejar para "preparar publicación".
- Enviar correos desde el servidor; SDK propietarios de relojes para tiempo real.

## Pruebas de seguridad que pidió el usuario (su propia app)
La app ya usa consultas parametrizadas y RLS, así que debería resistir inyección SQL. Probar escribiendo en los campos (chat, nombre de contacto, perfil) textos como `'; DROP TABLE conversaciones;--`, `' OR '1'='1`, `admin'--`, `' UNION SELECT * FROM usuarios--`. Esperado: se guardan y muestran como texto literal, no se borra ni aparece nada ajeno, y el código de derivación con esos caracteres se **rechaza** por formato (`INH-` + 6). Probar también RLS: con un usuario, pedir a mano datos de otro id y confirmar que vuelve vacío. Solo en instalaciones y proyecto propios.
