#!/usr/bin/env node
// Genera dashboard/config.js y dashboard/_headers a partir de variables de entorno
// (en Cloudflare Pages: Settings → Variables) o del archivo .env de la raíz.
//
//   node scripts/generar-config.mjs
//
// Solo copia valores PÚBLICOS. Si encuentra un secreto en las variables del panel, se detiene.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = join(dirname(fileURLToPath(import.meta.url)), "..");
const env = { ...leerEnv(join(raiz, ".env")), ...process.env };

function leerEnv(ruta) {
  if (!existsSync(ruta)) return {};
  const r = {};
  for (const linea of readFileSync(ruta, "utf8").split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m && !linea.trimStart().startsWith("#")) r[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
  return r;
}

const supabaseUrl = (env.SUPABASE_URL || "").replace(/\/+$/, "");
const supabaseKey = env.SUPABASE_PUBLISHABLE_KEY || env.SUPABASE_ANON_KEY || env.SUPABASE_KEY || "";

// Protección: la clave secreta o service_role nunca debe llegar al navegador.
const pareceSecreta = (k) => k.startsWith("sb_secret_") || /"role"\s*:\s*"service_role"/.test(decodificarJwt(k));
function decodificarJwt(k) {
  try { return Buffer.from(k.split(".")[1], "base64url").toString(); } catch { return ""; }
}
if (pareceSecreta(supabaseKey)) {
  console.error("✖ SUPABASE_PUBLISHABLE_KEY contiene una clave SECRETA. Usa la publishable/anon key.");
  process.exit(1);
}
if (supabaseUrl && !/^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(supabaseUrl)) {
  console.error(`✖ SUPABASE_URL no parece válida: ${supabaseUrl}`);
  process.exit(1);
}

const config = {
  supabase: { url: supabaseUrl, anonKey: supabaseKey },
  firebase: {
    apiKey: env.FIREBASE_API_KEY || "",
    authDomain: env.FIREBASE_AUTH_DOMAIN || (env.FIREBASE_PROJECT_ID ? `${env.FIREBASE_PROJECT_ID}.firebaseapp.com` : ""),
    projectId: env.FIREBASE_PROJECT_ID || "",
    ...(env.FIREBASE_APP_ID ? { appId: env.FIREBASE_APP_ID } : {}),
  },
};
writeFileSync(join(raiz, "dashboard", "config.js"),
  `// Generado por scripts/generar-config.mjs: no editar a mano. Solo contiene claves públicas.\nwindow.INACAP_CONFIG = ${JSON.stringify(config, null, 2)};\n`);

// Encabezados de seguridad para Cloudflare Pages (CSP restringida al proyecto real).
const host = supabaseUrl ? new URL(supabaseUrl).host : "*.supabase.co";
const csp = [
  "default-src 'self'",
  "script-src 'self' https://cdn.jsdelivr.net https://www.gstatic.com",
  "style-src 'self' https://fonts.googleapis.com",
  "font-src https://fonts.gstatic.com",
  `connect-src 'self' https://${host} wss://${host} https://firestore.googleapis.com https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://cdn.jsdelivr.net https://www.gstatic.com`,
  "img-src 'self' data: blob:",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "upgrade-insecure-requests",
].join("; ");
writeFileSync(join(raiz, "dashboard", "_headers"), `# Generado por scripts/generar-config.mjs (Cloudflare Pages).
/*
  Content-Security-Policy: ${csp}
  Strict-Transport-Security: max-age=31536000; includeSubDomains
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: no-referrer
  Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=(), bluetooth=()
  Cross-Origin-Opener-Policy: same-origin
  X-Robots-Tag: noindex, nofollow

/config.js
  Cache-Control: no-store
`);

const faltan = [!supabaseUrl && "SUPABASE_URL", !supabaseKey && "SUPABASE_PUBLISHABLE_KEY", !config.firebase.apiKey && "FIREBASE_API_KEY", !config.firebase.projectId && "FIREBASE_PROJECT_ID"].filter(Boolean);
console.log(`✔ dashboard/config.js y dashboard/_headers generados${faltan.length ? ` (faltan: ${faltan.join(", ")}: el panel solo mostrará la demostración)` : ""}.`);
