// INACAP Health · Panel de datos y del equipo
// Lee DOS bases:
//   · SQL (Supabase): función dashboard_resumen() → solo cifras agregadas.
//   · NoSQL (Firestore): colección "eventos" → se agrega aquí, en el navegador.
// Ambas exigen iniciar sesión como administrador (RLS / reglas de Firestore).

import { iniciarPersonal } from "./personal.js";
import { iniciarAdmin } from "./admin.js";

const CFG = window.INACAP_CONFIG || null;
const URL_SUPABASE_SDK = "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.118.0/+esm";
const URL_FIREBASE = "https://www.gstatic.com/firebasejs/10.12.2";
const LIMITE_EVENTOS = 5000;
const ZONA = "America/Santiago";

const $ = (sel) => document.querySelector(sel);
const estado = {
  modo: null,          // "real" | "demo"
  dias: 30,
  sb: null,            // cliente Supabase
  fb: null,            // { auth, db, fs }
  graficos: {},
  ultimo: null,        // { sql, eventos }
  rol: null,           // admin | tutor | docente | bienestar
  pestana: null,
  detenerPersonal: null,
};

const PESTANAS = {
  admin: [["estadisticas", "Estadísticas"], ["equipo", "Equipo"], ["seguridad", "Seguridad"], ["derivaciones", "Derivaciones"]],
  tutor: [["orientacion", "Solicitudes y chats"]],
  docente: [["orientacion", "Supervisión"]],
  bienestar: [["derivaciones", "Solicitudes"], ["orientacion", "Atención"]],
};
const NOMBRES_ROL = { admin: "Administrador", tutor: "Tutor par", docente: "Docente supervisor", bienestar: "Profesional de salud" };

// Sesión: se cierra sola tras 15 min sin uso o 8 h desde que se inició (además de la expiración del token en el servidor).
const SESION = { INACTIVIDAD_MS: 15 * 60_000, MAXIMA_MS: 8 * 3_600_000 };
const sesion = { inicio: 0, actividad: 0, reloj: null };

// ------------------------------------------------------------------
// Utilidades
// ------------------------------------------------------------------
const nf = new Intl.NumberFormat("es-CL");
const num = (n) => (n === null || n === undefined || Number.isNaN(n) ? "—" : nf.format(n));
const pct = (a, b) => (b > 0 ? `${Math.round((a / b) * 100)}%` : "—");
const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
const diaChile = (fecha) => new Intl.DateTimeFormat("en-CA", { timeZone: ZONA }).format(fecha);
const fechaCorta = (iso) => {
  const [a, m, d] = iso.split("-").map(Number);
  return new Date(a, m - 1, d).toLocaleDateString("es-CL", { day: "numeric", month: "short" });
};
const escapar = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function tabla(id, columnas, filas) {
  const el = document.getElementById(id);
  if (!filas.length) {
    el.innerHTML = '<p class="vacio">Sin datos en el período.</p>';
    return;
  }
  const th = columnas.map((c) => `<th class="${c.num ? "num" : ""}">${escapar(c.t)}</th>`).join("");
  const tr = filas
    .map((f) => `<tr>${f.map((v, i) => `<td class="${columnas[i].num ? "num" : ""}">${escapar(columnas[i].num ? num(v) : v)}</td>`).join("")}</tr>`)
    .join("");
  el.innerHTML = `<table><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table>`;
}

const ETIQUETAS = {
  imc: { bajo_rango: "Bajo el rango", en_rango: "En rango", sobre_rango: "Sobre el rango" },
  canal: { bienestar_presencial: "Bienestar (presencial)", bienestar_correo: "Bienestar (correo)", cesfam: "CESFAM" },
  edad: ["<18", "18-24", "25-29", "30-39", "40+", "sin_dato"],
  pantalla: {
    inicio: "Inicio", salud: "Salud", bienestar: "Bienestar", perfil: "Perfil", onboarding: "Bienvenida",
    terminos: "Términos", imc_agua: "Calculadoras", historial: "Historial", scoff: "Test SCOFF",
    derivacion: "Apoyo profesional", educativo: "Aprende", perfil_datos: "Mis datos",
    recordatorios: "Recordatorios", privacidad: "Privacidad",
  },
  evento: {
    app_abierta: "App abierta", pantalla_vista: "Pantalla vista", agua_registrada: "Agua registrada",
    agua_quitada: "Agua quitada", imc_calculado: "IMC calculado", scoff_completado: "Test SCOFF completado",
    derivacion_solicitada: "Derivación solicitada", recordatorio_cambiado: "Recordatorio cambiado",
    terminos_aceptados: "Términos aceptados", nube_activada: "Envío a la nube activado", error_app: "Error de la app",
  },
};
const etiqueta = (grupo, clave) => ETIQUETAS[grupo][clave] || (clave === "sin_dato" ? "Sin dato" : clave);

