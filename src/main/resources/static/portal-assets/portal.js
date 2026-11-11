const elemento = id => document.getElementById(id);
const raiz = "/api/v1";
const perfil = document.body.dataset.perfil;
const estado = {
  INICIAL: "Inicial",
  REVISAO_PENDENTE: "Em revisão técnica",
  REJEITADO: "Rejeitada",
  AGUARDANDO_AJUSTE_ORCAMENTARIO: "Aguarda ajuste orçamentário",
  AGUARDANDO_AUTORIZACAO_FINANCEIRA: "Aguarda autorização da reserva",
  AGUARDANDO_LIBERACAO_FINANCEIRA: "Aguarda liberação financeira",
  PROVISIONAMENTO_LIBERADO: "Provisionamento autorizado"
};
const motivo = { RESERVA: "Reserva orçamentária", SALDO_INSUFICIENTE: "Saldo insuficiente" };

async function consultar(caminho, signal) {
  const resposta = await fetch(raiz + caminho, { signal, headers: { Accept: "application/json" } });
  const corpo = await resposta.json();
  if (!resposta.ok) {
    const erro = new Error(corpo.erro?.mensagem ?? "Não foi possível consultar os dados.");
    erro.codigo = corpo.erro?.codigo;
    throw erro;
  }
  return corpo;
}

function mostrarFalha(erro) {
  if (erro.codigo === "SESSAO_NECESSARIA") {
    window.location.replace("/login?expirada");
    return;
  }
  const aviso = elemento("aviso");
  aviso.textContent = erro.codigo === "ACESSO_NEGADO"
    ? "Acesso negado. Abra o portal novamente para atualizar as permissões da sessão."
    : "Não foi possível consultar os dados. Tente atualizar a página.";
  aviso.setAttribute("data-erro", "");
  aviso.hidden = false;
  if (erro.codigo === "ACESSO_NEGADO") {
    document.querySelectorAll("main > section").forEach(secao => { secao.hidden = true; });
  }
}

function reais(valor) {
  const [inteiro, decimal] = valor.split(".");
  return "R$\u00a0" + inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, ".") + "," + decimal;
}

function texto(tag, valor, classe) {
  const no = document.createElement(tag);
  no.textContent = valor;
  if (classe) no.className = classe;
  return no;
}

function mostrarLinhas(tabela, linhas, acrescentar = false) {
  if (acrescentar) tabela.append(...linhas);
  else tabela.replaceChildren(...linhas);
  tabela.closest(".table-wrap").hidden = tabela.rows.length === 0;
}

async function catalogo() {
  const aviso = elemento("situacao-catalogo");
  try {
    const { recursos } = await consultar("/catalogo/recursos");
    const linhas = recursos.map(recurso => {
      const linha = document.createElement("tr");
      linha.append(texto("td", recurso.nome), texto("td", reais(recurso.custoMensal)));
      return linha;
    });
    mostrarLinhas(elemento("recursos"), linhas);
    aviso.hidden = linhas.length > 0;
    aviso.textContent = "O catálogo está vazio.";
  } catch (erro) {
    aviso.textContent = "Catálogo indisponível.";
    mostrarFalha(erro);
  }
}

const tabela = elemento("requisicoes");
const botaoPedidos = elemento("mais-pedidos");
const avisoPedidos = elemento("situacao-pedidos");
let cursorPedidos = null;
let departamentoAtual = null;
let consultaPedidos;

async function pedidos(acrescentar = false) {
  if (consultaPedidos) consultaPedidos.abort();
  const consulta = new AbortController();
  consultaPedidos = consulta;
  const parametros = new URLSearchParams();
  if (departamentoAtual) parametros.set("departamentoId", departamentoAtual);
  if (acrescentar && cursorPedidos) parametros.set("apos", cursorPedidos);
  if (!acrescentar) {
    cursorPedidos = null;
    mostrarLinhas(tabela, []);
    avisoPedidos.textContent = "Carregando requisições…";
    avisoPedidos.hidden = false;
  }
  botaoPedidos.disabled = true;

  try {
    const { requisicoes, proximoCursor } = await consultar("/requisicoes?" + parametros, consulta.signal);
    if (consulta.signal.aborted) return;
    const linhas = requisicoes.map(pedido => {
      const linha = document.createElement("tr");
      const id = document.createElement("td");
      id.append(texto("code", pedido.id));
      const celulaEstado = document.createElement("td");
      celulaEstado.append(texto("span", estado[pedido.estado] ?? pedido.estado, "badge"));
      linha.append(id, celulaEstado, texto("td", motivo[pedido.motivoRevisao] ?? "—"),
        texto("td", reais(pedido.custoTotalProjetado)));
      return linha;
    });
    mostrarLinhas(tabela, linhas, acrescentar);
    avisoPedidos.textContent = "Nenhuma requisição encontrada.";
    avisoPedidos.hidden = tabela.rows.length > 0;
    cursorPedidos = proximoCursor;
  } catch (erro) {
    if (consulta.signal.aborted) return;
    avisoPedidos.textContent = "Requisições indisponíveis.";
    avisoPedidos.hidden = false;
    mostrarFalha(erro);
  } finally {
    if (!consulta.signal.aborted) {
      botaoPedidos.disabled = false;
      botaoPedidos.hidden = !cursorPedidos;
    }
  }
}

async function departamentos() {
  const seletor = elemento("departamento");
  const botao = elemento("mais-departamentos");
  const aviso = elemento("situacao-departamentos");
  const dados = new Map();
  let cursor;

  function selecionar() {
    const departamento = dados.get(seletor.value);
    departamentoAtual = departamento.id;
    elemento("valores").hidden = false;
    elemento("teto").textContent = reais(departamento.tetoMensal);
    elemento("saldo").textContent = reais(departamento.saldoDisponivel);
    void pedidos();
  }

  async function carregar() {
    botao.disabled = true;
    const parametros = cursor ? "?apos=" + encodeURIComponent(cursor) : "";
    try {
      const pagina = await consultar("/departamentos" + parametros);
      const primeiraPagina = dados.size === 0;
      for (const departamento of pagina.departamentos) {
        dados.set(departamento.id, departamento);
        seletor.add(new Option(departamento.nome, departamento.id));
      }
      cursor = pagina.proximoCursor;
      aviso.textContent = "Nenhum departamento encontrado.";
      aviso.hidden = dados.size > 0;
      elemento("seletor").hidden = dados.size === 0;
      if (primeiraPagina && dados.size > 0) selecionar();
    } catch (erro) {
      aviso.textContent = "Departamentos indisponíveis.";
      aviso.hidden = false;
      mostrarFalha(erro);
    } finally {
      botao.disabled = false;
      botao.hidden = !cursor;
    }
  }

  botao.addEventListener("click", carregar);
  seletor.addEventListener("change", selecionar);
  await carregar();
}

async function iniciar() {
  try {
    const csrf = await consultar("/csrf");
    const logout = elemento("logout");
    const campo = document.createElement("input");
    campo.type = "hidden";
    campo.name = "_csrf";
    campo.value = csrf.token;
    logout.append(campo);
    logout.querySelector("button").disabled = false;
    const sessao = await consultar("/sessao");
    if (sessao.perfil !== perfil) {
      window.location.replace("/portal");
      return;
    }
    botaoPedidos.addEventListener("click", () => pedidos(true));
    if (perfil === "ARQUITETO_CLOUD") await departamentos();
    else await Promise.all([catalogo(), pedidos()]);
  } catch (erro) {
    mostrarFalha(erro);
  }
}

void iniciar();
