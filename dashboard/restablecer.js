// Página para crear una contraseña nueva desde el enlace del correo de recuperación.
const msg = (t) => { const m = document.getElementById("msg"); m.textContent = t; m.hidden = false; };
const cfg = window.INACAP_CONFIG?.supabase;
if (!cfg) { msg("Esta página no está configurada todavía."); throw new Error("sin config"); }
const { createClient } = await import("https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.118.0/+esm");
const sb = createClient(cfg.url, cfg.anonKey, { auth: { persistSession: false, detectSessionInUrl: true, flowType: "implicit" } });
// El enlace trae una sesión de recuperación en la URL; supabase-js la lee sola.
const { data } = await sb.auth.getSession();
if (!data.session) {
  document.getElementById("sub").textContent = "Este enlace no es válido o ya venció. Pide uno nuevo desde la app (¿Olvidaste tu contraseña?).";
} else {
  document.getElementById("form").hidden = false;
  document.getElementById("form").onsubmit = async (e) => {
    e.preventDefault();
    const p1 = document.getElementById("p1").value, p2 = document.getElementById("p2").value;
    if (p1 !== p2) return msg("Las contraseñas no coinciden.");
    if (!(p1.length >= 8 && /[a-zA-Z]/.test(p1) && /\d/.test(p1))) return msg("Usa al menos 8 caracteres, con letras y números.");
    document.getElementById("btn").disabled = true;
    const { error } = await sb.auth.updateUser({ password: p1 });
    if (error) { document.getElementById("btn").disabled = false; return msg("No se pudo guardar: " + error.message); }
    await sb.auth.signOut();
    document.getElementById("form").hidden = true;
    document.getElementById("sub").textContent = "Listo. Ya puedes iniciar sesión en la app con tu nueva contraseña.";
  };
}
