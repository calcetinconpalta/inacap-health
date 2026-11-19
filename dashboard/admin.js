// INACAP Health · Paneles del administrador: equipo (roles) y seguridad (IPs, rechazos, auditoría).
// Todo lo que viene del servidor se muestra con textContent (nunca como HTML).
// Los permisos los revisa el servidor: estas funciones rechazan a quien no es administrador.

import { limpiar } from "./personal.js";

const $ = (s, r = document) => r.querySelector(s);
const el = (tag, props = {}, ...hijos) => {
  const n = Object.assign(document.createElement(tag), props);
  hijos.flat().forEach((h) => n.append(h instanceof Node ? h : document.createTextNode(String(h ?? ""))));
  return n;
};
const fecha = (iso) => (iso ? new Date(iso).toLocaleString("es-CL", { dateStyle: "short", timeStyle: "short" }) : "Nunca");
const ROLES = { tutor: "Tutor par", docente: "Docente supervisor", bienestar: "Profesional de salud", admin: "Administrador" };
const ACCIONES = {
  rol_asignado: "Rol asignado", rol_desactivado: "Acceso desactivado", ip_bloqueada: "IP bloqueada",
  ip_desbloqueada: "IP desbloqueada", derivacion_gestionada: "Derivación actualizada", atencion_iniciada: "Chat de atención iniciado",
  ip_bloqueada_auto: "IP bloqueada automáticamente",
};
const IP_VALIDA = /^(\d{1,3}(\.\d{1,3}){3}|[0-9a-f:]{2,39})$/i;
const EMAIL_VALIDO = /^[^\s@]{1,64}@[^\s@]{1,190}\.[a-z]{2,}$/i;

function fuenteSupabase(sb) {
  const ok = ({ data, error }) => { if (error) throw new Error(error.message); return data; };
  return {
    equipo: async () => ok(await sb.rpc("listar_equipo")),
    asignar: async (email, rol, nombre) => ok(await sb.rpc("asignar_rol", { p_email: email, p_rol: rol, p_nombre: nombre })),
    desactivar: async (uid) => ok(await sb.rpc("desactivar_personal", { p_user: uid })),
    seguridad: async () => ok(await sb.rpc("resumen_seguridad")),
    bloquear: async (ip, minutos, motivo) => ok(await sb.rpc("bloquear_ip", { p_ip: ip, p_minutos: minutos, p_motivo: motivo })),
    desbloquear: async (ip) => ok(await sb.rpc("desbloquear_ip", { p_ip: ip })),
  };
}

function fuenteDemo() {
  const hace = (min) => new Date(Date.now() - min * 60000).toISOString();
  const equipo = [
    { user_id: "u1", email: "admin@ejemplo.cl", rol: "admin", nombre_visible: "Coordinación", activo: true, ultimo_acceso: hace(3) },
    { user_id: "u2", email: "camila.tutora@ejemplo.cl", rol: "tutor", nombre_visible: "Camila (Nutrición)", activo: true, ultimo_acceso: hace(55) },
    { user_id: "u3", email: "docente.nutricion@ejemplo.cl", rol: "docente", nombre_visible: "Prof. Soto", activo: true, ultimo_acceso: hace(600) },
    { user_id: "u4", email: "psicologa.bienestar@ejemplo.cl", rol: "bienestar", nombre_visible: "Psicóloga Bienestar", activo: true, ultimo_acceso: hace(140) },
    { user_id: "u5", email: "ex.tutor@ejemplo.cl", rol: "tutor", nombre_visible: "Tutor 2025", activo: false, ultimo_acceso: hace(90000) },
  ];
  const seg = {
    rechazos_24h: 17,
    ips_bloqueadas: [{ ip: "203.0.113.45", hasta: new Date(Date.now() + 12 * 60000).toISOString(), motivo: "Bloqueo automático: demasiados rechazos" }],
    auditoria: [
      { fecha: hace(2), accion: "ip_bloqueada_auto", detalle: { ip: "203.0.113.45" }, actor: "Sistema" },
      { fecha: hace(30), accion: "atencion_iniciada", detalle: {}, actor: "Psicóloga Bienestar" },
      { fecha: hace(240), accion: "rol_asignado", detalle: { rol: "tutor" }, actor: "Coordinación" },
      { fecha: hace(1440), accion: "rol_desactivado", detalle: {}, actor: "Coordinación" },
    ],
  };
  return {
    equipo: async () => equipo.map((p) => ({ ...p })),
    asignar: async (email, rol, nombre) => {
      const p = equipo.find((x) => x.email === email);
      if (p) Object.assign(p, { rol, nombre_visible: nombre, activo: true });
      else equipo.push({ user_id: crypto.randomUUID(), email, rol, nombre_visible: nombre, activo: true, ultimo_acceso: null });
      seg.auditoria.unshift({ fecha: new Date().toISOString(), accion: "rol_asignado", detalle: { rol }, actor: "Tú (demo)" });
    },
    desactivar: async (uid) => { equipo.find((x) => x.user_id === uid).activo = false; },
    seguridad: async () => JSON.parse(JSON.stringify(seg)),
    bloquear: async (ip, minutos, motivo) => {
      seg.ips_bloqueadas = seg.ips_bloqueadas.filter((b) => b.ip !== ip);
      seg.ips_bloqueadas.unshift({ ip, hasta: new Date(Date.now() + minutos * 60000).toISOString(), motivo });
      seg.auditoria.unshift({ fecha: new Date().toISOString(), accion: "ip_bloqueada", detalle: { ip }, actor: "Tú (demo)" });
    },
    desbloquear: async (ip) => { seg.ips_bloqueadas = seg.ips_bloqueadas.filter((b) => b.ip !== ip); },
  };
}

