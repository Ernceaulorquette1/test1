import { test } from "node:test";
import assert from "node:assert/strict";
import { generateInviteCode, INVITE_CODE, isValidRut, normalizeRut, slug } from "./validation";

test("RUT con dígito verificador", () => {
  assert.equal(isValidRut("11.111.111-1"), true);
  assert.equal(isValidRut("12.345.678-5"), true);
  assert.equal(isValidRut("10.000.013-K"), true);
  assert.equal(isValidRut("12.345.678-9"), false);
  assert.equal(normalizeRut("12.345.678-5"), "12345678-5");
});

test("códigos de invitación con formato DL-ABC-1234", () => {
  for (let i = 0; i < 100; i++) assert.match(generateInviteCode(), INVITE_CODE);
});

test("slug de ciudades para temas FCM", () => {
  assert.equal(slug("Viña del Mar"), "vina_del_mar");
  assert.equal(slug("Concepción"), "concepcion");
});
