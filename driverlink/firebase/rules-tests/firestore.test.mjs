// Pruebas de seguridad: ejecutar con `npm run test:emulator` desde esta carpeta.
import { test, before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDoc, collection, addDoc, serverTimestamp, Timestamp } from "firebase/firestore";

let env;
const profile = (overrides = {}) => ({
  firstName: "Ana", lastName: "Muñoz", email: "ana@driverlink.cl", phone: "+56912345678", photoUrl: null,
  city: "Santiago", commune: "Maipú", platforms: ["UBER"], verificationStatus: "UNVERIFIED",
  accountStatus: "ACTIVE", subscriptionStatus: "FREE", role: "DRIVER",
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
});
const as = (uid, claims = {}) => env.authenticatedContext(uid, { email: `${uid}@driverlink.cl`, ...claims }).firestore();
const admin = async (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-driverlink",
    firestore: { rules: readFileSync("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

test("el usuario crea su perfil solo como conductor gratuito y sin verificar", async () => {
  const db = as("ana");
  await assertFails(setDoc(doc(db, "users/ana"), profile({ email: "ana@driverlink.cl", verificationStatus: "VERIFIED" })));
  await assertFails(setDoc(doc(db, "users/ana"), profile({ email: "ana@driverlink.cl", role: "ADMIN" })));
  await assertFails(setDoc(doc(db, "users/ana"), profile({ email: "ana@driverlink.cl", subscriptionStatus: "PRO" })));
  await assertFails(setDoc(doc(as("otro"), "users/ana"), profile()));
  await assertSucceeds(setDoc(doc(db, "users/ana"), profile({ email: "ana@driverlink.cl" })));
});

test("el usuario no puede verificarse, darse Premium ni cambiar su rol", async () => {
  await admin((db) => setDoc(doc(db, "users/ana"), profile()));
  const db = as("ana");
  await assertSucceeds(updateDoc(doc(db, "users/ana"), { city: "Valparaíso", updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, "users/ana"), { verificationStatus: "VERIFIED" }));
  await assertFails(updateDoc(doc(db, "users/ana"), { subscriptionStatus: "PRO" }));
  await assertFails(updateDoc(doc(db, "users/ana"), { role: "ADMIN" }));
  await assertFails(setDoc(doc(db, "subscriptions/ana"), { status: "PRO" }));
});

test("el perfil privado no es legible por otros conductores", async () => {
  await admin((db) => setDoc(doc(db, "users/ana"), profile()));
  await assertFails(getDoc(doc(as("pedro"), "users/ana")));
  await assertSucceeds(getDoc(doc(as("mod", { role: "MODERATOR" }), "users/ana")));
});

test("mensajes: sin suplantación y borrado solo del autor", async () => {
  await admin((db) => setDoc(doc(db, "channels/santiago"), { name: "Santiago", active: true, mode: "TEXT", order: 0 }));
  const db = as("ana");
  const msg = { senderId: "ana", senderName: "Ana Muñoz", senderPhotoUrl: null, type: "TEXT", text: "Hola", createdAt: serverTimestamp() };
  await assertFails(addDoc(collection(db, "channels/santiago/messages"), { ...msg, senderId: "pedro" }));
  await assertFails(addDoc(collection(db, "channels/santiago/messages"), { ...msg, text: "" }));
  const ref = await assertSucceeds(addDoc(collection(db, "channels/santiago/messages"), msg));
  await assertFails(updateDoc(doc(as("pedro"), ref.path), { deletedAt: serverTimestamp(), text: null }));
  await assertSucceeds(updateDoc(doc(db, ref.path), { deletedAt: serverTimestamp(), text: null }));
  await assertFails(addDoc(collection(as("susp", { suspended: true }), "channels/santiago/messages"), { ...msg, senderId: "susp" }));
});

test("alertas: contadores en cero y un solo voto por usuario", async () => {
  const db = as("ana");
  const alert = {
    creatorId: "ana", creatorName: "Ana", category: "TRAFFIC", description: "Taco", latitude: -33.4, longitude: -70.6,
    city: "Santiago", commune: "Providencia", status: "ACTIVE", confirmationsCount: 0, endedCount: 0, incorrectCount: 0,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), expiresAt: Timestamp.fromMillis(Date.now() + 3600_000),
  };
  await assertFails(setDoc(doc(db, "alerts/a0"), { ...alert, confirmationsCount: 50 }));
  await assertFails(setDoc(doc(db, "alerts/a0"), { ...alert, expiresAt: Timestamp.fromMillis(Date.now() + 3 * 86400_000) }));
  await assertSucceeds(setDoc(doc(db, "alerts/a1"), alert));
  await assertFails(updateDoc(doc(db, "alerts/a1"), { confirmationsCount: 10 }));
  // El creador no vota su propia alerta.
  await assertFails(setDoc(doc(db, "alerts/a1/confirmations/ana"), { vote: "CONFIRM", userId: "ana", createdAt: serverTimestamp() }));
  const pedro = as("pedro");
  await assertFails(setDoc(doc(pedro, "alerts/a1/confirmations/otro"), { vote: "CONFIRM", userId: "pedro", createdAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(pedro, "alerts/a1/confirmations/pedro"), { vote: "CONFIRM", userId: "pedro", createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(pedro, "alerts/a1/confirmations/pedro"), { vote: "ENDED", userId: "pedro", createdAt: serverTimestamp() }));
});

test("SOS: solo servidor crea; el dueño solo actualiza su ubicación", async () => {
  await assertFails(setDoc(doc(as("ana"), "sosEvents/s1"), { userId: "ana", status: "CREATED" }));
  await admin((db) => setDoc(doc(db, "sosEvents/s1"), { userId: "ana", status: "CREATED", latitude: null, longitude: null }));
  const db = as("ana");
  await assertSucceeds(updateDoc(doc(db, "sosEvents/s1"), { latitude: -33.4, longitude: -70.6, locationUpdatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, "sosEvents/s1"), { status: "RESOLVED" }));
  await assertFails(getDoc(doc(as("pedro"), "sosEvents/s1")));
  await assertSucceeds(getDoc(doc(as("op", { role: "SOS_OPERATOR" }), "sosEvents/s1")));
});

test("contactos de emergencia requieren Premium vigente validado en servidor", async () => {
  const contact = { name: "Carolina", relationship: "Hermana", phone: "+56987654321", updatedAt: serverTimestamp() };
  await admin((db) => setDoc(doc(db, "subscriptions/ana"), { status: "FREE" }));
  await assertFails(setDoc(doc(as("ana"), "users/ana/emergencyContacts/c1"), contact));
  await admin((db) => setDoc(doc(db, "subscriptions/ana"), { status: "TRIAL", expiresAt: Timestamp.fromMillis(Date.now() - 1000) }));
  await assertFails(setDoc(doc(as("ana"), "users/ana/emergencyContacts/c1"), contact));
  await admin((db) => setDoc(doc(db, "subscriptions/ana"), { status: "TRIAL", expiresAt: Timestamp.fromMillis(Date.now() + 86400_000) }));
  await assertSucceeds(setDoc(doc(as("ana"), "users/ana/emergencyContacts/c1"), contact));
});

test("empresa: canales privados solo para miembros ACTIVE", async () => {
  await admin(async (db) => {
    await setDoc(doc(db, "companies/c1"), { tradeName: "Demo" });
    await setDoc(doc(db, "companies/c1/members/dueno"), { userId: "dueno", role: "OWNER", status: "ACTIVE" });
    await setDoc(doc(db, "companies/c1/members/pendiente"), { userId: "pendiente", role: "DRIVER", status: "PENDING" });
    await setDoc(doc(db, "companies/c1/channels/general"), { name: "General", active: true, mode: "TEXT", order: 0 });
  });
  const msg = (uid) => ({ senderId: uid, senderName: uid, senderPhotoUrl: null, type: "TEXT", text: "Hola", createdAt: serverTimestamp() });
  await assertFails(getDoc(doc(as("extrano"), "companies/c1/channels/general")));
  await assertFails(getDoc(doc(as("pendiente"), "companies/c1/channels/general")));
  await assertFails(addDoc(collection(as("pendiente"), "companies/c1/channels/general/messages"), msg("pendiente")));
  await assertSucceeds(addDoc(collection(as("dueno"), "companies/c1/channels/general/messages"), msg("dueno")));
  // Conocer el código no da acceso: el cliente no puede crear su propia membresía.
  await assertFails(setDoc(doc(as("extrano"), "companies/c1/members/extrano"), { userId: "extrano", role: "DRIVER", status: "ACTIVE" }));
  await assertFails(getDoc(doc(as("pendiente"), "companies/c1/private/invite")));
});

test("jornada: el conductor solo registra ubicación en su jornada abierta", async () => {
  await admin(async (db) => {
    await setDoc(doc(db, "companies/c1/members/juan"), { userId: "juan", role: "DRIVER", status: "ACTIVE" });
  });
  const db = as("juan");
  await assertSucceeds(setDoc(doc(db, "companies/c1/workSessions/w1"),
    { userId: "juan", vehicleId: null, status: "ON_DUTY", startedAt: serverTimestamp(), endedAt: null }));
  await assertSucceeds(setDoc(doc(db, "companies/c1/workSessions/w1/locationUpdates/l1"),
    { latitude: -33.4, longitude: -70.6, createdAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(db, "companies/c1/workSessions/w1"), { status: "OFF_DUTY", endedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(db, "companies/c1/workSessions/w1/locationUpdates/l2"),
    { latitude: -33.4, longitude: -70.6, createdAt: serverTimestamp() }));
});

test("reportes: solo moderación los lee", async () => {
  const report = { reporterId: "ana", targetType: "MESSAGE", targetId: "m1", targetOwnerId: "pedro", reason: "SPAM",
    details: "", contextPath: "channels/santiago", status: "OPEN", createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(as("ana"), "reports/r1"), report));
  await assertFails(getDoc(doc(as("ana"), "reports/r1")));
  await assertSucceeds(getDoc(doc(as("mod", { role: "MODERATOR" }), "reports/r1")));
});
