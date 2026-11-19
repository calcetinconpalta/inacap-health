// Pruebas de firestore.rules con el emulador (no usa la nube real).
// Ejecutar:  cd backend/firebase && npm install && npm test
import { readFileSync } from "node:fs";
import { test, before, after, beforeEach } from "node:test";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";

let env;
const evento = (uid, extra = {}) => ({
  uid, tipo: "agua_registrada", ts: new Date(), props: { ml: 250 }, version_app: "1.1", android_sdk: 34, ...extra,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-inacap-health",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc("administradores/admin1").set({ email: "admin@ejemplo.cl" });
    await db.doc("eventos/de-b").set(evento("usuarioB"));
  });
});

const como = (uid) => env.authenticatedContext(uid).firestore();

test("la app crea su propio evento válido", async () => {
  await assertSucceeds(como("usuarioA").doc("eventos/e1").set(evento("usuarioA")));
});

test("no puede crear eventos a nombre de otro usuario", async () => {
  await assertFails(como("usuarioA").doc("eventos/e1").set(evento("usuarioB")));
});

test("rechaza campos no permitidos, faltantes o inválidos", async () => {
  const db = como("usuarioA");
  await assertFails(db.doc("eventos/e1").set(evento("usuarioA", { nombre: "Ana" })));     // dato extra
  await assertFails(db.doc("eventos/e2").set({ uid: "usuarioA", tipo: "x" }));              // sin ts
  await assertFails(db.doc("eventos/e3").set(evento("usuarioA", { ts: "ayer" })));          // ts no es fecha
  await assertFails(db.doc("eventos/e4").set(evento("usuarioA", { tipo: "x".repeat(41) })));
  const props = Object.fromEntries(Array.from({ length: 11 }, (_, i) => [`k${i}`, i]));
  await assertFails(db.doc("eventos/e5").set(evento("usuarioA", { props })));               // > 10 props
  await assertFails(db.doc("eventos/e6").set(evento("usuarioA", { ts: new Date(Date.now() + 3600e3) }))); // futuro
});

test("sin sesión no se puede escribir ni leer", async () => {
  const anon = env.unauthenticatedContext().firestore();
  await assertFails(anon.doc("eventos/e1").set(evento("usuarioA")));
  await assertFails(anon.doc("eventos/de-b").get());
});

test("nadie puede editar un evento", async () => {
  await assertFails(como("usuarioB").doc("eventos/de-b").update({ tipo: "otro" }));
});

test("cada usuario borra solo lo suyo", async () => {
  await assertFails(como("usuarioA").doc("eventos/de-b").delete());
  await assertSucceeds(como("usuarioB").doc("eventos/de-b").delete());
});

test("un usuario lee solo sus eventos (consulta filtrada por su uid)", async () => {
  await assertSucceeds(como("usuarioB").collection("eventos").where("uid", "==", "usuarioB").get());
  await assertFails(como("usuarioA").doc("eventos/de-b").get());
  await assertFails(como("usuarioA").collection("eventos").get());
});

test("el administrador lee todos los eventos; otros no", async () => {
  await assertSucceeds(como("admin1").collection("eventos").get());
  await assertFails(como("usuarioA").collection("eventos").get());
});

test("nadie se puede agregar como administrador", async () => {
  await assertFails(como("usuarioA").doc("administradores/usuarioA").set({ email: "x" }));
  await assertSucceeds(como("admin1").doc("administradores/admin1").get());
  await assertFails(como("usuarioA").doc("administradores/admin1").get());
});

test("otras colecciones están cerradas", async () => {
  await assertFails(como("usuarioA").doc("cualquier/cosa").set({ a: 1 }));
});
