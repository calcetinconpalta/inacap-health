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

### 10. Sacar "base SQL / NoSQL" y nombres de servicios de TODAS las versiones
Hoy "Probar conexión" (`SyncManager.diagnosticar`) muestra "Base SQL (Supabase)" y "Base NoSQL (Firestore)", y `ExplicacionError.explicar` dice "Supabase: …" / "Firestore: …". Se ocultó solo en release, pero el usuario prueba con el APK debug y lo sigue viendo: **no sirve**. Cambiar las etiquetas a lenguaje simple en todas las versiones ("Registros", "Uso de la app", "Conexión") y dejar el nombre técnico solo dentro de "Ver detalle del error".

### 11. Confirmación de correo que no lleve al panel
Hoy el enlace del correo de confirmación de Supabase abre el panel del equipo (la Site URL). Hacer:
- Una página `dashboard/confirmado.html`: "✓ Correo confirmado. Vuelve a la app", con botón "Abrir la app" (deep link `inacaphealth://confirmado`, con su `intent-filter` en el manifest).
- Pasar esa dirección como `redirect_to` al pedir el cambio/alta de correo (`PUT /auth/v1/user` y el registro). Agregarla también en Supabase → Authentication → URL Configuration → Redirect URLs (lo hace el usuario; déjalo escrito en `docs/DESPLIEGUE_Y_SEGURIDAD.md`).
- Al volver a la app (onResume), revisar sola si el correo ya se confirmó y terminar el registro; el botón "Ya confirmé" queda solo como respaldo.
- La verificación ya funciona (sin abrir el correo, "Ya confirmé" dice que no se ve la confirmación). Confirmar que *Confirm email* siga activado en Supabase.

### 12. Cuentas: todas nacen como alumno; el equipo se pre-registra
- Quitar de la app "crear cuenta del equipo". Toda cuenta creada desde la app es **alumno**.
- Nueva tabla en el servidor (p. ej. `personal_invitado`: correo, rol, nombre_visible, creado_por) que llena el **administrador** desde el panel (pestaña Equipo).
- Cuando una cuenta con ese correo inicia sesión o se crea, el servidor le asigna el rol automáticamente **solo si el correo está confirmado** (`email_confirmed_at` no nulo). Sin esto, alguien podría registrarse con el correo de otra persona sin poder abrirlo y quedarse con su rol.
- Pruebas SQL: correo pre-registrado + confirmado → rol; no confirmado → sin rol; no pre-registrado → alumno.

### 13. Una sola sesión activa por cuenta
Al iniciar sesión en un teléfono nuevo, la sesión del teléfono anterior se cierra (decisión del usuario: opción "a"). Implementar en el servidor (p. ej. registrar la sesión vigente por usuario y revocar las anteriores al iniciar sesión) y en la app (si el servidor rechaza la sesión, volver a la pantalla de inicio de sesión con el aviso "Iniciaste sesión en otro teléfono").

### 14. Pedir los datos del perfil justo después de crear la cuenta
- Al terminar de crear la cuenta, ir directo a *Mis datos* con botón **Omitir**.
- Si omite (o entró con una cuenta existente sin datos), mostrar en Inicio un aviso: "Completa tus datos para recomendaciones personalizadas → Completar", que desaparece al completarlos.

### 15. Agua en vasos y litros, con recomendación personalizada
- La meta ya se calcula con el peso (`SaludLogic.metaAguaMl`: 35 ml/kg + extra por actividad); el problema es cómo se muestra.
- Mostrar en **vasos (250 ml) y litros**: "Llevas 3 de 8 vasos (0,75 de 2 L)". Botones "+ 1 vaso" / "Quitar 1 vaso". Nada de "ml" en pantalla.
- Al calcular el IMC (peso y estatura), mostrar la recomendación: "Con tu peso te recomendamos unos 2,3 L al día (9 vasos)" y actualizar la meta.
- Sin peso registrado: meta general y sugerencia de completar los datos.

### 16. Test SCOFF: solo Sí y No
- Hoy hay 4 botones (Sí, No, Atrás, Siguiente) y confunde.
- Dejar **Sí** y **No** grandes y bien separados; al elegir uno aparece **Siguiente** ("Ver resultado" en la última).
- Un enlace de texto chico, separado de los botones, para "Volver a la pregunta anterior". Nada amontonado.

### 17. Pantalla negra al apretar atrás muchas veces
Al retroceder más allá de la primera pantalla el `NavHost` queda sin destino y se ve negro. Desde Inicio (raíz), atrás debe cerrar la app normalmente. Va junto con la tarea 2.

