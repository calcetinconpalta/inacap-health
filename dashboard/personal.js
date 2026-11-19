// INACAP Health · Paneles del personal: orientación entre pares (tutor/docente),
// atención profesional (chat) y derivaciones cifradas de extremo a extremo (profesional de salud).
// Todo lo que escriben las personas se muestra con textContent (nunca como HTML).

const $ = (s, r = document) => r.querySelector(s);
const el = (tag, props = {}, ...hijos) => {
  const n = Object.assign(document.createElement(tag), props);
  hijos.flat().forEach((h) => n.append(h instanceof Node ? h : document.createTextNode(String(h ?? ""))));
  return n;
};
const fecha = (iso) => new Date(iso).toLocaleString("es-CL", { dateStyle: "short", timeStyle: "short" });
const TEMAS = { alimentacion: "Alimentación", hidratacion: "Hidratación", actividad: "Actividad física", habitos: "Hábitos y descanso", otro: "Otro", apoyo_profesional: "Atención profesional" };
const ESTADOS = { abierta: "Abierta", cerrada: "Cerrada", derivada: "Derivada" };
const ROLES_MSG = { estudiante: "Estudiante", tutor: "Tutor par", docente: "Docente", profesional: "Profesional", sistema: "Aviso" };

// Limpieza antes de enviar (el servidor vuelve a validar): quita caracteres de control e
// invisibles, junta saltos de línea repetidos y limita el largo.
export const limpiar = (t, max = 1000) => String(t ?? "")
  .replace(/\r\n/g, "\n")
  .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u200B-\u200F\u2028-\u202E\u2060-\u2064\uFEFF]/g, "")
  .replace(/\n{3,}/g, "\n\n").trim().slice(0, max);
const CANALES = { bienestar_presencial: "Presencial", bienestar_correo: "Correo", cesfam: "CESFAM" };
const ESTADOS_DER = { pendiente: "Pendiente", en_contacto: "En contacto", atendida: "Atendida" };

// ------------------------------------------------------------------
// Cifrado E2E (WebCrypto). Debe coincidir con CifradoE2E.kt de la app.
// ------------------------------------------------------------------
const b64aBytes = (b64) => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
const bytesAb64 = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf)));
const RSA = { name: "RSA-OAEP", hash: "SHA-256" };

export async function huellaSpki(spkiBytes) {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", spkiBytes));
  return [...h.slice(0, 8)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function generarParDeClaves() {
  const par = await crypto.subtle.generateKey({ ...RSA, modulusLength: 3072, publicExponent: new Uint8Array([1, 0, 1]) }, true, ["encrypt", "decrypt"]);
  const spki = await crypto.subtle.exportKey("spki", par.publicKey);
  const jwk = await crypto.subtle.exportKey("jwk", par.privateKey);
  return { publica: bytesAb64(spki), privadaJwk: jwk, huella: await huellaSpki(spki), par };
}

async function importarPrivada(jwk) {
  const privada = await crypto.subtle.importKey("jwk", jwk, RSA, false, ["decrypt"]);
  const publica = await crypto.subtle.importKey("jwk", { kty: "RSA", n: jwk.n, e: jwk.e, alg: "RSA-OAEP-256", ext: true }, RSA, true, ["encrypt"]);
  return { privada, huella: await huellaSpki(await crypto.subtle.exportKey("spki", publica)) };
}

export async function cifrarSobre(texto, clavePublica, huella) {
  const aes = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, aes, new TextEncoder().encode(texto));
  const ek = await crypto.subtle.encrypt(RSA, clavePublica, await crypto.subtle.exportKey("raw", aes));
  return { v: 1, alg: "RSA-OAEP-256+A256GCM", kid: huella, ek: bytesAb64(ek), iv: bytesAb64(iv), ct: bytesAb64(ct) };
}

export async function descifrarSobre(sobre, privada) {
  const raw = await crypto.subtle.decrypt(RSA, privada, b64aBytes(sobre.ek));
  const aes = await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["decrypt"]);
  const claro = await crypto.subtle.decrypt({ name: "AES-GCM", iv: b64aBytes(sobre.iv) }, aes, b64aBytes(sobre.ct));
  return JSON.parse(new TextDecoder().decode(claro));
}

