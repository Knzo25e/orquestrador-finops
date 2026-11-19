import assert from "node:assert/strict";
import test from "node:test";
import { estimarItem, decimalDeCentavos } from "../src/main/resources/static/portal-assets/estimativa.js";

test("subtotais e total preservam centavos inclusive acima do limite de Number", () => {
  assert.equal(decimalDeCentavos(estimarItem("12.50", 3)), "37.50");
  assert.equal(decimalDeCentavos(estimarItem("12.50", 70) + estimarItem("12.79", 2)), "900.58");
  assert.equal(estimarItem("99999999999999999.99", 2147483647), 9999999999999999999n * 2147483647n);
  assert.equal(decimalDeCentavos(estimarItem("0.00", 1)), "0.00");
});

test("linhas incompletas e quantidades inválidas não compõem um total", () => {
  assert.equal(estimarItem(undefined, 1), null);
  for (const n of [NaN, 0, -1, 0.5, 2147483648]) assert.equal(estimarItem("12.50", n), null);
});
