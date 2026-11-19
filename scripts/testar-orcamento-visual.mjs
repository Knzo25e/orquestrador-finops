import test from "node:test";
import assert from "node:assert/strict";
import {distribuicao} from "../src/main/resources/static/portal-assets/orcamento-visual.js";

test("fotografias válidas do orçamento, teto zero e arredondamento percentual", () => {
  for (const [teto, saldo, comprometido, percentual] of [
    ["0.00","0.00","0.00",null], ["1000.00","1000.00","0.00",0],
    ["1000.00","0.00","1000.00",100], ["3.00","2.00","1.00",33.33],
    ["99999999999999999.99","99999999999999999.98","0.01",0],
    ["1000.00","99.42","900.58",90.06], ["2500.00","1599.42","900.58",36.02]
  ]) assert.deepEqual(distribuicao(teto,saldo), {comprometido,percentual});
});
