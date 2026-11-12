import assert from "node:assert/strict";
import { test } from "node:test";
import {
  criarControleDeOperacoes, decimalMonetario, prepararOperacao
} from "../src/main/resources/static/portal-assets/operacoes.js";

function operacao(entrada = { operacaoId: "mesmo-id", itens: [{ quantidade: 2 }, { quantidade: 1 }] }) {
  return prepararOperacao("/requisicoes/mesmo-id/revisao-tecnica", entrada,
    { id: "mesmo-id", usuarioId: "autor", recibo: true });
}

test("bloqueia duplo clique e outras mutações antes de terminar o envio", async () => {
  let concluir;
  let tentativas = 0;
  const controle = criarControleDeOperacoes(() => {
    tentativas++;
    return new Promise(resolve => { concluir = resolve; });
  });
  const original = operacao();
  const envio = controle.executar(original);
  assert.equal(controle.enviando, true);
  assert.equal(await controle.executar(original), null);
  assert.equal(await controle.executar(operacao()), null);
  concluir({ status: 200, corpo: { resultado: "APROVADA_TECNICAMENTE" } });
  assert.equal((await envio).sucesso, true);
  assert.equal(tentativas, 1);
});

test("mantém URL, usuário e corpo serializado após perder a resposta", async () => {
  const chamadas = [];
  const controle = criarControleDeOperacoes(async (pedido, repetir) => {
    chamadas.push({ pedido, repetir });
    if (chamadas.length === 1) throw new TypeError("rede indisponível");
    return { status: 200, corpo: { resultado: "LIBERADA_COM_RESERVA" } };
  });
  const entrada = { operacaoId: "mesmo-id", justificativa: "Original" };
  const original = operacao(entrada);
  assert.equal((await controle.executar(original)).pendente, true);
  entrada.justificativa = "Editada após o envio";
  assert.equal(chamadas.length, 1);
  assert.equal(await controle.executar(operacao()), null);
  const confirmado = await controle.executar(controle.pendente, true);
  assert.equal(confirmado.repeticao, true);
  assert.equal(confirmado.recuperada, true);
  assert.equal(chamadas[1].pedido, chamadas[0].pedido);
  assert.equal(chamadas[1].repetir, true);
  assert.equal(JSON.parse(chamadas[1].pedido.corpo).justificativa, "Original");
  assert.equal(chamadas[1].pedido.usuarioId, "autor");
  assert.equal(controle.pendente, null);
});

test("retém o comando após 503 e mudança de usuário ou falta de acesso", async () => {
  const respostas = [
    { status: 503, corpo: { erro: { codigo: "CONFIRMACAO_INTERVENCAO_DESCONHECIDA" } } },
    { status: 403, corpo: { erro: { codigo: "USUARIO_DIFERENTE" } } },
    { status: 403, corpo: { erro: { codigo: "ACESSO_NEGADO" } } }
  ];
  const controle = criarControleDeOperacoes(async () => respostas.shift());
  const original = operacao();
  for (let i = 0; i < 3; i++) {
    assert.equal((await controle.executar(original)).pendente, true);
    assert.equal(controle.pendente, original);
  }
});

test("409 de negócio com recibo permite replay, mas colisão de UUID não", async () => {
  const respostas = [
    { status: 409, corpo: { erro: { codigo: "TETO_ABAIXO_DO_COMPROMETIDO", operacaoId: "mesmo-id" } } },
    { status: 409, corpo: { erro: { codigo: "OPERACAO_ID_EM_CONFLITO" } } }
  ];
  const controle = criarControleDeOperacoes(async () => respostas.shift());
  const original = operacao();
  assert.equal((await controle.executar(original)).sucesso, false);
  assert.equal(controle.ultima, original);
  assert.equal(controle.pendente, null);
  await controle.executar(operacao());
  assert.equal(controle.ultima, original);
});

test("valor monetário é normalizado como texto e validação não envia corpo", () => {
  assert.equal(decimalMonetario(" 0002500,00 "), "2500.00");
  assert.equal(decimalMonetario("99999999999999999.99"), "99999999999999999.99");
  for (const invalido of ["2.500,00", "-1,00", "1,001", "100000000000000000.00", "2e3", "R$ 1,00"]) {
    assert.equal(decimalMonetario(invalido), null);
  }
  assert.equal(prepararOperacao("/validacao", undefined, {}).corpo, undefined);
});
