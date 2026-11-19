# INACAP Health

App Android de autocuidado para estudiantes (Kotlin + Jetpack Compose). Orienta, **no diagnostica**.

## Qué hay en esta carpeta

| Carpeta | Contenido |
|---|---|
| `app/` | App Android (modo claro y noche). Base local cifrada (SQLCipher), bloqueo con PIN/huella, pulso con cámara o **reloj por Bluetooth**, pasos, Health Connect, PDF, orientación entre pares, derivación cifrada y **modo equipo** (tutor par, profesional de salud, docente supervisor) |
| `backend/` | **Bases de datos en la nube**: `supabase/schema.sql` (SQL) y `firebase/firestore.rules` (NoSQL), con la guía de configuración en `backend/README.md` |
| `dashboard/` | **Panel web** por roles: estadísticas, equipo y seguridad (admin), orientación (tutor y docente), solicitudes y chats de atención (profesional). Se publica en Cloudflare Pages |
| `scripts/` | `generar-config.mjs`: crea la configuración y los encabezados de seguridad del panel desde `.env` |
| `.env.example` | Plantilla de **todas** las claves y secretos (copiar como `.env`, nunca subir a git) |
| `docs/` | `TERMINOS_Y_CONDICIONES.md`, `CAMBIOS.md` y `DESPLIEGUE_Y_SEGURIDAD.md` (Cloudflare, sesiones, límites, CORS) |

## Arquitectura de datos

```
                 ┌──────────── Teléfono ─────────────┐
 Usuario ──────► │ Room (SQLite): perfil, IMC, agua, │
                 │ SCOFF, consentimientos            │
                 │        │ solo si acepta compartir │
                 │        ▼                          │
                 │ sync_pendiente (bandeja offline)  │
                 └────────┬──────────────────────────┘
                          │ WorkManager (cuando hay internet)
              ┌───────────┴────────────┐
              ▼                        ▼
   SQL · Supabase (PostgreSQL)   NoSQL · Firestore
   usuarios, consentimientos,    eventos de uso
   IMC/agua, SCOFF, derivaciones (pantallas, acciones, errores)
              └───────────┬────────────┘
                          ▼
              Panel web del equipo (Cloudflare Pages · cada rol ve solo lo suyo)
```

## Empezar

1. Abre el proyecto en Android Studio y ejecútalo: funciona en **modo local** sin configurar nada.
2. Para activar la nube y el dashboard, sigue [`backend/README.md`](backend/README.md).
3. Para ver el dashboard con datos de ejemplo:
   ```bash
   cd dashboard && python3 -m http.server 8080
   ```
   Abre <http://localhost:8080> y presiona **Ver con datos de demostración**.

Pruebas: `./gradlew testDebugUnitTest` (app), `cd backend/firebase && npm test` (reglas de Firestore, 10) y `backend/supabase/test/` (SQL, 5 suites).

Publicar el panel: `docs/DESPLIEGUE_Y_SEGURIDAD.md`.
