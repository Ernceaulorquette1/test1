import { test, before, after } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { ref, uploadBytes, getBytes } from "firebase/storage";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-driverlink",
    storage: { rules: readFileSync("../storage.rules", "utf8"), host: "127.0.0.1", port: 9199 },
  });
});
after(async () => env.cleanup());

const bytes = new Uint8Array([1, 2, 3]);

test("evidencia de verificación es privada", async () => {
  const owner = env.authenticatedContext("ana").storage();
  await assertSucceeds(uploadBytes(ref(owner, "verification/ana/doc.jpg"), bytes, { contentType: "image/jpeg" }));
  await assertFails(uploadBytes(ref(env.authenticatedContext("pedro").storage(), "verification/ana/x.jpg"), bytes, { contentType: "image/jpeg" }));
  await assertFails(getBytes(ref(env.authenticatedContext("pedro").storage(), "verification/ana/doc.jpg")));
  await assertFails(getBytes(ref(owner, "verification/ana/doc.jpg")));
  await assertSucceeds(getBytes(ref(env.authenticatedContext("mod", { role: "MODERATOR" }).storage(), "verification/ana/doc.jpg")));
});

test("audios de radio: solo el autor sube y con tipo audio", async () => {
  const ana = env.authenticatedContext("ana").storage();
  await assertSucceeds(uploadBytes(ref(ana, "audio/community/santiago/ana/m1.m4a"), bytes, { contentType: "audio/mp4" }));
  await assertFails(uploadBytes(ref(ana, "audio/community/santiago/pedro/m2.m4a"), bytes, { contentType: "audio/mp4" }));
  await assertFails(uploadBytes(ref(ana, "audio/community/santiago/ana/m3.exe"), bytes, { contentType: "application/octet-stream" }));
  await assertFails(getBytes(ref(env.unauthenticatedContext().storage(), "audio/community/santiago/ana/m1.m4a")));
});