function tablaDe(columnas, filas) {
  if (!filas.length) return el("p", { className: "vacio" }, "Sin registros.");
  return el("table", {},
    el("thead", {}, el("tr", {}, ...columnas.map((c) => el("th", {}, c)))),
    el("tbody", {}, ...filas.map((f) => el("tr", {}, ...f.map((v) => el("td", {}, v))))));
}

// ------------------------------------------------------------------
// Equipo: quién tiene acceso y con qué rol
// ------------------------------------------------------------------
async function panelEquipo(raiz, fuente) {
  const rolSel = el("select", { id: "a-rol" }, ...Object.entries(ROLES).map(([k, v]) => el("option", { value: k }, v)));
  const form = el("form", { className: "a-form" },
    el("label", {}, "Correo de la cuenta", el("input", { type: "email", id: "a-email", required: true, maxLength: 254, autocomplete: "off" })),
    el("label", {}, "Nombre visible", el("input", { type: "text", id: "a-nombre", required: true, maxLength: 60, placeholder: "Ej.: Camila (Nutrición)" })),
    el("label", {}, "Rol", rolSel),
    el("button", { type: "submit", className: "btn-primario" }, "Dar acceso"));
  const aviso = el("p", { className: "nota", role: "status" });
  raiz.replaceChildren(
    el("article", { className: "tarjeta" },
      el("header", {}, el("h3", {}, "Dar o cambiar acceso"),
        el("p", {}, "La persona primero crea su cuenta con correo (en la app: «Soy del equipo» → «Crear tu cuenta»). Luego le asignas el rol aquí.")),
      form, aviso),
    el("article", { className: "tarjeta" },
      el("header", {}, el("h3", {}, "Equipo"), el("p", {}, "Cuentas con acceso al panel y a la app en modo equipo")),
      el("div", { className: "tabla", id: "a-equipo" })));

  async function lista() {
    const filas = await fuente.equipo();
    const t = tablaDe(["Nombre", "Correo", "Rol", "Estado", "Último acceso", ""], filas.map((p) => {
      let accion = "";
      if (p.activo) {
        accion = el("button", { type: "button", className: "btn-icono" }, "Quitar acceso");
        accion.onclick = async () => {
          if (!confirm(`¿Quitar el acceso de ${p.nombre_visible}? Su sesión deja de tener permisos de inmediato.`)) return;
          try { await fuente.desactivar(p.user_id); await lista(); } catch (e) { alert(e.message); }
        };
      }
      return [p.nombre_visible, p.email, ROLES[p.rol] || p.rol, p.activo ? "Activo" : "Sin acceso", fecha(p.ultimo_acceso), accion];
    }));
    $("#a-equipo").replaceChildren(t);
  }

  form.onsubmit = async (e) => {
    e.preventDefault();
    const email = limpiar($("#a-email").value, 254).toLowerCase();
    const nombre = limpiar($("#a-nombre").value, 60).replace(/\n/g, " ");
    if (!EMAIL_VALIDO.test(email)) { aviso.textContent = "Escribe un correo válido."; return; }
    if (nombre.length < 2) { aviso.textContent = "Escribe un nombre visible."; return; }
    if (rolSel.value === "admin" && !confirm("El rol Administrador puede dar y quitar accesos y bloquear IPs. ¿Continuar?")) return;
    try {
      await fuente.asignar(email, rolSel.value, nombre);
      aviso.textContent = `Listo: ${nombre} ahora es ${ROLES[rolSel.value]}.`;
      form.reset();
      await lista();
    } catch (err) { aviso.textContent = err.message; }
  };
  await lista();
}

