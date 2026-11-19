import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";
import { promisify } from "node:util";

// Executado no navegador com a página e os módulos de produção.
function ensaiar() {
  const el = id => document.getElementById(id);
  const paginas = [];
  const envios = [];
  let responder;
  const pedido = id => ({ id, estado: "INICIAL", custoTotalProjetado: "1.00", itens: [] });
  window.fetch = async (url, opcoes) => {
    const { pathname, searchParams } = new URL(url, location.href);
    let dados;
    let status = 200;
    if (opcoes.method === "POST") {
      envios.push(opcoes.body);
      status = await new Promise(resolve => { responder = resolve; });
      dados = status === 503 ? { erro: { codigo: "CONFIRMACAO_CRIACAO_DESCONHECIDA" } } : { resultado: "CRIADA" };
    } else if (pathname.endsWith("/sessao")) {
      dados = { perfil: "DESENVOLVEDOR", usuarioId: "autor", departamentoId: "engenharia" };
    } else if (pathname.endsWith("/csrf")) {
      dados = { cabecalho: "X-CSRF-TOKEN", token: "teste" };
    } else if (pathname.endsWith("/recursos")) {
      dados = { recursos: [{ id: "recurso", nome: "Recurso local", custoMensal: "1.00" }] };
    } else if (pathname.endsWith("/requisicoes")) {
      const pagina = Number(searchParams.get("apos") ?? 0);
      paginas.push(pagina);
      dados = { requisicoes: [pedido("pedido-" + pagina)], proximoCursor: String(pagina + 1) };
    } else {
      dados = { requisicao: pedido(pathname.split("/").pop()) };
    }
    return Response.json(dados, { status });
  };
  async function aguardar(condicao, descricao) {
    for (let i = 0; i < 100; i++) {
      if (condicao()) return;
      await new Promise(resolve => setTimeout(resolve, 10));
    }
    throw Error(descricao);
  }
  window.addEventListener("load", async () => {
    const verificar = (condicao, descricao) => { if (!condicao) throw Error(descricao); };
    try {
      await aguardar(() => el("requisicoes").rows.length === 1, "primeira página");
      location.hash = "pedidos";
      el("mais-pedidos").click();
      await aguardar(() => el("requisicoes").rows.length === 2, "segunda página");
      verificar(el("resumo-pedidos").textContent === "2" && el("nota-pedidos").textContent.includes("Parcial"), "contagem parcial");
      location.hash = "nova";
      verificar(el("resumo-catalogo").textContent === "1", "contagem do catálogo");
      const recurso = document.querySelector("#criar select");
      recurso.value = "recurso";
      recurso.dispatchEvent(new Event("change", { bubbles: true }));
      const quantidade = document.querySelector("#itens-novos input");
      quantidade.value = "3";
      quantidade.dispatchEvent(new Event("input", { bubbles: true }));
      verificar(el("total-estimado").textContent.includes("3,00"), "estimativa por quantidade");
      el("adicionar-item").click();
      verificar(el("total-estimado").textContent === "Complete os itens", "total incompleto");
      document.querySelector("#itens-novos .item-row:last-child button").click();
      verificar(el("total-estimado").textContent.includes("3,00") && envios.length === 0, "remoção sem envio");
      el("criar").requestSubmit();
      await aguardar(() => envios.length === 1, "primeiro envio");
      location.hash = "catalogo";
      await aguardar(() => !el("catalogo").hidden, "catálogo aberto");
      const incerta = location.search === "?incerta";
      responder(incerta ? 503 : 201);
      const tipo = () => el("retorno-operacao").dataset.tipo;
      await aguardar(() => tipo() === (incerta ? "pendente" : "sucesso"), "recibo");
      verificar(!el("catalogo").hidden, "área alterada");
      if (incerta) {
        verificar(el("consultar-operacao").textContent !== "Ver requisição", "rótulo definitivo durante a pendência");
        verificar(el("mensagem-operacao").textContent.includes("Confirmação desconhecida"), "aviso de confirmação");
        verificar(el("consultar-operacao").textContent === "Consultar situação atual", "rótulo de consulta");
        el("consultar-operacao").click();
        await aguardar(() => el("id-detalhe").textContent === JSON.parse(envios[0]).requisicaoId, "consulta por UUID");
        verificar(tipo() === "pendente" && !el("reconciliar").hidden && envios.length === 1, "GET encerrou a pendência ou repetiu o envio");
        el("fechar-detalhe").click();
        el("reconciliar").click();
        await aguardar(() => envios.length === 2, "reenvio explícito");
        verificar(envios[0] === envios[1], "corpo ou UUID alterado");
        responder(200);
        await aguardar(() => tipo() === "sucesso", "confirmação do reenvio");
      }
      verificar(el("consultar-operacao").textContent === "Ver requisição", "rótulo após confirmação");
      location.hash = "pedidos";
      verificar(paginas.join() === "0,1" && el("requisicoes").rows.length === 2, "paginação reiniciada");
      verificar(!el("atualizacao-pedidos").hidden, "aviso de atualização");
      el("mais-pedidos").click();
      await aguardar(() => el("requisicoes").rows.length === 3, "cursor da terceira página");
      el("atualizar-pedidos").click();
      await aguardar(() => el("requisicoes").rows.length === 1, "atualização explícita");
      verificar(paginas.join() === "0,1,2,0" && el("atualizacao-pedidos").hidden, "sequência de páginas");
      document.body.dataset.teste = "passou";
    } catch (erro) {
      document.body.dataset.teste = erro.message;
    }
  });
}

const base = new URL("../src/main/resources/", import.meta.url);
for (const cenario of ["confirmada", "incerta"]) {
  test("resposta " + cenario + " enquanto o catálogo está aberto", async () => {
    const html = await readFile(new URL("portal/desenvolvedor.html", base), "utf8");
    const server = createServer(async (req, res) => {
      const rota = new URL(req.url, "http://localhost").pathname;
      if (rota === "/") {
        res.setHeader("Content-Type", "text/html; charset=utf-8");
        res.end(html.replace("<head>", "<head><script>(" + ensaiar.toString() + ")()</script>"));
      } else if (/^\/portal-assets\/[a-z-]+\.(js|svg|css)$/.test(rota)) {
        const tipo = { js: "text/javascript", css: "text/css", svg: "image/svg+xml" };
        res.setHeader("Content-Type", tipo[rota.split(".").pop()]);
        res.end(await readFile(new URL("static" + rota, base)));
      } else {
        res.writeHead(404).end();
      }
    });
    const perfil = await mkdtemp(path.join(tmpdir(), "finops-navegacao-"));
    try {
      await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
      const navegador = process.env.FINOPS_BROWSER ?? "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
      const { stdout } = await promisify(execFile)(navegador, ["--headless", "--disable-gpu", "--no-first-run",
        "--disable-background-networking", "--user-data-dir=" + perfil, "--virtual-time-budget=6000", "--dump-dom",
        "http://127.0.0.1:" + server.address().port + "/?" + cenario], { timeout: 30000 });
      assert.equal(/<body[^>]* data-teste="([^"]*)"/.exec(stdout)?.[1], "passou");
    } finally {
      server.close();
      await rm(perfil, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    }
  });
}