// ------------------------------------------------------------------
// Fuente de datos: Supabase real o memoria (demostración)
// ------------------------------------------------------------------
function fuenteSupabase(sb, uid) {
  const ok = ({ data, error }) => { if (error) throw new Error(error.message); return data; };
  return {
    uid,
    conversaciones: async () => ok(await sb.from("conversaciones").select("id,tema,estado,tipo,tutor_id,profesional_id,creada_en,actualizada_en").order("actualizada_en", { ascending: false }).limit(200)),
    mensajes: async (id) => ok(await sb.from("mensajes").select("id,rol_autor,texto,creado_en").eq("conversacion_id", id).order("creado_en")),
    bitacora: async (id) => ok(await sb.from("supervision").select("accion,nota,creada_en").eq("conversacion_id", id).order("creada_en")),
    enviar: async (id, rol, texto) => ok(await sb.from("mensajes").insert({ conversacion_id: id, rol_autor: rol, texto: limpiar(texto) })),
    tomar: async (id) => ok(await sb.rpc("tomar_conversacion", { p_id: id })),
    estado: async (id, estado, nota) => ok(await sb.rpc("cambiar_estado_conversacion", { p_id: id, p_estado: estado, p_nota: nota ? limpiar(nota) : null })),
    supervisar: async (id, accion, nota) => ok(await sb.rpc("supervisar", { p_id: id, p_accion: accion, p_nota: nota ? limpiar(nota) : null })),
    derivaciones: async () => ok(await sb.from("derivaciones").select("id,canal,codigo,fecha,estado,contacto_cifrado").not("contacto_cifrado", "is", null).order("fecha", { ascending: false }).limit(200)),
    gestionar: async (id, estado) => ok(await sb.rpc("gestionar_derivacion", { p_id: id, p_estado: estado })),
    iniciarAtencion: async (id) => ok(await sb.rpc("iniciar_atencion", { p_derivacion: id })),
  };
}

