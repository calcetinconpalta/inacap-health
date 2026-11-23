# Despliegue del panel en Cloudflare Pages y configuración de seguridad

El panel (`dashboard/`) son archivos estáticos: Cloudflare Pages lo publica gratis con HTTPS.

## 1. Variables y secretos (`.env`)

Todas las claves del proyecto están en **`.env`** (raíz). Plantilla: `.env.example`. `.env` está en `.gitignore`.

| Variable | ¿Pública? | Dónde se usa |
|---|---|---|
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | Sí (por diseño) | App y panel |
| `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID` | Sí (por diseño) | Panel (la app usa la clave Android de `local.properties`) |
| `BIENESTAR_CLAVE_PUBLICA` | Sí | App (cifra las derivaciones) |
| `SUPABASE_SECRET_KEY` | **NO** | Solo scripts de administración en tu computador. Salta todas las reglas de seguridad |
| `SUPABASE_DB_PASSWORD` | **NO** | Solo `psql` / respaldos |
| `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` | **NO** | Solo para desplegar con `wrangler` |

- La app lee las claves de `local.properties` y, si no están ahí, de `.env` (y de variables de entorno). La compilación **falla** si encuentra una clave `sb_secret_…`.
- `node scripts/generar-config.mjs` crea `dashboard/config.js` (solo claves públicas) y `dashboard/_headers`. También se detiene si detecta una clave secreta.
- Las claves "públicas" no protegen los datos: los protegen RLS, las funciones con rol y las reglas de Firestore. Aun así, restríngelas (punto 4).

## 2. Publicar en Cloudflare Pages

### Opción A · Desde GitHub (se actualiza solo con cada push)
1. <https://dash.cloudflare.com> → **Workers & Pages → Create → Pages → Connect to Git** → elige el repositorio.
2. Configuración de compilación:
   - **Framework preset:** None
   - **Build command:** `node scripts/generar-config.mjs`
   - **Build output directory:** `dashboard`
3. **Settings → Variables and Secrets** (entorno *Production*): agrega `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`. **No agregues** los secretos.
4. **Save and Deploy**. Queda en `https://inacap-health.pages.dev` (o el nombre que elijas).

### Opción B · Desde tu computador (sin GitHub)
```bash
node scripts/generar-config.mjs
```
```bash
npx wrangler login
```
```bash
npx wrangler pages deploy dashboard --project-name inacap-health
```
(No hay `wrangler.toml` a propósito: si existe, Cloudflare Pages ignora las variables cargadas en su panel y el panel quedaría en modo demostración.)

### Después de publicar
- **Supabase → Authentication → URL Configuration:** *Site URL* = la dirección del panel; *Redirect URLs* = `https://TU-PANEL.pages.dev/restablecer.html` **y** `https://TU-PANEL.pages.dev/confirmado.html`.
  - `confirmado.html` es la página "✓ Correo confirmado · Vuelve a la app" a la que lleva el enlace de confirmación cuando un estudiante crea su cuenta desde la app (la app la manda como `redirect_to`). **Si no está en Redirect URLs, Supabase la ignora y el enlace vuelve a abrir el panel.**
  - Si cambias la dirección del panel, actualiza también `url_restablecer` y `url_confirmado` en `app/src/main/res/values/strings.xml`.
  - Revisa que **Authentication → Sign In / Providers → Email → Confirm email** siga activado: así nadie puede quedarse con un correo que no puede abrir.
- En la app: `app/src/main/res/values/strings.xml` → `url_restablecer` con esa misma dirección.
- **Firebase → Authentication → Settings → Authorized domains:** agrega `TU-PANEL.pages.dev`.
- Opcional: **Cloudflare Access** (Zero Trust, gratis hasta 50 personas) delante del panel para que solo correos del equipo vean siquiera la página de acceso.

