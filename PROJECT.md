# INACAP Health
App Android de salud estudiantil (autocuidado preventivo, "primer filtro", NUNCA diagnostica). Versión de prueba para demostración.
## Stack
Kotlin, Compose, Material 3, Navigation Compose, Room v3 (KSP, cifrada con SQLCipher + Android Keystore, con migraciones y esquemas exportados en app/schemas), WorkManager (sincronización offline y pasos), CameraX (pulso), Health Connect (opcional, API 26+), BiometricPrompt (bloqueo), una sola Activity, DI manual (Application + AndroidViewModel), Recordatorios con AlarmManager (alarmas inexactas; se reprograman tras reiniciar el teléfono). Nube: Supabase (SQL) y Firestore (NoSQL) vía REST con HttpURLConnection, sin SDKs. Claves en local.properties.
## Diseño
Minimalista, fondo blanco, rojo INACAP como color de marca (botones, títulos, íconos). Rojo NUNCA para alertas o riesgo: usar grises neutros. Textos empáticos, sin lenguaje alarmista. Todo texto en español. Colores: primary #D6001C, fondo #FFFFFF, superficie #F6F6F6, borde #E0E0E0, texto #1A1A1A, texto secundario #6B6B6B.
## Estructura
com.inacap.health: HealthApp.kt, MainActivity.kt, ui/theme/, ui/nav/ (Routes.kt, AppNav.kt), ui/components/ (PlaceholderScreen.kt), ui/screens/<modulo>/, data/ (entidades, dao, AppDatabase), data/remote/ (clientes Supabase y Firestore), data/sync/ (SyncManager, SyncWorker, Anonimizacion), legal/ (TextosLegales), util/. Fuera de la app: backend/ (schema.sql, firestore.rules, guía), dashboard/ (panel web), docs/.
## Navegación
Barra inferior con 4 pestañas: Inicio, Salud, Bienestar, Perfil. Las demás pantallas son subrutas.
## Implementar de verdad
IMC, agua, SCOFF, onboarding+consentimiento, perfil, módulo educativo, derivación confidencial, eliminar datos, base local, historial con gráficos, recordatorios.
## Fuera de alcance (placeholder)
Videollamadas con nutricionistas y diagnóstico automático: descartados por el equipo (la app nunca diagnostica).

## Estado
- Prompt 1: base creada. Prompt 2: menús de las pestañas y placeholders hechos.
- Prompt 3: onboarding+consentimiento, perfil y eliminar datos hechos.
- Prompt 4: IMC, agua y dashboard hechos (agua = filas con aguaMl>0; meta y nivel en SharedPreferences health_prefs).
- Prompt 5: SCOFF (guarda ResultadoScoff, requiereDerivacion si puntaje>=2) y derivación simulada hechos.
- Prompt 6: historial con gráficos hecho (Canvas, sin librerías).
- Prompt 7: recordatorios y módulo educativo hechos. Fase de funciones reales completa.
- Prompt 9 (v1.2): todo el backlog: SQLCipher + Keystore, bloqueo PIN/huella, pulso por cámara, pasos, historial de actividad, PDF, Health Connect, derivación cifrada E2E, orientación entre pares con roles y supervisión, términos v2. Ya no quedan placeholders salvo videollamadas (descartado).
- Prompt 10 (v1.3): rediseño calmado/clínico + modo noche (ui/theme/Theme.kt: Apariencia), reloj BLE (salud/RelojBluetooth.kt), modo equipo (equipo/Equipo.kt, ui/screens/equipo/), pantallas sin nombres de servidores, código anónimo ocultable, términos v3, panel con Equipo/Seguridad/Atención, .env + Cloudflare Pages (scripts/generar-config.mjs, _headers), schema v3 (límites, IP, auditoría).
- Prompt 8: términos y condiciones versionados + historial de consentimientos; base SQL (Supabase) + NoSQL (Firestore) con sincronización offline anonimizada y opcional; dashboard web para administradores; corrección de errores (ver docs/CAMBIOS.md).

## Rutas
| Ruta | Pantalla |
| --- | --- |
| onboarding · terminos | Bienvenida, consentimiento y términos |
| inicio · salud · bienestar · perfil | Pestañas |
| imc_agua · historial · pulso · health_connect · resumen_pdf | Salud |
| scoff · derivacion · educativo · orientacion | Bienestar |
| perfil_datos · recordatorios · apariencia · bloqueo · seguridad · privacidad · cuenta · acceso | Perfil |
| equipo_acceso · atencion | Modo equipo y chat de atención del estudiante |