// ------------------------------------------------------------------
// Seguridad: rechazos, IPs bloqueadas y registro de auditoría
// ------------------------------------------------------------------
async function panelSeguridad(raiz, fuente) {
  const form = el("form", { className: "a-form" },
    el("label", {}, "IP", el("input", { type: "text", id: "s-ip", required: true, maxLength: 45, placeholder: "203.0.113.45" })),
    el("label", {}, "Duración", el("select", { id: "s-min" },
      ...[[15, "15 minutos"], [60, "1 hora"], [1440, "1 día"], [10080, "7 días"], [43200, "30 días"]].map(([v, t]) => el("option", { value: v }, t)))),
    el("label", {}, "Motivo", el("input", { type: "text", id: "s-motivo", maxLength: 200, placeholder: "Ej.: intentos repetidos" })),
    el("button", { type: "submit", className: "btn-primario" }, "Bloquear IP"));
  const aviso = el("p", { className: "nota", role: "status" });
  raiz.replaceChildren(
    el("section", { className: "kpis", id: "s-kpis" }),
    el("article", { className: "tarjeta" },
      el("header", {}, el("h3", {}, "IPs bloqueadas"), el("p", {}, "Las IP con muchos rechazos seguidos se bloquean solas por 15 minutos.")),
      el("div", { className: "tabla", id: "s-ips" }), form, aviso),
    el("article", { className: "tarjeta" },
      el("header", {}, el("h3", {}, "Registro de auditoría"), el("p", {}, "Últimas 50 acciones del personal y del sistema")),
      el("div", { className: "tabla", id: "s-auditoria" })));

  async function cargar() {
    const r = await fuente.seguridad();
    const kpi = (t, v, d) => el("article", { className: "kpi" }, el("p", { className: "k-etiqueta" }, t), el("p", { className: "k-valor" }, v), el("p", { className: "k-detalle" }, d));
    $("#s-kpis").replaceChildren(
      kpi("Rechazos (24 h)", String(r.rechazos_24h ?? 0), "Peticiones frenadas por límites o validación"),
      kpi("IPs bloqueadas", String(r.ips_bloqueadas?.length ?? 0), "Bloqueos vigentes"),
      kpi("Acciones auditadas", String(r.auditoria?.length ?? 0), "En el registro reciente"));
    $("#s-ips").replaceChildren(tablaDe(["IP", "Hasta", "Motivo", ""], (r.ips_bloqueadas || []).map((b) => {
      const quitar = el("button", { type: "button", className: "btn-icono" }, "Desbloquear");
      quitar.onclick = async () => { try { await fuente.desbloquear(b.ip); await cargar(); } catch (e) { alert(e.message); } };
      return [b.ip, fecha(b.hasta), b.motivo || "—", quitar];
    })));
    $("#s-auditoria").replaceChildren(tablaDe(["Fecha", "Quién", "Acción", "Detalle"], (r.auditoria || []).map((a) => [
      fecha(a.fecha), a.actor, ACCIONES[a.accion] || a.accion,
      Object.entries(a.detalle || {}).filter(([k]) => k !== "usuario").map(([k, v]) => `${k}: ${k === "rol" ? ROLES[v] || v : v}`).join(" · ") || "—",
    ])));
  }

  form.onsubmit = async (e) => {
    e.preventDefault();
    const ip = limpiar($("#s-ip").value, 45);
    if (!IP_VALIDA.test(ip)) { aviso.textContent = "Escribe una IP válida (IPv4 o IPv6)."; return; }
    try {
      await fuente.bloquear(ip, Number($("#s-min").value), limpiar($("#s-motivo").value, 200) || "Bloqueo manual");
      aviso.textContent = `IP ${ip} bloqueada.`;
      form.reset();
      await cargar();
    } catch (err) { aviso.textContent = err.message; }
  };
  await cargar();
  return setInterval(cargar, 30000);
}

export async function iniciarAdmin({ sb, demo, raizEquipo, raizSeguridad }) {
  const fuente = demo ? fuenteDemo() : fuenteSupabase(sb);
  const temporizadores = [];
  if (raizEquipo) await panelEquipo(raizEquipo, fuente);
  if (raizSeguridad) temporizadores.push(await panelSeguridad(raizSeguridad, fuente));
  return () => temporizadores.forEach(clearInterval);
}
