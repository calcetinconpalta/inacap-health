// Copia este archivo como config.js y completa los valores.
// config.js NO se sube a git.
//
// Las dos claves de abajo son PÚBLICAS por diseño (Supabase "anon key" y la
// configuración web de Firebase): la seguridad la dan las políticas RLS de
// Supabase y las reglas de Firestore, que solo dejan leer a los administradores.
// NUNCA pongas aquí la "service_role key" de Supabase.
window.INACAP_CONFIG = {
  supabase: {
    url: "https://TU-PROYECTO.supabase.co",
    anonKey: "tu-anon-key",
  },
  firebase: {
    apiKey: "tu-web-api-key",
    authDomain: "tu-proyecto.firebaseapp.com",
    projectId: "tu-proyecto",
  },
};