## 3. Encabezados de seguridad (`dashboard/_headers`)
Cloudflare Pages los aplica a todas las páginas:
- **Content-Security-Policy:** solo carga scripts del propio sitio, jsDelivr (supabase-js, Chart.js) y gstatic (Firebase); solo se conecta a TU proyecto de Supabase y a las APIs de Firebase. Sin scripts en línea, sin `eval`, sin iframes.
- **HSTS**, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy` (sin cámara, micrófono ni ubicación), `noindex`.
- `config.js` sin caché, para que un cambio de claves se aplique de inmediato.

## 4. Lista de seguridad del servidor

| Medida | Dónde | Estado |
|---|---|---|
| **RLS** en todas las tablas | `schema.sql` | Cada persona solo ve lo suyo; personal según rol |
| **Validación en el servidor** | Disparadores `sanear_entrada`, `CHECK`, funciones que revisan el rol | Texto limpio, largos máximos, formatos (código `INH-XXXXXX`) |
| **Límite por persona** (rate limit) | Disparador `limitar_por_usuario` | Mensajes 30/min, conversaciones 5/h, derivaciones 5/día, registros 500/h… |
| **Límite por IP** | `antes_de_cada_peticion` (PostgREST) | 120 escrituras/min por IP; bloqueo automático 15 min tras 10 rechazos; bloqueo manual desde el panel |
| **Auditoría** | Tabla `auditoria` | Cambios de rol, bloqueos de IP, derivaciones y chats; visible en *Seguridad* |
| **Cifrado** | App (base cifrada + Keystore), E2E en derivaciones, HTTPS | — |
| **Sesión con expiración** | App: 30 min sin uso / 12 h (equipo). Panel: 15 min sin uso / 8 h. Token: ver abajo | — |
| **CORS** | Ver abajo | — |

### Ajustes que se hacen en las consolas (no en el código)
**Supabase**
- *Authentication → Sessions*: **Time-box user sessions** = 12 h; **Inactivity timeout** = 1 h (plan Pro). En el plan gratuito, *JWT expiry* (Settings → JWT Keys) en **3600 s** (1 hora): la app y el panel renuevan el token solos.
- *Authentication → Rate Limits*: correos 3–5/h, inicios de sesión y registros ≤ 30 cada 5 min por IP, verificaciones OTP ≤ 30/h.
- *Authentication → Attack Protection*: activa **CAPTCHA** (Cloudflare Turnstile, gratis) y **Leaked password protection** (Pro).
- *Authentication → Providers → Email*: contraseña mínima 8 caracteres, con letras y dígitos.
- *Settings → API → Data API*: deja expuesto solo el esquema `public`.
- Rota la *secret key* si alguna vez se compartió y nunca la pongas en la app ni en Cloudflare.

**Firebase / Google Cloud**
- *Google Cloud → APIs y servicios → Credenciales*:
  - Clave **Android**: restricción "Apps de Android" con `com.inacap.health` + SHA-1 de tu firma; APIs: Identity Toolkit, Token Service, Cloud Firestore.
  - Clave **Web** (panel): restricción "Sitios web" con `https://TU-PANEL.pages.dev/*` y `http://localhost:8080/*`; mismas 3 APIs.
- *Firestore → Reglas*: publica `backend/firebase/firestore.rules`.
- *App Check* (opcional) con Play Integrity para la app.

### Sobre CORS
- Supabase alojado responde a cualquier origen (no se puede restringir en el plan gratuito). Esto **no** es un riesgo por sí solo: sin un token válido, RLS no deja leer ni escribir nada, y las claves públicas no dan permisos.
- Lo que sí controlamos: la **CSP** del panel (el navegador solo habla con nuestros servidores), las **restricciones de la API key** de Google por sitio web/app, las *Redirect URLs* de Supabase y los *Authorized domains* de Firebase (evitan que otro sitio use nuestros correos de acceso).
- Si en el futuro se agrega un backend propio (Cloudflare Worker), allí se fija `Access-Control-Allow-Origin` solo a la dirección del panel.