### 18. Historial por mes, semana a semana
- El mes actual se muestra por semanas (semana 1, 2, 3, 4…). Al empezar el mes siguiente, la vista parte de nuevo desde la semana 1.
- Flechas **‹ mes anterior · mes siguiente ›** para ver los meses pasados.
- Aplica a agua, pasos, pulso e IMC.

### 18b. Orientación: recomendar apoyo profesional sin bloquear la consulta
**Problema:** en `OrientacionScreen.kt` → `NuevaConsulta`, si el último SCOFF tiene alerta (`requiereDerivacion`), la pantalla hace `return@Column` y solo deja "Ir a Apoyo profesional": no se puede hacer ninguna consulta general, para siempre, aunque el texto diga "tú decides".
**Qué hacer:**
- Mostrar la tarjeta de recomendación arriba (con su botón), y **debajo** un botón "Seguir con mi consulta general" que muestra el formulario normal.
- La alerta solo cuenta si el SCOFF es **reciente** (últimos 30 días).
- Mantener `SenalesDeAlerta` mientras escribe (aviso, no bloqueo).
- Esto cambia el criterio de aceptación de **HU-13** de "bloquear" a "recomendar sin bloquear": actualizarlo en `docs/Product_Backlog_INACAP_Health.xlsx` y en `docs/CAMBIOS.md`.

### 18c. Sede del alumno (para escalar a más regiones)
- Agregar a *Mis datos* (y al paso de completar datos de la tarea 14) el campo **Sede INACAP**, una lista. Por ahora solo **Valdivia**; se deja preparado para agregar más sedes sin cambiar código (lista en un solo lugar).
- **No** usar GPS ni pedir permiso de ubicación; no pedir dirección.
- A la nube va solo la sede (como hoy `rango_edad`), y solo si comparte datos anonimizados: columna `sede` en `usuarios` (`schema.sql`, con `CHECK` de valores permitidos o tabla `sedes`), y en `Anonimizacion.usuario`.
- Panel: estadísticas por sede, **ocultando las sedes con menos de 5 personas** (para que no se pueda identificar a nadie).
- Mencionar la sede en `legal/TextosLegales.kt` (datos que se comparten) y regenerar `docs/TERMINOS_Y_CONDICIONES.md`.
- Si el usuario prefiere comuna/región de residencia en vez de sede, cambiar solo la lista y el texto.

### 18d. Antecedentes de salud para quien atiende
Decisión del usuario: quien atiende debe conocer los antecedentes (p. ej. hipertensión, alergia al paracetamol).
- Compartir patologías, alergias y medicamentos es **obligatorio para hacer cualquier consulta** (orientación y apoyo). **No** es obligatorio para usar el resto de la app (calculadoras, pasos, pulso, historial).
- Antes de la primera consulta, pantalla que diga: **"Necesitamos saber más de ti para darte la mejor atención"**, con los campos (o lo que ya tenga en *Mis datos*) y un botón para aceptar compartirlos. Sin aceptar, no se puede enviar la consulta, pero se explica por qué.
- Ojo: hoy los cifra la clave de Bienestar, así que solo Bienestar los lee. Quién más debe leerlos (por ejemplo, quien responde en orientación) depende de la definición de roles pendiente; mientras tanto, cifrar para Bienestar.
- Se envían **cifrados de extremo a extremo** con la clave pública de Bienestar (igual que `contacto_cifrado` de las derivaciones, `CifradoE2E.kt`). Ni la base ni los administradores los leen. **No** se usan para estadísticas.
- Se suben **al pedir atención y cada vez que el alumno los edita** (no cada 5 minutos: Android no permite menos de 15 min en segundo plano, gasta batería y casi nunca cambian).
- Al eliminar la cuenta o revocar, se borran.
- Cambiar el aviso "nunca se suben" en onboarding, Privacidad y `TextosLegales.kt` por: "Tus antecedentes de salud se comparten cifrados solo con el equipo que te atiende, cuando pides atención. Nunca se venden, publican ni usan para estadísticas." Subir la versión de los términos (la app pedirá aceptar de nuevo).
- **Regla para el equipo** (en las instrucciones del modo equipo y en los términos): los estudiantes de medicina **no indican ni sugieren medicamentos** por chat; los antecedentes sirven para orientar con seguridad y para saber cuándo derivar a un médico.

