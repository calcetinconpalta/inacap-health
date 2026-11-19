# Configuración de las bases de datos en la nube

INACAP Health usa **dos bases de datos en la nube**, cada una para el tipo de dato que mejor maneja:

| | SQL · **Supabase** (PostgreSQL) | NoSQL · **Firebase Firestore** |
|---|---|---|
| Qué guarda | Datos estructurados y relacionados: usuarios, consentimientos, IMC, agua, pasos, pulso, SCOFF, derivaciones (contacto cifrado E2E), orientación entre pares (conversaciones, mensajes, supervisión) y roles del personal | Eventos de uso: pantallas vistas, acciones, errores de la app |
| Por qué ahí | Tablas con llaves foráneas, restricciones (`CHECK`), borrado en cascada y consultas agregadas con SQL para el dashboard | Alto volumen, esquema flexible (cada evento trae campos distintos), escala sola |
| Archivo | `supabase/schema.sql` | `firebase/firestore.rules` |

Ambas tienen plan gratuito suficiente para el proyecto.

```
Teléfono (Room) ──► sync_pendiente (bandeja offline) ──► WorkManager ──┬─► Supabase  (SQL)
   │  solo si el usuario activó "Compartir datos anonimizados"         └─► Firestore (NoSQL)
   └─ nombre, patologías, alergias, medicamentos, peso y estatura NUNCA salen del teléfono
                                                         Dashboard web ◄── solo administradores
```

Si no configuras nada, la app funciona igual que antes, solo en modo local.

---

## 1. Supabase (base SQL)

1. Crea una cuenta en <https://supabase.com> y un proyecto nuevo (región: São Paulo, la más cercana a Chile).
2. **Activa el inicio de sesión anónimo:** *Authentication → Sign In / Providers → Allow anonymous sign-ins* → activar.
   La app crea una cuenta anónima por instalación, sin correo ni nombre.
3. **Crea las tablas:** *SQL Editor → New query* → pega el contenido completo de `supabase/schema.sql` → **Run**.
   Se puede ejecutar varias veces sin romper nada.
4. **Crea la cuenta del administrador** (para el dashboard): *Authentication → Users → Add user → Create new user* con correo y contraseña (marca *Auto Confirm User*).
5. **Dale permiso de administrador:** en el *SQL Editor* ejecuta (cambia el correo):
   ```sql
   insert into public.administradores (user_id, email)
   select id, email from auth.users where email = 'admin@tu-correo.cl';
   ```
6. Copia las claves en *Project Settings → API*: **Project URL** y **anon public key**.
   ⚠️ **Nunca** uses la `service_role` key en la app ni en el dashboard.

### Cuentas de estudiantes con correo (opcional)
La app funciona sin cuenta (anónima). Si la persona crea una cuenta, su cuenta anónima se **convierte** en una con correo (mismo id, mismos datos) y puede recuperar sus registros en otro teléfono.
1. *Authentication → Sign In / Providers → Email*: debe estar **habilitado**.
2. *Confirm email*: si está activado, la persona recibe un enlace y luego vuelve a la app a terminar. Para probar rápido puedes desactivarlo.
3. *Authentication → URL Configuration*:
   - **Site URL**: la dirección donde publiques el panel (por ejemplo `https://inacap-health.pages.dev`).
   - **Redirect URLs**: agrega `https://inacap-health.pages.dev/restablecer.html` (página para crear una contraseña nueva).
   - En la app, pon esa misma dirección en `app/src/main/res/values/strings.xml` → `url_restablecer`.
4. El correo de Supabase incluido envía **pocos correos por hora**. Para usarlo con muchas personas configura tu propio SMTP (*Authentication → Emails → SMTP Settings*).

### Personal: tutores, docentes, profesionales de salud y administradores
Cada persona del equipo entra con correo y contraseña **en la app (modo equipo) o en el panel web**.
- **Lo normal:** la persona crea su cuenta en la app (*Soy del equipo → Crear tu cuenta*) y un administrador le da el rol en el panel → pestaña **Equipo**.
- **El primer administrador** se crea a mano:
1. *Authentication → Users → Add user* (correo + contraseña, *Auto Confirm User*).
2. En el *SQL Editor*, asígnale su rol (`tutor`, `docente`, `bienestar` = profesional de salud, o `admin`):
   ```sql
   insert into public.personal (user_id, rol, nombre_visible)
   select id, 'tutor', 'Tutora Camila' from auth.users where email = 'tutora@tu-correo.cl';
   ```
   Para quitar el acceso: `update public.personal set activo = false where ...`.

| Rol | Ve en el dashboard | Puede |
|---|---|---|
| `tutor` (tutor par) | Consultas de orientación sin asignar y las suyas | Tomar, responder, cerrar o derivar (app y panel) |
| `docente` (docente supervisor) | Todas las conversaciones de orientación y la bitácora | Responder, registrar revisión u observación, derivar, cerrar, reabrir |
| `bienestar` (profesional de salud) | Derivaciones (contacto cifrado) y sus chats de atención | Descifrar, cambiar estado, **responder por chat** (solo él y el estudiante lo ven) |
| `admin` | Estadísticas agregadas, equipo y seguridad | Dar/quitar roles, bloquear IPs, ver auditoría |

### Clave de Bienestar para derivaciones cifradas (E2E)
1. Abre el dashboard como `bienestar` → pestaña **Derivaciones** → **Generar par de claves nuevo**.
2. Se descarga `clave-privada-bienestar-XXXX.json`. **Guárdala fuera de internet** (pendrive del equipo o gestor de contraseñas). Si se pierde, las solicitudes cifradas con ella no se pueden leer.
3. Copia la línea `BIENESTAR_CLAVE_PUBLICA=...` que aparece en pantalla a `local.properties` de la app y vuelve a compilarla.
4. Para leer solicitudes: **Cargar clave privada (.json)**. La clave queda solo en la memoria del navegador.