// ------------------------------------------------------------------
// Conexión
// ------------------------------------------------------------------
const sqlConfigurado = () => !!(CFG?.supabase?.url && CFG.supabase.anonKey && !CFG.supabase.url.includes("TU-PROYECTO"));
const nosqlConfigurado = () => !!(CFG?.firebase?.apiKey && CFG.firebase.projectId && !CFG.firebase.apiKey.startsWith("tu-"));

async function iniciarSesion(email, password) {
  const errores = [];
  if (sqlConfigurado()) {
    try {
      const { createClient } = await import(URL_SUPABASE_SDK);
      estado.sb = createClient(CFG.supabase.url, CFG.supabase.anonKey, { auth: { persistSession: false } });
      const { error } = await estado.sb.auth.signInWithPassword({ email, password });
      if (error) throw error;
      const { data: rol, error: e2 } = await estado.sb.rpc("rol_actual");
      if (e2) throw e2;
      if (!rol) throw new Error("tu cuenta no tiene un rol asignado en la tabla personal");
      estado.rol = rol;
      // Un profesional de salud que también es administrador ve además las pestañas de administración.
      estado.tambienAdmin = false;
      if (rol !== "admin") {
        const { data: adm } = await estado.sb.rpc("es_admin");
        estado.tambienAdmin = adm === true;
      }
    } catch (e) {
      estado.sb = null;
      errores.push(`Base SQL: ${e.message}`);
    }
  }
  if (nosqlConfigurado()) {
    try {
      const [app, auth, fs] = await Promise.all([
        import(`${URL_FIREBASE}/firebase-app.js`),
        import(`${URL_FIREBASE}/firebase-auth.js`),
        import(`${URL_FIREBASE}/firebase-firestore.js`),
      ]);
      const fbApp = app.initializeApp(CFG.firebase, "panel");
      const fbAuth = auth.getAuth(fbApp);
      await auth.signInWithEmailAndPassword(fbAuth, email, password);
      estado.fb = { auth: fbAuth, authMod: auth, db: fs.getFirestore(fbApp), fs };
    } catch (e) {
      estado.fb = null;
      // El personal que no es administrador no necesita cuenta en Firebase.
      if (!estado.sb || estado.rol === "admin") errores.push(`Base NoSQL: ${e.code || e.message}`);
    }
  }
  if (!estado.sb && estado.fb) estado.rol = "admin";
  return errores;
}

async function cerrarSesion() {
  try { await estado.sb?.auth.signOut(); } catch { /* sin acción */ }
  try { await estado.fb?.authMod.signOut(estado.fb.auth); } catch { /* sin acción */ }
  estado.sb = null;
  estado.fb = null;
}

async function cargarSql(dias) {
  if (!estado.sb) return null;
  const { data, error } = await estado.sb.rpc("dashboard_resumen", { dias });
  if (error) throw new Error(`Base SQL: ${error.message}`);
  return data;
}

async function cargarEventos(dias) {
  if (!estado.fb) return null;
  const { fs, db } = estado.fb;
  const desde = fs.Timestamp.fromDate(new Date(Date.now() - dias * 86400000));
  const q = fs.query(
    fs.collection(db, "eventos"),
    fs.where("ts", ">=", desde),
    fs.orderBy("ts", "desc"),
    fs.limit(LIMITE_EVENTOS),
  );
  const snap = await fs.getDocs(q);
  return snap.docs.map((d) => {
    const x = d.data();
    return { uid: x.uid, tipo: x.tipo, ts: x.ts.toDate(), props: x.props || {}, version: x.version_app || "" };
  });
}

// ------------------------------------------------------------------
// Agregación de eventos (NoSQL)
// ------------------------------------------------------------------
function agregarEventos(eventos, dias, demo = false) {
  const dias_ = [];
  for (let i = dias - 1; i >= 0; i--) dias_.push(diaChile(new Date(Date.now() - i * 86400000)));
  const porDia = Object.fromEntries(dias_.map((d) => [d, { eventos: 0, uids: new Set() }]));
  const pantallas = {}, tipos = {}, errores = [], instalaciones = new Set();
  for (const e of eventos) {
    const d = diaChile(e.ts);
    if (porDia[d]) { porDia[d].eventos++; porDia[d].uids.add(e.uid); }
    instalaciones.add(e.uid);
    tipos[e.tipo] = (tipos[e.tipo] || 0) + 1;
    if (e.tipo === "pantalla_vista" && e.props.pantalla) pantallas[e.props.pantalla] = (pantallas[e.props.pantalla] || 0) + 1;
    if (e.tipo === "error_app") errores.push(e);
  }
  const orden = (o) => Object.entries(o).sort((a, b) => b[1] - a[1]);
  return {
    total: eventos.length,
    truncado: !demo && eventos.length >= LIMITE_EVENTOS,
    instalaciones: instalaciones.size,
    serie: dias_.map((d) => ({ dia: d, eventos: porDia[d].eventos, activos: porDia[d].uids.size })),
    pantallas: orden(pantallas),
    tipos: orden(tipos),
    errores: errores.slice(0, 20),
  };
}