async function fuenteDemo() {
  const ahora = Date.now();
  const iso = (min) => new Date(ahora - min * 60000).toISOString();
  const convs = [
    { id: "c1a2b3", tema: "alimentacion", estado: "abierta", tutor_id: null, actualizada_en: iso(8) },
    { id: "c4d5e6", tema: "hidratacion", estado: "abierta", tutor_id: "yo", actualizada_en: iso(40) },
    { id: "c7f8a9", tema: "habitos", estado: "derivada", tutor_id: "otro", actualizada_en: iso(300) },
    { id: "a1b2c3", tema: "apoyo_profesional", tipo: "atencion", estado: "abierta", tutor_id: null, profesional_id: "yo", actualizada_en: iso(15) },
  ];
  convs.forEach((c) => { c.tipo ||= "orientacion"; });
  const msgs = {
    c1a2b3: [{ id: "m1", rol_autor: "estudiante", texto: "Hola, tengo clases de 8 a 18 y casi no alcanzo a almorzar. ¿Qué colaciones me recomiendan?", creado_en: iso(8) }],
    c4d5e6: [
      { id: "m2", rol_autor: "estudiante", texto: "¿Cuánta agua debería tomar si entreno 3 veces por semana?", creado_en: iso(60) },
      { id: "m3", rol_autor: "tutor", texto: "Depende de tu peso y actividad; la calculadora de la app te da una meta. Lleva una botella y toma antes, durante y después del entrenamiento.", creado_en: iso(40) },
    ],
    a1b2c3: [
      { id: "m6", rol_autor: "profesional", texto: "Hola, soy psicóloga de Bienestar Estudiantil. Recibí tu solicitud. ¿Cómo te has sentido esta semana?", creado_en: iso(20) },
      { id: "m7", rol_autor: "estudiante", texto: "Hola, gracias por escribir. Con más calma que antes, pero me cuesta organizar las comidas.", creado_en: iso(15) },
    ],
    c7f8a9: [
      { id: "m4", rol_autor: "estudiante", texto: "Últimamente me salto comidas y me siento culpable cuando como.", creado_en: iso(320) },
      { id: "m5", rol_autor: "sistema", texto: "Para este tema te recomendamos conversar con un profesional. En la app, entra a Bienestar > Apoyo profesional: es confidencial.", creado_en: iso(300) },
    ],
  };
  const bit = { c7f8a9: [{ accion: "derivada", nota: "Señales que requieren evaluación profesional.", creada_en: iso(300) }] };
  // Derivaciones de ejemplo cifradas con un par de claves generado aquí mismo
  const claves = await generarParDeClaves();
  const contactos = [
    { nombre: "Valentina", contacto: "valentina.ejemplo@correo.cl", mensaje: "Prefiero que me escriban por correo.", codigo: "INH-7QK2PA" },
    { nombre: "Matías", contacto: "+56 9 1234 5678", mensaje: "", codigo: "INH-M3X9TB" },
  ];
  const ders = await Promise.all(contactos.map(async (c, i) => ({
    id: `d${i}`, canal: i ? "bienestar_presencial" : "bienestar_correo", codigo: c.codigo, fecha: iso(30 + i * 500),
    estado: i ? "en_contacto" : "pendiente", contacto_cifrado: await cifrarSobre(JSON.stringify(c), claves.par.publicKey, claves.huella),
  })));
  return {
    uid: "yo", demoClave: claves.privadaJwk,
    conversaciones: async () => convs.map((c) => ({ ...c })),
    mensajes: async (id) => (msgs[id] || []).slice(),
    bitacora: async (id) => (bit[id] || []).slice(),
    enviar: async (id, rol, texto) => { (msgs[id] ||= []).push({ id: crypto.randomUUID(), rol_autor: rol, texto, creado_en: new Date().toISOString() }); },
    tomar: async (id) => { convs.find((c) => c.id === id).tutor_id = "yo"; },
    estado: async (id, estado, nota) => {
      convs.find((c) => c.id === id).estado = estado;
      if (estado === "derivada") (msgs[id] ||= []).push({ id: crypto.randomUUID(), rol_autor: "sistema", texto: "Para este tema te recomendamos conversar con un profesional.", creado_en: new Date().toISOString() });
      (bit[id] ||= []).push({ accion: estado, nota, creada_en: new Date().toISOString() });
    },
    supervisar: async (id, accion, nota) => { if (["derivada", "cerrada"].includes(accion)) convs.find((c) => c.id === id).estado = accion; (bit[id] ||= []).push({ accion, nota, creada_en: new Date().toISOString() }); },
    derivaciones: async () => ders.map((d) => ({ ...d })),
    gestionar: async (id, estado) => { ders.find((d) => d.id === id).estado = estado; },
    iniciarAtencion: async (id) => {
      const nueva = { id: `a-${id}`, tema: "apoyo_profesional", tipo: "atencion", estado: "abierta", tutor_id: null, profesional_id: "yo", actualizada_en: new Date().toISOString() };
      if (!convs.some((c) => c.id === nueva.id)) convs.unshift(nueva);
      return nueva.id;
    },
  };
}