### 18e. Rediseño visual aprobado: rojo y blanco INACAP + hero con parallax y gradient mesh
Maqueta: `docs/maqueta-inicio.html` (ábrela en el navegador; los íconos y gatitos son interactivos). Se descartó una versión neumórfica gris porque perdía la identidad de INACAP. Reemplaza el tema actual en `ui/theme/` y las pantallas principales.
- **El rojo INACAP manda:** banda roja arriba con "INACAP Health", el saludo grande ("Hola, Ana") y la fecha. Fondo **blanco/gris muy claro**, no gris medio. Botón principal y pestaña activa en **rojo sólido**.
- **Hero de Inicio dentro de la banda roja**, como tarjeta propia (esquinas ~20 dp, sombra hacia abajo y un borde de luz blanco sutil arriba) para que se despegue de la banda.
  - Fondo del hero: **gradient mesh** (varios degradados radiales que se funden) anclado en el rojo INACAP, con una mezcla distinta por dato: agua = rojo + toque azul; pasos = rojo + naranjo/dorado; pulso = rojo + magenta; IMC = rojo + verde azulado. En Compose: varios `Brush.radialGradient` superpuestos en un `Canvas`/`drawBehind`.
  - **Parallax:** tres capas (malla de fondo con círculos blancos translúcidos, una ola/forma blanca translúcida propia de cada dato, y el dato grande al frente en blanco). Debajo de la banda, una fila de íconos Agua · Pasos · Pulso · IMC. Al elegir uno (o deslizar el hero), las capas avanzan a ese dato con **distinto retraso** (fondo el más lento, frente el más rápido). `HorizontalPager` con capas desplazadas según `currentPageOffsetFraction`, o `animateFloatAsState` con distintos `delayMillis`.
  - **Intensidad ajustable:** al usuario el hero le pareció **que destaca demasiado** y lo va a evaluar en la app. Deja la intensidad de la malla, la sombra y el tamaño del hero en **constantes/tokens en un solo lugar** para poder bajarlas fácil. Parte con una versión más suave que la maqueta (menos saturación y sombra) y muéstrasela.
  - El color propio de cada dato queda solo como un **punto chico** junto a su nombre; ya no es protagonista.
- **Relieve suave** (toque neumórfico) solo en tarjetas e íconos blancos: sombra doble muy sutil. Ícono elegido hundido y en rojo. Esquinas ~16 dp, nada inflado.
- El botón bajo los íconos cambia según el dato: "+ 1 vaso", "Ver mis pasos", "Medir pulso", "Calcular IMC". Botón rojo sólido con un borde inferior más oscuro (leve relieve).
- Barra de pestañas inferior flotante, blanca; la pestaña activa con fondo rojo y texto blanco.
- **El test SCOFF no va en Inicio:** queda en Bienestar (tarea 18f). En Inicio va el check-in de ánimo (tarea 18g).
- **Modo noche:** banda roja algo más oscura, fondo casi negro, malla oscurecida un poco y sombras más marcadas. Revisarlo en el teléfono.
- **Accesibilidad:** texto blanco sobre rojo con buen contraste; respetar "quitar animaciones" del sistema (sin movimiento).
- **Sin morado en las letras:** al usuario no le gustaron las A en morado (no pegan con el rojo INACAP). No pintes letras en morado. El usuario dirá más adelante dónde usar el morado.
- Con este rediseño, el agua se muestra en vasos y litros (tarea 15) y el SCOFF con solo Sí/No (tarea 16).