// ------------------------------------------------------------------
// Datos de demostración (para presentar sin conectar las bases)
// ------------------------------------------------------------------
function rng(semilla) {
  return () => {
    semilla |= 0; semilla = (semilla + 0x6d2b79f5) | 0;
    let t = Math.imul(semilla ^ (semilla >>> 15), 1 | semilla);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function datosDemo(dias) {
  const r = rng(42 + dias);
  const ent = (a, b) => Math.floor(a + r() * (b - a + 1));
  const serie = [];
  for (let i = dias - 1; i >= 0; i--) {
    const fecha = new Date(Date.now() - i * 86400000);
    const finde = [0, 6].includes(fecha.getDay());
    const k = finde ? 0.45 : 1;
    serie.push({
      dia: diaChile(fecha),
      nuevos_usuarios: Math.round(ent(1, 6) * k),
      registros_imc: Math.round(ent(4, 14) * k),
      registros_agua: Math.round(ent(40, 95) * k),
      tests_scoff: Math.round(ent(2, 9) * k),
      derivaciones: Math.round(ent(0, 2) * k),
    });
  }
  const suma = (c) => serie.reduce((a, s) => a + s[c], 0);
  const tests = suma("tests_scoff");
  const sugiere = Math.round(tests * 0.18);
  const sql = {
    periodo_dias: dias,
    usuarios: { total: 412, nuevos: suma("nuevos_usuarios"), activos: Math.min(412, 120 + dias * 3) },
    demografia: {
      rango_edad: { "<18": 6, "18-24": 241, "25-29": 92, "30-39": 48, "40+": 14, sin_dato: 11 },
      sexo: { Femenino: 198, Masculino: 171, Otro: 9, "Prefiero no decir": 21, sin_dato: 13 },
    },
    imc: { registros: suma("registros_imc"), usuarios: 188, promedio: 24.3, categorias: { bajo_rango: 14, en_rango: 109, sobre_rango: 65 } },
    agua: { registros: suma("registros_agua"), promedio_diario_ml: 1450, dias_registrados: 900 },
    scoff: {
      tests, sugiere_derivacion: sugiere,
      puntajes: { 0: Math.round(tests * 0.52), 1: tests - Math.round(tests * 0.52) - sugiere, 2: Math.round(sugiere * 0.6), 3: Math.round(sugiere * 0.3), 4: sugiere - Math.round(sugiere * 0.6) - Math.round(sugiere * 0.3), 5: 0 },
    },
    derivaciones: { total: suma("derivaciones"), por_canal: { bienestar_presencial: Math.ceil(suma("derivaciones") * 0.5), bienestar_correo: Math.floor(suma("derivaciones") * 0.3), cesfam: suma("derivaciones") - Math.ceil(suma("derivaciones") * 0.5) - Math.floor(suma("derivaciones") * 0.3) } },
    consentimientos: { terminos_por_version: { v1: 412 }, registrados_periodo: suma("nuevos_usuarios") * 3 + 7 },
    privacidad: { eliminaciones: Math.max(1, Math.round(dias / 10)), revocaciones: Math.max(1, Math.round(dias / 8)), eliminaciones_total: 9, revocaciones_total: 14 },
    retencion: { meses: 24, registros_vencidos: 0 },
    pulso: { mediciones: 96, promedio: 74, por_fuente: { camara: 61, health_connect: 35 } },
    pasos: { dias_registrados: 2140, promedio_diario: 6380 },
    orientacion: { conversaciones: 38, abiertas: 6, sin_tutor: 2, derivadas: 4, mensajes: 214, supervisiones: 51 },
    derivaciones_contacto: { con_contacto: 11, pendientes: 3 },
    serie,
  };
  const eventos = [];
  const pantallas = ["inicio", "inicio", "inicio", "imc_agua", "historial", "salud", "bienestar", "scoff", "educativo", "perfil", "recordatorios", "privacidad", "derivacion"];
  const acciones = ["agua_registrada", "agua_registrada", "agua_registrada", "imc_calculado", "scoff_completado", "recordatorio_cambiado", "app_abierta", "app_abierta"];
  for (const s of serie) {
    const [a, m, d] = s.dia.split("-").map(Number);
    const usuarios = Math.round(s.registros_agua * 0.9 + 10);
    const n = usuarios * ent(5, 8);
    for (let j = 0; j < n; j++) {
      const ts = new Date(a, m - 1, d, ent(8, 22), ent(0, 59));
      const uid = `u${ent(1, usuarios)}`;
      if (r() < 0.62) eventos.push({ uid, tipo: "pantalla_vista", ts, props: { pantalla: pantallas[ent(0, pantallas.length - 1)] } });
      else eventos.push({ uid, tipo: acciones[ent(0, acciones.length - 1)], ts, props: {} });
    }
    if (r() < 0.25) eventos.push({ uid: "u1", tipo: "error_app", ts: new Date(a, m - 1, d, 13), props: { detalle: r() < 0.5 ? "java.lang.IllegalStateException @ HistorialScreen.BarChart:612" : "java.net.SocketTimeoutException @ SyncManager.procesar:188" }, version: "1.1" });
  }
  return { sql, eventos };
}

// ------------------------------------------------------------------
// Gráficos (Chart.js)
// ------------------------------------------------------------------
function opcionesBase({ leyenda = false, horizontal = false } = {}) {
  const t2 = css("--texto-2"), grilla = css("--grilla"), tarjeta = css("--tarjeta"), texto = css("--texto"), borde = css("--borde");
  const ejeValor = { beginAtZero: true, grid: { color: grilla }, border: { display: false }, ticks: { color: t2, precision: 0, font: { size: 11 } } };
  const ejeCat = { grid: { display: false }, border: { color: borde }, ticks: { color: t2, font: { size: 11 }, maxRotation: 0, autoSkip: true, autoSkipPadding: 12 } };
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    indexAxis: horizontal ? "y" : "x",
    interaction: { mode: "index", intersect: false },
    plugins: {
      legend: { display: leyenda, position: "top", align: "start", labels: { color: texto, usePointStyle: true, pointStyle: "rectRounded", boxWidth: 8, boxHeight: 8, padding: 14, font: { size: 12 } } },
      tooltip: {
        backgroundColor: tarjeta, titleColor: texto, bodyColor: texto, borderColor: borde, borderWidth: 1,
        padding: 10, cornerRadius: 8, usePointStyle: true, boxWidth: 8, boxHeight: 8,
        callbacks: { label: (c) => ` ${c.dataset.label ? c.dataset.label + ": " : ""}${num(c.parsed[horizontal ? "x" : "y"])}` },
      },
    },
    scales: horizontal ? { x: ejeValor, y: { ...ejeCat, ticks: { ...ejeCat.ticks, autoSkip: false } } } : { x: ejeCat, y: ejeValor },
  };
}

function dibujar(id, config) {
  estado.graficos[id]?.destroy();
  const canvas = document.getElementById(id);
  estado.graficos[id] = new Chart(canvas, config);
}

function barras(id, etiquetas, valores, { horizontal = false, nombre = "" } = {}) {
  dibujar(id, {
    type: "bar",
    data: {
      labels: etiquetas,
      datasets: [{
        label: nombre, data: valores, backgroundColor: css("--serie-1"),
        borderRadius: 4, borderSkipped: "start", maxBarThickness: horizontal ? 18 : 40,
        categoryPercentage: 0.8, barPercentage: 0.9,
      }],
    },
    options: opcionesBase({ horizontal }),
  });
}

function lineas(id, etiquetas, series) {
  const colores = ["--serie-1", "--serie-2", "--serie-3", "--serie-4"].map(css);
  dibujar(id, {
    type: "line",
    data: {
      labels: etiquetas,
      datasets: series.map((s, i) => ({
        label: s.nombre, data: s.valores, borderColor: colores[i], backgroundColor: colores[i],
        borderWidth: 2, pointRadius: 0, pointHoverRadius: 4, pointHoverBorderWidth: 2,
        pointHoverBorderColor: css("--tarjeta"), cubicInterpolationMode: "monotone",
      })),
    },
    options: opcionesBase({ leyenda: series.length > 1 }),
  });
}

// ------------------------------------------------------------------
// Render
// ------------------------------------------------------------------
function kpi(etiquetaTxt, valor, detalle) {
  return `<div class="kpi"><div class="k-etiqueta">${escapar(etiquetaTxt)}</div><div class="k-valor">${escapar(valor)}</div><div class="k-detalle">${escapar(detalle)}</div></div>`;
}

function render() {
  const { sql, eventos } = estado.ultimo;
  const ev = eventos ? agregarEventos(eventos, estado.dias, estado.modo === "demo") : null;
  const d = estado.dias;

  // KPIs
  const k = [];
  if (sql) {
    k.push(kpi("Usuarios que comparten", num(sql.usuarios.total), `+${num(sql.usuarios.nuevos)} en ${d} días`));
    k.push(kpi("Activos en el período", num(sql.usuarios.activos), `${pct(sql.usuarios.activos, sql.usuarios.total)} del total`));
    k.push(kpi("IMC promedio", sql.imc.promedio != null ? String(sql.imc.promedio).replace(".", ",") : "—", `${num(sql.imc.usuarios)} usuarios con registro`));
    k.push(kpi("Agua diaria promedio", sql.agua.promedio_diario_ml ? `${num(sql.agua.promedio_diario_ml)} ml` : "—", `${num(sql.agua.registros)} registros`));
    k.push(kpi("Tests SCOFF", num(sql.scoff.tests), `${pct(sql.scoff.sugiere_derivacion, sql.scoff.tests)} sugieren consultar`));
    k.push(kpi("Derivaciones", num(sql.derivaciones.total), "solicitudes de apoyo"));
  }
  if (sql?.pasos) k.push(kpi("Pasos diarios promedio", num(sql.pasos.promedio_diario), `${num(sql.pasos.dias_registrados)} días registrados`));
  if (sql?.pulso) k.push(kpi("Pulso promedio", sql.pulso.promedio ? `${num(sql.pulso.promedio)} lpm` : "—", `${num(sql.pulso.mediciones)} mediciones`));
  if (sql?.orientacion) k.push(kpi("Orientación entre pares", num(sql.orientacion.conversaciones), `${num(sql.orientacion.sin_tutor)} esperan tutor · ${num(sql.orientacion.derivadas)} derivadas`));
  if (sql?.derivaciones_contacto) k.push(kpi("Derivaciones con contacto", num(sql.derivaciones_contacto.con_contacto), `${num(sql.derivaciones_contacto.pendientes)} pendientes de gestión`));
  if (ev) k.push(kpi("Eventos de uso", num(ev.total), `${num(ev.instalaciones)} instalaciones activas`));
  if (sql) k.push(kpi("Borrados y revocaciones", num(sql.privacidad.eliminaciones + sql.privacidad.revocaciones), `en ${d} días`));
  $("#kpis").innerHTML = k.join("") || '<p class="vacio">Sin datos.</p>';

  // SQL
  if (sql) {
    const etq = sql.serie.map((s) => fechaCorta(s.dia));
    lineas("g-serie-agua", etq, [{ nombre: "Agua", valores: sql.serie.map((s) => s.registros_agua) }]);
    lineas("g-serie", etq, [
      { nombre: "IMC", valores: sql.serie.map((s) => s.registros_imc) },
      { nombre: "Tests SCOFF", valores: sql.serie.map((s) => s.tests_scoff) },
      { nombre: "Derivaciones", valores: sql.serie.map((s) => s.derivaciones) },
    ]);
    tabla("t-serie", [{ t: "Día" }, { t: "Nuevos usuarios", num: 1 }, { t: "Agua", num: 1 }, { t: "IMC", num: 1 }, { t: "SCOFF", num: 1 }, { t: "Derivaciones", num: 1 }],
      sql.serie.map((s) => [fechaCorta(s.dia), s.nuevos_usuarios, s.registros_agua, s.registros_imc, s.tests_scoff, s.derivaciones]));

    const cats = ["bajo_rango", "en_rango", "sobre_rango"];
    barras("g-imc", cats.map((c) => etiqueta("imc", c)), cats.map((c) => sql.imc.categorias[c] || 0), { nombre: "Usuarios" });
    tabla("t-imc", [{ t: "Rango" }, { t: "Usuarios", num: 1 }, { t: "%", num: 0 }],
      cats.map((c) => [etiqueta("imc", c), sql.imc.categorias[c] || 0, pct(sql.imc.categorias[c] || 0, sql.imc.usuarios)]));
    $("#p-imc").textContent = `Último IMC de cada usuario · referencia OMS, no es diagnóstico`;

    const puntajes = [0, 1, 2, 3, 4, 5];
    barras("g-scoff", puntajes.map(String), puntajes.map((p) => sql.scoff.puntajes[p] || 0), { nombre: "Tests" });
    tabla("t-scoff", [{ t: "Puntaje" }, { t: "Tests", num: 1 }], puntajes.map((p) => [String(p), sql.scoff.puntajes[p] || 0]));

    const edades = ETIQUETAS.edad.filter((e) => sql.demografia.rango_edad[e]);
    barras("g-edad", edades.map((e) => etiqueta("edad", e)), edades.map((e) => sql.demografia.rango_edad[e]), { nombre: "Usuarios" });
    const sexos = Object.entries(sql.demografia.sexo).sort((a, b) => b[1] - a[1]);
    tabla("t-edad", [{ t: "Grupo" }, { t: "Usuarios", num: 1 }],
      [...edades.map((e) => [`Edad ${etiqueta("edad", e)}`, sql.demografia.rango_edad[e]]), ...sexos.map(([s, n]) => [`Sexo: ${s === "sin_dato" ? "Sin dato" : s}`, n])]);

    const canales = Object.keys(ETIQUETAS.canal);
    barras("g-deriv", canales.map((c) => etiqueta("canal", c)), canales.map((c) => sql.derivaciones.por_canal[c] || 0), { nombre: "Solicitudes" });
    tabla("t-deriv", [{ t: "Canal" }, { t: "Solicitudes", num: 1 }], canales.map((c) => [etiqueta("canal", c), sql.derivaciones.por_canal[c] || 0]));

    const versiones = Object.entries(sql.consentimientos.terminos_por_version);
    tabla("t-privacidad", [{ t: "Indicador" }, { t: `Últimos ${d} días`, num: 1 }, { t: "Total", num: 1 }], [
      ["Consentimientos registrados", sql.consentimientos.registrados_periodo, null],
      ...versiones.map(([v, n]) => [`Usuarios con términos ${v} aceptados`, null, n]),
      ["Revocaciones del envío a la nube", sql.privacidad.revocaciones, sql.privacidad.revocaciones_total],
      ["Eliminaciones de todos los datos", sql.privacidad.eliminaciones, sql.privacidad.eliminaciones_total],
      [`Registros con más de ${sql.retencion.meses} meses (a purgar)`, null, sql.retencion.registros_vencidos],
    ]);
  } else {
    ["t-serie", "t-imc", "t-scoff", "t-edad", "t-deriv", "t-privacidad"].forEach((id) => tabla(id, [], []));
    ["g-serie", "g-serie-agua", "g-imc", "g-scoff", "g-edad", "g-deriv"].forEach((id) => { estado.graficos[id]?.destroy(); });
  }

  // NoSQL
  if (ev) {
    const etq = ev.serie.map((s) => fechaCorta(s.dia));
    barras("g-eventos", etq, ev.serie.map((s) => s.eventos), { nombre: "Eventos" });
    barras("g-dau", etq, ev.serie.map((s) => s.activos), { nombre: "Instalaciones" });
    tabla("t-eventos", [{ t: "Día" }, { t: "Eventos", num: 1 }, { t: "Instalaciones activas", num: 1 }], ev.serie.map((s) => [fechaCorta(s.dia), s.eventos, s.activos]));
    $("#p-eventos").textContent = ev.truncado
      ? `Basado en los ${num(LIMITE_EVENTOS)} eventos más recientes del período`
      : `${num(ev.total)} eventos de uso en el período`;

    const top = ev.pantallas.slice(0, 10);
    barras("g-pantallas", top.map(([p]) => etiqueta("pantalla", p)), top.map(([, n]) => n), { horizontal: true, nombre: "Vistas" });
    tabla("t-pantallas", [{ t: "Pantalla" }, { t: "Vistas", num: 1 }], ev.pantallas.map(([p, n]) => [etiqueta("pantalla", p), n]));
    tabla("t-tipos", [{ t: "Acción" }, { t: "Eventos", num: 1 }, { t: "%" }], ev.tipos.map(([t, n]) => [etiqueta("evento", t), n, pct(n, ev.total)]));
    tabla("t-errores", [{ t: "Fecha" }, { t: "Detalle técnico" }, { t: "Versión" }],
      ev.errores.map((e) => [e.ts.toLocaleString("es-CL", { timeZone: ZONA, dateStyle: "short", timeStyle: "short" }), e.props.detalle || "—", e.version || "—"]));
  } else {
    ["t-eventos", "t-pantallas", "t-tipos", "t-errores"].forEach((id) => tabla(id, [], []));
    ["g-eventos", "g-dau", "g-pantallas"].forEach((id) => { estado.graficos[id]?.destroy(); });
  }

  tabla("t-inventario", [{ t: "Dato" }, { t: "Teléfono" }, { t: "SQL (Supabase)" }, { t: "NoSQL (Firestore)" }, { t: "Retención en la nube" }], [
    ["Nombre, patologías, alergias, medicamentos", "Sí", "No", "No", "—"],
    ["Edad / sexo", "Sí (exacta)", "Rango de edad y sexo", "No", "24 meses"],
    ["Peso y estatura", "Sí", "No", "No", "—"],
    ["IMC y rango de referencia", "Sí", "Sí", "No", "24 meses"],
    ["Consumo de agua", "Sí", "Sí", "No", "24 meses"],
    ["Test SCOFF", "Respuestas y puntaje", "Solo puntaje", "No", "24 meses"],
    ["Solicitud de derivación", "No", "Canal y código", "No", "24 meses"],
    ["Consentimientos", "Historial", "Historial", "No", "Mientras exista el usuario"],
    ["Pantallas y acciones", "No", "No", "Sí", "24 meses"],
    ["Errores de la app", "No", "No", "Tipo y ubicación en el código", "24 meses"],
  ]);
}

async function cargar() {
  const btn = $("#btn-actualizar");
  btn.disabled = true;
  $("#estado").textContent = "Cargando…";
  try {
    if (estado.modo === "demo") {
      estado.ultimo = datosDemo(estado.dias);
      $("#estado").textContent = "Datos de demostración generados al azar: no corresponden a personas reales.";
    } else {
      const avisos = [];
      const [sql, eventos] = await Promise.all([
        cargarSql(estado.dias).catch((e) => { avisos.push(e.message); return null; }),
        cargarEventos(estado.dias).catch((e) => { avisos.push(`Base NoSQL: ${e.code || e.message}`); return null; }),
      ]);
      estado.ultimo = { sql, eventos };
      const hora = new Date().toLocaleTimeString("es-CL", { hour: "2-digit", minute: "2-digit" });
      $("#estado").textContent = avisos.length ? `Actualizado ${hora}. Avisos: ${avisos.join(" · ")}` : `Actualizado a las ${hora}.`;
    }
    render();
  } finally {
    btn.disabled = false;
  }
}

function mostrarPanel(modo) {
  estado.modo = modo;
  if (modo === "demo") estado.rol = $("#demo-rol").value;
  $("#vista-login").hidden = true;
  $("#vista-panel").hidden = false;
  $("#demo-rol").hidden = modo !== "demo";
  const doble = modo !== "demo" && estado.tambienAdmin && estado.rol !== "admin";
  const nombreRol = doble ? `${NOMBRES_ROL[estado.rol]} · ${NOMBRES_ROL.admin}` : NOMBRES_ROL[estado.rol];
  const partes = modo === "demo" ? ["Demostración", nombreRol] : [nombreRol];
  $("#chip-modo").textContent = partes.join(" · ");
  const tabs = doble ? [...(PESTANAS[estado.rol] || []), ...PESTANAS.admin.filter(([id]) => id !== "derivaciones")] : (PESTANAS[estado.rol] || []);
  $("#pestanas").replaceChildren(...tabs.map(([id, nombre]) => {
    const b = document.createElement("button");
    b.type = "button"; b.textContent = nombre; b.dataset.id = id;
    b.onclick = () => abrirPestana(id);
    return b;
  }));
  abrirPestana(tabs[0]?.[0]);
}

async function abrirPestana(id) {
  estado.pestana = id;
  estado.detenerPersonal?.();
  estado.detenerPersonal = null;
  document.querySelectorAll("#pestanas button").forEach((b) => b.classList.toggle("activo", b.dataset.id === id));
  const stats = id === "estadisticas";
  $("#vista-estadisticas").hidden = !stats;
  $("#periodo").hidden = !stats;
  $("#btn-actualizar").hidden = !stats;
  $("#vista-orientacion").hidden = id !== "orientacion";
  $("#vista-derivaciones").hidden = id !== "derivaciones";
  $("#vista-equipo").hidden = id !== "equipo";
  $("#vista-seguridad").hidden = id !== "seguridad";
  $("#estado").textContent = estado.modo === "demo" ? "Datos de demostración generados al azar: no corresponden a personas reales." : "";
  if (stats) return cargar();
  try {
    if (id === "equipo" || id === "seguridad") {
      estado.detenerPersonal = await iniciarAdmin({
        sb: estado.sb, demo: estado.modo === "demo",
        raizEquipo: id === "equipo" ? $("#vista-equipo") : null,
        raizSeguridad: id === "seguridad" ? $("#vista-seguridad") : null,
      });
      return;
    }
    estado.detenerPersonal = await iniciarPersonal({
      sb: estado.sb, demo: estado.modo === "demo", rol: estado.rol,
      raizOrientacion: id === "orientacion" ? $("#vista-orientacion") : null,
      raizDerivaciones: id === "derivaciones" ? $("#vista-derivaciones") : null,
    });
  } catch (e) {
    $("#estado").textContent = `No se pudo cargar: ${e.message}`;
  }
}

// ------------------------------------------------------------------
// Eventos de la interfaz
// ------------------------------------------------------------------
$("#nota-config").textContent = sqlConfigurado() || nosqlConfigurado()
  ? "Acceso solo para el equipo de INACAP Health. Tu sesión se cierra sola tras 15 minutos sin uso."
  : "Panel sin configurar (falta config.js). Mientras tanto puedes ver la demostración.";
if (!sqlConfigurado() && !nosqlConfigurado()) $("#btn-entrar").disabled = true;

$("#form-login").addEventListener("submit", async (e) => {
  e.preventDefault();
  const btn = $("#btn-entrar");
  btn.disabled = true;
  btn.textContent = "Entrando…";
  $("#login-error").hidden = true;
  const errores = await iniciarSesion($("#email").value.trim(), $("#password").value);
  btn.disabled = false;
  btn.textContent = "Entrar";
  if (!estado.sb && !estado.fb) {
    $("#login-error").textContent = errores.join(" · ") || "No se pudo iniciar sesión.";
    $("#login-error").hidden = false;
    return;
  }
  $("#password").value = "";
  iniciarReloj();
  mostrarPanel("real");
});

// ------------------------------------------------------------------
// Expiración de la sesión
// ------------------------------------------------------------------
function iniciarReloj() {
  sesion.inicio = sesion.actividad = Date.now();
  clearInterval(sesion.reloj);
  sesion.reloj = setInterval(() => {
    const ahora = Date.now();
    if (ahora - sesion.actividad > SESION.INACTIVIDAD_MS || ahora - sesion.inicio > SESION.MAXIMA_MS) salir("Tu sesión se cerró por seguridad. Vuelve a ingresar.");
  }, 20_000);
}
["pointerdown", "keydown", "scroll"].forEach((ev) => addEventListener(ev, () => { sesion.actividad = Date.now(); }, { passive: true }));

async function salir(mensaje) {
  clearInterval(sesion.reloj);
  await cerrarSesion();
  estado.detenerPersonal?.();
  estado.detenerPersonal = null;
  estado.rol = null;
  estado.tambienAdmin = false;
  Object.values(estado.graficos).forEach((g) => g.destroy());
  estado.graficos = {};
  ["#vista-orientacion", "#vista-derivaciones", "#vista-equipo", "#vista-seguridad"].forEach((v) => $(v).replaceChildren());
  $("#vista-panel").hidden = true;
  $("#vista-login").hidden = false;
  if (mensaje) { $("#login-error").textContent = mensaje; $("#login-error").hidden = false; }
}

$("#btn-demo").addEventListener("click", () => { iniciarReloj(); mostrarPanel("demo"); });
$("#demo-rol").addEventListener("change", () => mostrarPanel("demo"));
$("#btn-actualizar").addEventListener("click", cargar);
$("#btn-salir").addEventListener("click", () => salir());

// Modo claro / noche (se recuerda en este navegador).
const TEMA_KEY = "inacap-panel-tema";
function aplicarTema(t) {
  if (t) document.documentElement.dataset.theme = t; else delete document.documentElement.dataset.theme;
  const oscuro = t ? t === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
  document.querySelectorAll(".btn-tema").forEach((b) => { b.textContent = oscuro ? "Modo claro" : "Modo noche"; });
  if (estado.ultimo) render();
}
let temaGuardado = null;
try { temaGuardado = localStorage.getItem(TEMA_KEY); } catch { /* sin almacenamiento */ }
aplicarTema(temaGuardado);
document.querySelectorAll(".btn-tema").forEach((b) => b.addEventListener("click", () => {
  const oscuro = document.documentElement.dataset.theme ? document.documentElement.dataset.theme === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
  const nuevo = oscuro ? "light" : "dark";
  try { localStorage.setItem(TEMA_KEY, nuevo); } catch { /* sin almacenamiento */ }
  aplicarTema(nuevo);
}));
document.querySelectorAll(".segmentado button").forEach((b) =>
  b.addEventListener("click", () => {
    document.querySelectorAll(".segmentado button").forEach((x) => x.classList.toggle("activo", x === b));
    estado.dias = Number(b.dataset.dias);
    cargar();
  }),
);
window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => estado.ultimo && render());