// ------------------------------------------------------------------
// Orientación (tutor y docente)
// ------------------------------------------------------------------
async function panelOrientacion(raiz, fuente, rol) {
  let seleccion = null;
  const prof = rol === "bienestar";
  let filtro = rol === "docente" ? "todas" : "abierta";
  raiz.replaceChildren(
    el("div", { className: "orient" },
      el("aside", { className: "tarjeta orient-lista" },
        el("header", {}, el("h3", {}, prof ? "Atención profesional" : rol === "docente" ? "Conversaciones" : "Bandeja de consultas"),
          el("p", {}, prof ? "Chats con estudiantes que pidieron apoyo" : rol === "docente" ? "Supervisión de todas las conversaciones" : "Sin asignar y asignadas a ti")),
        el("div", { className: "segmentado", id: "o-filtro" }),
        el("ul", { className: "o-items", id: "o-items" })),
      el("section", { className: "tarjeta orient-hilo", id: "o-hilo" }, el("p", { className: "vacio" }, "Selecciona una conversación.")))
  );
  const filtros = prof ? ["abierta", "cerrada"] : rol === "docente" ? ["todas", "abierta", "derivada", "cerrada"] : ["abierta", "mias"];
  const nombres = { todas: "Todas", abierta: "Abiertas", derivada: "Derivadas", cerrada: "Cerradas", mias: "Mías" };
  filtros.forEach((f) => {
    const b = el("button", { type: "button", className: f === filtro ? "activo" : "" }, nombres[f]);
    b.onclick = () => { filtro = f; [...$("#o-filtro").children].forEach((x) => x.classList.toggle("activo", x === b)); lista(); };
    $("#o-filtro").append(b);
  });

  async function lista() {
    let convs = (await fuente.conversaciones()).filter((c) => prof ? c.tipo === "atencion" && c.profesional_id === fuente.uid : (c.tipo || "orientacion") === "orientacion");
    if (filtro === "mias") convs = convs.filter((c) => c.tutor_id === fuente.uid);
    else if (filtro !== "todas") convs = convs.filter((c) => c.estado === filtro);
    const ul = $("#o-items");
    ul.replaceChildren(...convs.map((c) => {
      const li = el("li", { className: c.id === seleccion ? "sel" : "" },
        el("strong", {}, TEMAS[c.tema] || c.tema),
        el("span", {}, `Estudiante #${c.id.slice(0, 6)} · ${ESTADOS[c.estado]}${prof ? "" : c.tutor_id ? (c.tutor_id === fuente.uid ? " · tuya" : " · asignada") : " · sin asignar"}`),
        el("small", {}, fecha(c.actualizada_en)));
      li.onclick = () => { seleccion = c.id; lista(); hilo(c); };
      return li;
    }));
    if (!convs.length) ul.append(el("li", { className: "vacio" }, "No hay conversaciones."));
    const actual = convs.find((c) => c.id === seleccion);
    if (actual) hilo(actual, true);
  }

  async function hilo(c, silencioso = false) {
    const caja = $("#o-hilo");
    const [msgs, bit] = await Promise.all([fuente.mensajes(c.id), rol === "docente" ? fuente.bitacora(c.id) : []]);
    const borrador = silencioso ? $("#o-texto")?.value || "" : "";
    const mia = prof ? c.profesional_id === fuente.uid : c.tutor_id === fuente.uid;
    const acciones = el("div", { className: "o-acciones" });
    const boton = (t, fn, primario) => { const b = el("button", { type: "button", className: primario ? "btn-primario" : "btn-icono" }, t); b.onclick = async () => { try { await fn(); await lista(); } catch (e) { alert(e.message); } }; acciones.append(b); };
    if (rol === "tutor" && c.estado === "abierta" && !c.tutor_id) boton("Tomar consulta", () => fuente.tomar(c.id), true);
    if ((rol === "tutor" && mia && c.estado === "abierta") || rol === "docente") {
      if (c.estado === "abierta") {
        boton("Derivar a profesional", () => rol === "docente" ? fuente.supervisar(c.id, "derivada", prompt("Motivo (queda en la bitácora):") || "") : fuente.estado(c.id, "derivada"));
        boton("Cerrar", () => rol === "docente" ? fuente.supervisar(c.id, "cerrada", "") : fuente.estado(c.id, "cerrada"));
      }
    }
    if (prof && mia) {
      if (c.estado === "abierta") boton("Cerrar atención", () => fuente.estado(c.id, "cerrada"));
      else boton("Reabrir", () => fuente.estado(c.id, "abierta"));
    }
    if (rol === "docente") {
      boton("Registrar revisión", () => fuente.supervisar(c.id, "revisada", ""));
      boton("Observación…", async () => { const n = prompt("Observación para la bitácora:"); if (n) await fuente.supervisar(c.id, "observacion", n); });
      if (c.estado !== "abierta") boton("Reabrir", () => fuente.supervisar(c.id, "reabierta", ""));
    }
    const puedeEscribir = c.estado === "abierta" && ((rol === "tutor" && mia) || rol === "docente" || (prof && mia));
    caja.replaceChildren(
      el("header", {}, el("h3", {}, `${TEMAS[c.tema] || c.tema} · Estudiante #${c.id.slice(0, 6)}`), el("p", {}, `${ESTADOS[c.estado]} · actualizada ${fecha(c.actualizada_en)}`)),
      acciones,
      el("p", { className: "nota" }, prof ? "Atención confidencial: solo tú y la persona ven esta conversación. Ante riesgo vital, sigue el protocolo de urgencia." : "Solo orientación educativa: sin diagnósticos, dietas ni tratamientos. Ante señales de TCA o riesgo, deriva."),
      el("div", { className: "o-mensajes" }, ...msgs.map((m) => el("div", { className: `o-msg ${m.rol_autor}` }, el("small", {}, `${ROLES_MSG[m.rol_autor]} · ${fecha(m.creado_en)}`), el("p", {}, m.texto)))),
      puedeEscribir ? el("form", { className: "o-form", id: "o-form" }, el("textarea", { id: "o-texto", rows: 2, maxLength: 1000, placeholder: "Escribe tu respuesta…" }), el("button", { className: "btn-primario", type: "submit" }, "Enviar")) : "",
      rol === "docente" ? el("div", { className: "o-bitacora" }, el("h4", {}, "Bitácora de supervisión"),
        bit.length ? el("ul", {}, ...bit.map((b) => el("li", {}, `${fecha(b.creada_en)} · ${b.accion}${b.nota ? ": " + b.nota : ""}`))) : el("p", { className: "vacio" }, "Sin registros.")) : ""
    );
    const form = $("#o-form");
    if (form) {
      $("#o-texto").value = borrador;
      form.onsubmit = async (e) => {
        e.preventDefault();
        const t = limpiar($("#o-texto").value);
        if (!t) return;
        try { await fuente.enviar(c.id, prof ? "profesional" : rol === "docente" ? "docente" : "tutor", t); $("#o-texto").value = ""; await lista(); } catch (err) { alert(err.message); }
      };
    }
  }

  await lista();
  return setInterval(lista, 10000);
}