### Retención de 24 meses (opcional)
La función `purgar_datos_antiguos()` borra lo que tenga más de 24 meses. Para que corra sola cada día:
*Database → Extensions → pg_cron* (activar) y luego en el SQL Editor:
```sql
select cron.schedule('purgar-inacap-health', '0 4 * * *', $$select public.purgar_datos_antiguos(24)$$);
```

---

## 2. Firebase Firestore (base NoSQL)

1. Entra a <https://console.firebase.google.com> → **Agregar proyecto** (puedes desactivar Google Analytics).
2. **Authentication → Comenzar → Método de acceso:** activa **Anónimo** y **Correo electrónico/contraseña**.
3. **Firestore Database → Crear base de datos** → modo producción → ubicación `southamerica-east1` (São Paulo).
4. **Reglas de seguridad:** *Firestore → Reglas* → reemplaza todo con el contenido de `firebase/firestore.rules` → **Publicar**.
   (O con la CLI: `cd backend/firebase && firebase deploy --only firestore`.)
5. **Cuenta del administrador:** *Authentication → Usuarios → Agregar usuario* (usa el **mismo correo y contraseña** que en Supabase: el dashboard inicia sesión en las dos bases con un solo formulario). Copia su **UID**.
6. **Permiso de administrador:** *Firestore → Iniciar colección* → ID de colección `administradores` → ID del documento = **el UID copiado** → agrega un campo cualquiera, por ejemplo `email` (string) → Guardar.
7. **Claves:** *Configuración del proyecto (⚙️) → General → Tus apps → Agregar app → Web (</>)*. Copia `apiKey`, `authDomain` y `projectId`.

---

## 3. Conectar la app Android

Completa `.env` en la raíz (plantilla `.env.example`) **o** `local.properties` (plantilla `local.properties.example`; tiene prioridad) con:

```properties
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...
FIREBASE_API_KEY=AIzaSy...
FIREBASE_PROJECT_ID=inacap-health-xxxx
```

Luego **Build → Rebuild Project**. Ni `.env` ni `local.properties` se suben a git.

**Probar:** instala la app, en la bienvenida marca *Compartir datos anonimizados*, registra un vaso de agua y espera unos segundos con internet.
Deberías ver filas en Supabase (*Table Editor → registros_biometricos*) y documentos en Firestore (*colección eventos*).
En la app, *Perfil → Privacidad y mis datos* muestra los envíos pendientes y el último envío.

---

## 4. Dashboard web

1. Completa `.env` en la raíz (plantilla `.env.example`) y ejecuta `node scripts/generar-config.mjs`: crea `dashboard/config.js` y `dashboard/_headers`.
2. Ábrelo con un servidor local (los módulos de JavaScript no funcionan con doble clic):
   ```bash
   cd dashboard
   python3 -m http.server 8080
   ```
   y entra a <http://localhost:8080>.
3. Inicia sesión con la cuenta de administrador.
   Sin configurar nada, el botón **Ver con datos de demostración** muestra el panel con datos ficticios (útil para presentar).

Para publicarlo gratis con HTTPS usa **Cloudflare Pages**: pasos en `docs/DESPLIEGUE_Y_SEGURIDAD.md`.
Las claves de `config.js` son públicas por diseño; lo que protege los datos son las políticas RLS y las reglas de Firestore, que solo dejan leer a quien está en `administradores`.

---

## 5. Qué protege cada capa

| Riesgo | Protección |
|---|---|
| Un usuario lee o modifica datos de otro | RLS en Supabase (`usuario_id = auth.uid()`) y reglas de Firestore (`uid == request.auth.uid`) |
| Alguien sin sesión consulta la base | Rol `anon` sin permisos; Firestore exige `request.auth` |
| Datos basura o demasiado grandes | Restricciones `CHECK` en SQL y validación de campos en las reglas de Firestore |
| Envío duplicado al reintentar | El id de cada fila/documento se genera en el teléfono (UUID); reenviar no duplica |
| Sin internet | Bandeja `sync_pendiente` + WorkManager reintenta con espera creciente |
| Cambiar el historial de consentimientos | Disparador en SQL que impide modificar filas existentes |
| El dashboard expone personas | La función `dashboard_resumen()` devuelve solo cifras agregadas y exige ser administrador |
| Derecho de supresión | `eliminar_mis_datos()` borra en cascada (y la cuenta anónima); la app también borra sus eventos en Firestore |
| Personal con más permisos de los necesarios | Rol leído del servidor (tabla `personal`), funciones que validan el rol en cada cambio de estado |
| Lectura de datos de contacto en la nube | Cifrado de extremo a extremo: la base solo guarda texto ilegible; se descifra en el navegador de Bienestar |
| Supervisión sin registro | Bitácora `supervision`, que se conserva (sin contenido) aunque se borre la conversación |

## 6. Pruebas automáticas

- Reglas de Firestore (emulador local, no toca la nube): `cd backend/firebase && npm install && npm test`.
- Esquema SQL en un PostgreSQL local: `backend/supabase/test/README.md` (incluye roles, orientación y derivaciones: `04_roles_orientacion_derivaciones.sql`).

**Pendiente para producción:** revisión legal de los términos, supresión de grupos pequeños en el dashboard (k-anonimato) cuando haya pocos usuarios, CAPTCHA en Supabase Auth y rotación periódica de la clave de Bienestar. Los límites de uso, el bloqueo por IP y la auditoría ya están (ver `docs/DESPLIEGUE_Y_SEGURIDAD.md`).