### 18f. Cuestionarios en Bienestar
Decisión del usuario: agregarlos todos, **PHQ-9 incluido** (solo en Bienestar). Todos de uso libre y validados en español. Van en una sección **"Cuestionarios"** dentro de Bienestar (nunca en Inicio), con preguntas Sí/No u opciones grandes, una por pantalla, como el SCOFF nuevo.
- **SCOFF** (ya existe, 5): trastornos alimentarios.
- **WHO-5** (5): bienestar general.
- **PHQ-2** (2): ánimo. Si sale en alerta (≥3), ofrecer el PHQ-9 completo y recomendar hablar con Bienestar.
- **PHQ-9** (9): solo en Bienestar (decisión del usuario, 2026-11-23). **Protocolo mínimo obligatorio** por la pregunta 9 (pensamientos de muerte o de hacerse daño): si la respuesta a esa pregunta es distinta de "Nunca", **sin importar el puntaje total**, mostrar de inmediato una pantalla de ayuda con la línea de prevención del suicidio ***4141** (MINSAL, gratuita, 24 h), **Salud Responde 600 360 7777** y emergencias **131**, con botones para llamar, más el botón de pedir atención a Bienestar. Esa pantalla no se puede saltar sin tocar "Entiendo". El protocolo de seguimiento del equipo (quién contacta, en cuánto tiempo) lo define el docente supervisor; dejarlo anotado como pendiente en `docs/`.
- **GAD-2 → GAD-7**: ansiedad; el GAD-7 completo solo si el GAD-2 sale en alerta (≥3).
- **PSS-10** (10): estrés percibido.
- **AUDIT-C** (3): consumo de alcohol.
- **IPAQ corto** (7): actividad física.
- No usar MBI ni ISI (requieren licencia).
- Resultados: lenguaje simple, sin diagnóstico ("esto no es un diagnóstico"), y cuando salga alerta, recomendación de Bienestar con el botón de pedir atención. Las respuestas individuales se quedan en el teléfono (como el SCOFF); a la nube solo va el puntaje, y solo si comparte datos anonimizados. Agregar cada cuestionario a `TextosLegales.kt` y a la tabla correspondiente del servidor.
- Verificar el texto exacto de cada pregunta y los puntos de corte en la versión validada en español antes de implementar (no inventarlos).

### 18g. Check-in diario "¿Cómo te sientes hoy?" en Inicio
- Reemplaza al SCOFF en Inicio. Un toque al día con **5 gatitos**: 😿 Muy mal · 🙀 Mal · 😺 Normal · 😸 Bien · 😻 Muy bien.
- Título "¿Cómo te sientes hoy?" y la respuesta ("Hoy te sientes **Bien**"); gatito elegido hundido con un borde de color. El color de ese acento está **por decidir** (el usuario dirá dónde va el morado); mientras tanto, déjalo como un token que se cambie en un solo lugar.
- Se guarda en el teléfono (nueva tabla en Room) y se ve en el historial. Si marca "Mal" o "Muy mal" 3 días seguidos, sugerir con suavidad un cuestionario o hablar con Bienestar.
- Los emojis se ven distinto según la marca del teléfono; si se quiere el mismo dibujo en todos, usar ilustraciones propias de gatitos en vez de emojis.

## Decisiones pendientes (NO implementar hasta que el usuario confirme)
- **Prioridad del usuario:** primero arreglar los problemas de la app (tareas 1–18g). Los roles se definen después.
- **Dónde va el morado:** el usuario lo decidirá viéndolo en la app. Por ahora no se pinta ninguna letra en morado.
- **Roles nuevos:** propuesta en discusión — *Estudiante de medicina* (atiende por chat; hoy rol `bienestar`), *Profesional supervisor* (titulado; hoy "tutor": ve todos los chats, supervisa y puede intervenir), *Administrador*. Falta confirmar si sigue el docente supervisor y si sigue la orientación entre pares con estudiantes de Nutrición. Implica cambiar la RLS de los chats de atención para que el supervisor pueda leerlos, y avisarlo en el consentimiento.
- **Ánimo (confirmado 2026-11-23):** en Inicio va el check-in de gatitos; el PHQ-2 y el PHQ-9 van en Bienestar → Cuestionarios (tarea 18f).

### 19. Cerrar la versión
Cuando las tareas 1–18g estén hechas y probadas: subir a `versionCode = 7`, `versionName = "1.3.3"` y agregar la sección 1.3.3 en `docs/CAMBIOS.md` describiendo estos cambios.

## Fuera de alcance por ahora (no hacer todavía)
- **Compilación release** reducida/ofuscada (R8) + firma: necesita reglas para SQLCipher/Health Connect/WorkManager y pruebas en teléfono real. Dejar para "preparar publicación".
- Enviar correos desde el servidor; SDK propietarios de relojes para tiempo real.

## Pruebas de seguridad que pidió el usuario (su propia app)
La app ya usa consultas parametrizadas y RLS, así que debería resistir inyección SQL. Probar escribiendo en los campos (chat, nombre de contacto, perfil) textos como `'; DROP TABLE conversaciones;--`, `' OR '1'='1`, `admin'--`, `' UNION SELECT * FROM usuarios--`. Esperado: se guardan y muestran como texto literal, no se borra ni aparece nada ajeno, y el código de derivación con esos caracteres se **rechaza** por formato (`INH-` + 6). Probar también RLS: con un usuario, pedir a mano datos de otro id y confirmar que vuelve vacío. Solo en instalaciones y proyecto propios.