// ------------------------------------------------------------------
// Derivaciones (Bienestar)
// ------------------------------------------------------------------
async function panelDerivaciones(raiz, fuente) {
  let clave = null;
  raiz.replaceChildren(
    el("article", { className: "tarjeta" },
      el("header", {}, el("h3", {}, "Clave privada de Bienestar"),
        el("p", {}, "Los datos de contacto llegan cifrados desde el teléfono. Se descifran aquí, en tu navegador: la clave nunca se sube a internet.")),
      el("div", { className: "o-acciones" },
        el("label", { className: "btn-icono archivo" }, "Cargar clave privada (.json)", el("input", { type: "file", accept: ".json,application/json", id: "d-archivo", hidden: true })),
        el("button", { type: "button", className: "btn-icono", id: "d-generar" }, "Generar par de claves nuevo")),
      el("p", { className: "nota", id: "d-estado-clave" }, "Sin clave cargada: los contactos se muestran cifrados."),
      el("div", { id: "d-generadas" })),
    el("article", { className: "tarjeta" }, el("header", {}, el("h3", {}, "Solicitudes con datos de contacto"), el("p", {}, "Más recientes primero")), el("div", { className: "tabla", id: "d-tabla" }))
  );

  async function usarJwk(jwk) {
    clave = await importarPrivada(jwk);
    $("#d-estado-clave").textContent = `Clave cargada (huella ${clave.huella}). Se borra al cerrar o recargar la página.`;
    await tabla();
  }
  $("#d-archivo").onchange = async (e) => {
    try { await usarJwk(JSON.parse(await e.target.files[0].text())); } catch { $("#d-estado-clave").textContent = "Ese archivo no es una clave privada válida."; }
  };
  $("#d-generar").onclick = async () => {
    if (!confirm("Se creará un par de claves NUEVO. Las solicitudes cifradas con la clave anterior solo se podrán leer con la clave anterior. ¿Continuar?")) return;
    const g = await generarParDeClaves();
    const blob = new Blob([JSON.stringify(g.privadaJwk, null, 2)], { type: "application/json" });
    const a = el("a", { href: URL.createObjectURL(blob), download: `clave-privada-bienestar-${g.huella}.json` }); a.click();
    $("#d-generadas").replaceChildren(
      el("p", {}, `1) Se descargó la clave PRIVADA (huella ${g.huella}). Guárdala en un lugar seguro y compártela solo con el equipo de Bienestar.`),
      el("p", {}, "2) Copia esta clave PÚBLICA en local.properties de la app y vuelve a compilarla:"),
      el("textarea", { className: "clave-publica", readOnly: true, rows: 4, value: `BIENESTAR_CLAVE_PUBLICA=${g.publica}` })
    );
    await usarJwk(g.privadaJwk);
  };

  async function tabla() {
    const filas = await fuente.derivaciones();
    const t = el("table", {}, el("thead", {}, el("tr", {}, ...["Fecha", "Código", "Canal", "Contacto", "Estado", "Chat"].map((h) => el("th", {}, h)))));
    const tb = el("tbody");
    for (const d of filas) {
      let contacto = "Cifrado";
      if (clave) {
        try {
          const c = await descifrarSobre(d.contacto_cifrado, clave.privada);
          contacto = [c.nombre, c.contacto, c.mensaje].filter(Boolean).join(" · ");
        } catch { contacto = d.contacto_cifrado.kid && d.contacto_cifrado.kid !== clave.huella ? `Cifrado con otra clave (${d.contacto_cifrado.kid})` : "No se pudo descifrar"; }
      }
      const sel = el("select", {}, ...Object.entries(ESTADOS_DER).map(([k, v]) => el("option", { value: k, selected: k === d.estado }, v)));
      sel.onchange = async () => { try { await fuente.gestionar(d.id, sel.value); } catch (e) { alert(e.message); sel.value = d.estado; } };
      const chat = el("button", { type: "button", className: "btn-icono" }, "Responder por chat");
      chat.onclick = async () => {
        try { await fuente.iniciarAtencion(d.id); alert("Chat listo. Ábrelo en la pestaña Atención: la persona lo verá en la app, en Apoyo profesional → Ver mensajes."); }
        catch (e) { alert(e.message); }
      };
      tb.append(el("tr", {}, el("td", {}, fecha(d.fecha)), el("td", {}, d.codigo), el("td", {}, CANALES[d.canal] || d.canal), el("td", {}, contacto), el("td", {}, sel), el("td", {}, chat)));
    }
    t.append(tb);
    $("#d-tabla").replaceChildren(filas.length ? t : el("p", { className: "vacio" }, "No hay solicitudes con contacto."));
  }
  await tabla();
  if (fuente.demoClave) $("#d-estado-clave").textContent = "Demostración: carga la clave de ejemplo para descifrar.";
  if (fuente.demoClave) {
    const b = el("button", { type: "button", className: "btn-primario" }, "Usar clave de demostración");
    b.onclick = async () => { await usarJwk(fuente.demoClave); b.remove(); };
    $("#d-estado-clave").after(b);
  }
}

// ------------------------------------------------------------------
let demoCompartida = null;
export async function iniciarPersonal({ sb, demo, rol, raizOrientacion, raizDerivaciones }) {
  let uid = null;
  if (!demo) uid = (await sb.auth.getUser()).data.user?.id;
  // En la demostración los datos se conservan al cambiar de pestaña.
  const fuente = demo ? (demoCompartida ||= await fuenteDemo()) : fuenteSupabase(sb, uid);
  const temporizadores = [];
  if (raizOrientacion && (rol === "tutor" || rol === "docente" || rol === "bienestar")) temporizadores.push(await panelOrientacion(raizOrientacion, fuente, rol));
  if (raizDerivaciones && (rol === "bienestar" || rol === "admin")) await panelDerivaciones(raizDerivaciones, fuente);
  return () => temporizadores.forEach(clearInterval);
}
