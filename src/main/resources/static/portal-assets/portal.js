import { criarControleDeOperacoes, decimalMonetario, prepararOperacao } from "./operacoes.js";

const elemento = id => document.getElementById(id);
const raiz = "/api/v1";
const arquiteto = document.body.dataset.perfil === "ARQUITETO_CLOUD";
const estados = {
  INICIAL: "Inicial",
  REVISAO_PENDENTE: "Em revisão técnica",
  REJEITADO: "Rejeitada",
  AGUARDANDO_AJUSTE_ORCAMENTARIO: "Aguarda ajuste orçamentário",
  AGUARDANDO_AUTORIZACAO_FINANCEIRA: "Aguarda autorização da reserva",
  AGUARDANDO_LIBERACAO_FINANCEIRA: "Aguarda liberação financeira",
  PROVISIONAMENTO_LIBERADO: "Provisionamento autorizado"
};
const motivos = { RESERVA: "Reserva orçamentária", SALDO_INSUFICIENTE: "Saldo insuficiente" };
const decisoes = {
  APROVAR: "Aprovar tecnicamente", REJEITAR: "Rejeitar tecnicamente",
  AUTORIZAR_RESERVA: "Autorizar uso da reserva", NEGAR_RESERVA: "Negar uso da reserva",
  LIBERAR_SEM_RESERVA: "Liberar sem uso da reserva"
};
const resultados = {
  CRIADA: "Requisição salva. A submissão financeira é uma etapa separada.",
  JA_EXISTENTE: "A requisição já estava registrada.",
  LIBERADA: "Provisionamento autorizado.",
  JA_LIBERADA: "O pedido já estava liberado.",
  REVISAO_POR_RESERVA: "Encaminhada para revisão por atingir a reserva.",
  REVISAO_POR_SALDO_INSUFICIENTE: "Encaminhada para revisão por saldo insuficiente.",
  APROVADA_TECNICAMENTE: "Aprovação técnica registrada. A etapa financeira continua separada.",
  REJEITADA: "Rejeição técnica registrada.",
  AGUARDA_AJUSTE: "Saldo insuficiente. O pedido aguarda ajuste orçamentário.",
  AGUARDA_AUTORIZACAO: "O pedido aguarda autorização para uso da reserva.",
  RESERVA_NEGADA: "Uso da reserva negado. A pendência continua.",
  RESERVA_DISPENSADA: "A reserva deixou de ser necessária. Registre uma liberação sem uso da reserva.",
  LIBERADA_COM_RESERVA: "Provisionamento autorizado com uso da reserva.",
  TETO_AJUSTADO: "Teto ajustado. Os compromissos existentes foram preservados.",
  TETO_INALTERADO: "Teto mantido. A decisão foi registrada.",
  TETO_ABAIXO_DO_COMPROMETIDO: "O novo teto é inferior ao valor comprometido.",
  ESTADO_NAO_PERMITIDO: "O estado registrado não permite esta operação."
};
const acoesAuditadas = {
  AJUSTAR_TETO: "Ajuste de teto", REVISAO_TECNICA: "Revisão técnica", LIBERACAO_FINANCEIRA: "Decisão financeira"
};
const resultadosAuditados = {
  APROVADA_TECNICAMENTE: "Aprovada tecnicamente", REJEITADA: "Rejeitada",
  AGUARDA_AJUSTE: "Aguarda ajuste orçamentário", AGUARDA_AUTORIZACAO: "Aguarda autorização da reserva",
  RESERVA_NEGADA: "Reserva negada", RESERVA_DISPENSADA: "Reserva dispensada",
  LIBERADA: "Liberada", LIBERADA_COM_RESERVA: "Liberada com reserva",
  TETO_AJUSTADO: "Teto ajustado", TETO_INALTERADO: "Teto inalterado",
  TETO_ABAIXO_DO_COMPROMETIDO: "Teto abaixo do comprometido", ESTADO_NAO_PERMITIDO: "Estado não permitido"
};
const rotulosAuditoria = {
  tetoAnterior: "Teto anterior", tetoNovo: "Teto posterior", tetoSolicitado: "Teto solicitado",
  tetoMensal: "Teto considerado", variacaoTeto: "Variação do teto", saldoAnterior: "Saldo anterior",
  saldoNovo: "Saldo posterior", custoHistorico: "Custo histórico", comprometido: "Comprometido",
  valorReserva: "Reserva considerada", percentualReserva: "Proporção da reserva",
  estadoAnterior: "Estado anterior", estadoNovo: "Estado posterior", motivoOriginal: "Motivo original da revisão"
};
const monetariosAuditoria = new Set([
  "tetoAnterior", "tetoNovo", "tetoSolicitado", "tetoMensal", "variacaoTeto",
  "saldoAnterior", "saldoNovo", "custoHistorico", "comprometido", "valorReserva"
]);
const recursos = new Map();
const departamentos = new Map();
let sessao;
let csrf;
let departamentoAtual = null;
let pedidoAtual = null;
let consultaPedidos;
let consultaDetalhe;
let consultaAuditoria;
let cursorPedidos = null;
let cursorDepartamentos = null;
let cursorAuditoria = null;
let filtroAuditoria = null;

async function chamar(caminho, opcoes) {
  const resposta = await fetch(raiz + caminho, opcoes);
  return { status: resposta.status, corpo: await resposta.json() };
}

async function consultar(caminho, signal) {
  const prazo = AbortSignal.timeout(15000);
  const cancelar = signal ? AbortSignal.any([signal, prazo]) : prazo;
  const { status, corpo } = await chamar(caminho, { signal: cancelar, headers: { Accept: "application/json" } });
  if (status !== 200) {
    const erro = new Error(corpo.erro?.mensagem ?? "Não foi possível consultar os dados.");
    erro.codigo = corpo.erro?.codigo;
    erro.status = status;
    throw erro;
  }
  return corpo;
}

const operacoes = criarControleDeOperacoes(async (operacao, repetir) => {
  try {
    if (repetir) atualizarCsrf(await consultar("/csrf"));
    const atual = await consultar("/sessao");
    if (atual.usuarioId !== operacao.usuarioId || atual.perfil !== sessao.perfil
        || (!arquiteto && atual.departamentoId !== sessao.departamentoId)) {
      return { status: 403, corpo: { erro: { codigo: "USUARIO_DIFERENTE" } } };
    }
  } catch (erro) {
    if (erro.status) return { status: erro.status, corpo: { erro: { codigo: erro.codigo, mensagem: erro.message } } };
    throw erro;
  }

  const headers = { Accept: "application/json", [csrf.cabecalho]: csrf.token };
  if (operacao.corpo !== undefined) headers["Content-Type"] = "application/json";
  const resposta = await chamar(operacao.caminho, {
    method: "POST", headers, body: operacao.corpo, signal: AbortSignal.timeout(15000)
  });
  const codigo = resposta.corpo?.resultado ?? resposta.corpo?.situacao;
  if (resposta.status < 300 && typeof codigo !== "string") throw new Error("Confirmação ilegível.");
  return resposta;
});

function atualizarCsrf(token) {
  csrf = token;
  const logout = elemento("logout");
  let campo = logout.querySelector("input");
  if (!campo) {
    campo = document.createElement("input");
    campo.type = "hidden";
    campo.name = "_csrf";
    logout.append(campo);
  }
  campo.value = token.token;
  logout.querySelector("button").disabled = false;
}

function mostrarFalha(erro) {
  if (erro.codigo === "SESSAO_NECESSARIA" && !ultimaExibida && !operacoes.pendente && !operacoes.enviando) {
    window.location.replace("/login?expirada");
    return;
  }
  const aviso = elemento("aviso");
  aviso.textContent = erro.codigo === "ACESSO_NEGADO"
    ? "Acesso negado. Abra o portal novamente para atualizar as permissões da sessão."
    : "Não foi possível consultar os dados atuais. Atualize a consulta.";
  if (erro.codigo === "SESSAO_NECESSARIA") elemento("renovar-sessao").hidden = false;
  aviso.setAttribute("data-erro", "");
  aviso.hidden = false;
  if (erro.codigo === "ACESSO_NEGADO") {
    document.querySelectorAll("main > section:not(#retorno-operacao)").forEach(secao => { secao.hidden = true; });
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

function rotuloEstado(codigo) {
  return estados[codigo] ?? codigo ?? "—";
}

function rotuloMotivo(codigo) {
  return motivos[codigo] ?? (codigo || "—");
}

async function catalogo() {
  const aviso = elemento("situacao-catalogo");
  try {
    const pagina = await consultar("/catalogo/recursos");
    const linhas = pagina.recursos.map(recurso => {
      recursos.set(recurso.id, recurso);
      const linha = document.createElement("tr");
      linha.append(texto("td", recurso.nome), texto("td", reais(recurso.custoMensal)));
      return linha;
    });
    mostrarLinhas(elemento("recursos"), linhas);
    aviso.hidden = linhas.length > 0;
    aviso.textContent = "O catálogo está vazio.";
    if (!sessao.departamentoId || !linhas.length) {
      elemento("aviso-nova").textContent = sessao.departamentoId
        ? "Cadastre um recurso no catálogo para criar uma requisição."
        : "Uma conta com vínculo departamental é necessária para criar requisições.";
      return;
    }
    adicionarItem();
    elemento("criar").hidden = false;
  } catch (erro) {
    aviso.textContent = "Catálogo indisponível.";
    mostrarFalha(erro);
  }
}

function adicionarItem() {
  const lista = elemento("itens-novos");
  if (lista.children.length >= 100) return;
  const linha = texto("div", "", "item-row");
  const recurso = texto("label", "Recurso");
  const seletor = document.createElement("select");
  seletor.name = "recurso";
  seletor.required = true;
  seletor.add(new Option("Selecione um recurso", ""));
  for (const item of recursos.values()) seletor.add(new Option(item.nome + " · " + reais(item.custoMensal), item.id));
  recurso.append(seletor);
  const rotulo = texto("label", "Quantidade");
  const quantidade = document.createElement("input");
  Object.assign(quantidade, { type: "number", name: "quantidade", min: 1, max: 2147483647, step: 1, value: "1", required: true });
  rotulo.append(quantidade);
  const remover = texto("button", "Remover", "button secondary");
  remover.type = "button";
  remover.addEventListener("click", () => {
    linha.remove();
    atualizarItens();
  });
  linha.append(recurso, rotulo, remover);
  lista.append(linha);
  atualizarItens();
}

function atualizarItens() {
  const lista = elemento("itens-novos");
  for (const botao of lista.querySelectorAll("button")) botao.disabled = lista.children.length <= 1;
  elemento("adicionar-item").disabled = lista.children.length >= 100;
}

function criar(evento) {
  evento.preventDefault();
  const id = crypto.randomUUID();
  const itens = [...elemento("itens-novos").children].map(linha => ({
    recursoId: linha.querySelector("select").value, quantidade: linha.querySelector("input").valueAsNumber
  }));
  return executar(preparar("/requisicoes", { requisicaoId: id, itens }, id,
    { pedido: id, criacao: true }));
}

async function pedidos(acrescentar = false) {
  consultaPedidos?.abort();
  const consulta = new AbortController();
  consultaPedidos = consulta;
  const tabela = elemento("requisicoes");
  const botao = elemento("mais-pedidos");
  const aviso = elemento("situacao-pedidos");
  const parametros = new URLSearchParams();
  if (departamentoAtual) parametros.set("departamentoId", departamentoAtual);
  if (acrescentar && cursorPedidos) parametros.set("apos", cursorPedidos);
  if (!acrescentar) {
    cursorPedidos = null;
    mostrarLinhas(tabela, []);
    aviso.textContent = "Carregando requisições…";
    aviso.hidden = false;
  }
  botao.disabled = true;
  try {
    const pagina = await consultar("/requisicoes?" + parametros, consulta.signal);
    if (consulta.signal.aborted) return;
    const linhas = pagina.requisicoes.map(pedido => {
      const linha = document.createElement("tr");
      const id = document.createElement("td");
      const abrir = texto("button", pedido.id, "link-button");
      abrir.type = "button";
      abrir.setAttribute("aria-label", "Abrir requisição " + pedido.id);
      abrir.addEventListener("click", () => detalhe(pedido.id, true));
      id.append(abrir);
      const celulaEstado = document.createElement("td");
      celulaEstado.append(texto("span", rotuloEstado(pedido.estado), "badge"));
      linha.append(id, celulaEstado, texto("td", rotuloMotivo(pedido.motivoRevisao)),
        texto("td", reais(pedido.custoTotalProjetado)));
      return linha;
    });
    mostrarLinhas(tabela, linhas, acrescentar);
    aviso.textContent = "Nenhuma requisição encontrada.";
    aviso.hidden = tabela.rows.length > 0;
    cursorPedidos = pagina.proximoCursor;
  } catch (erro) {
    if (consulta.signal.aborted) return;
    aviso.textContent = "Requisições indisponíveis.";
    aviso.hidden = false;
    mostrarFalha(erro);
  } finally {
    if (!consulta.signal.aborted) {
      botao.disabled = false;
      botao.hidden = !cursorPedidos;
    }
  }
}

function fecharDetalhe() {
  consultaDetalhe?.abort();
  pedidoAtual = null;
  elemento("detalhe").hidden = true;
  if (arquiteto) elemento("filtrar-auditoria").hidden = true;
}

async function detalhe(id, focar = false, silencioso = false) {
  consultaDetalhe?.abort();
  const consulta = new AbortController();
  consultaDetalhe = consulta;
  const painel = elemento("detalhe");
  const aviso = elemento("situacao-detalhe");
  const mesmoPedido = pedidoAtual?.id === id;
  if (!silencioso) {
    painel.hidden = false;
    if (!mesmoPedido) elemento("dados-detalhe").hidden = true;
    aviso.hidden = false;
    aviso.textContent = "Carregando requisição…";
  }
  if (!mesmoPedido) pedidoAtual = null;
  elemento(arquiteto ? "intervir" : "submissao").hidden = true;
  if (arquiteto) elemento("filtrar-auditoria").hidden = true;
  try {
    const { requisicao } = await consultar("/requisicoes/" + id, consulta.signal);
    if (consulta.signal.aborted) return;
    pedidoAtual = requisicao;
    painel.hidden = false;
    aviso.hidden = true;
    elemento("dados-detalhe").hidden = false;
    elemento("id-detalhe").textContent = requisicao.id;
    elemento("estado-detalhe").textContent = rotuloEstado(requisicao.estado);
    elemento("motivo-detalhe").textContent = rotuloMotivo(requisicao.motivoRevisao);
    elemento("custo-detalhe").textContent = reais(requisicao.custoTotalProjetado);
    elemento("itens-detalhe").replaceChildren(...requisicao.itens.map(item => {
      const linha = document.createElement("tr");
      linha.append(texto("td", recursos.get(item.recursoId)?.nome ?? item.recursoId),
        texto("td", item.quantidade), texto("td", reais(item.precoUnitario)), texto("td", reais(item.subtotal)));
      return linha;
    }));
    if (arquiteto) {
      prepararIntervencao(requisicao.estado, mesmoPedido);
      elemento("filtrar-auditoria").hidden = false;
    } else {
      elemento("submissao").hidden = requisicao.estado !== "INICIAL";
    }
    if (focar) painel.focus();
  } catch (erro) {
    if (consulta.signal.aborted) return;
    aviso.hidden = false;
    aviso.textContent = erro.status === 404
      ? "Requisição não encontrada ou indisponível para esta conta."
      : "O detalhe atual está indisponível.";
    elemento("dados-detalhe").hidden = true;
    if (silencioso && erro.status === 404) {
      aviso.textContent = "A consulta ainda não localizou a requisição.";
      painel.hidden = false;
      return;
    }
    if (erro.status !== 404) mostrarFalha(erro);
  }
}

async function carregarDepartamentos(acrescentar = false) {
  const seletor = elemento("departamento");
  const botao = elemento("mais-departamentos");
  const aviso = elemento("situacao-departamentos");
  botao.disabled = true;
  try {
    const parametros = cursorDepartamentos && acrescentar ? "?apos=" + encodeURIComponent(cursorDepartamentos) : "";
    const pagina = await consultar("/departamentos" + parametros);
    for (const departamento of pagina.departamentos) {
      if (!departamentos.has(departamento.id)) seletor.add(new Option(departamento.nome, departamento.id));
      departamentos.set(departamento.id, departamento);
    }
    cursorDepartamentos = pagina.proximoCursor;
    aviso.textContent = "Nenhum departamento encontrado.";
    aviso.hidden = departamentos.size > 0;
    elemento("seletor").hidden = departamentos.size === 0;
    if (!departamentoAtual && departamentos.size > 0) selecionarDepartamento();
  } catch (erro) {
    aviso.textContent = "Departamentos indisponíveis.";
    aviso.hidden = false;
    mostrarFalha(erro);
  } finally {
    botao.disabled = false;
    botao.hidden = !cursorDepartamentos;
  }
}

function mostrarOrcamento(departamento) {
  elemento("valores").hidden = false;
  elemento("teto").textContent = reais(departamento.tetoMensal);
  elemento("saldo").textContent = reais(departamento.saldoDisponivel);
  elemento("ajustar").hidden = false;
}

function selecionarDepartamento() {
  departamentoAtual = elemento("departamento").value;
  fecharDetalhe();
  elemento("ajustar").reset();
  filtroAuditoria = null;
  mostrarOrcamento(departamentos.get(departamentoAtual));
  void pedidos();
  void auditoria();
}

async function atualizarOrcamento(id) {
  let cursor = null;
  do {
    const parametros = cursor ? "?apos=" + encodeURIComponent(cursor) : "";
    const pagina = await consultar("/departamentos" + parametros);
    const atual = pagina.departamentos.find(departamento => departamento.id === id);
    if (atual) {
      departamentos.set(id, atual);
      if (departamentoAtual === id) mostrarOrcamento(atual);
      return;
    }
    cursor = pagina.proximoCursor;
  } while (cursor);
}

function prepararIntervencao(estado, mesmoPedido) {
  const tecnica = estado === "REVISAO_PENDENTE";
  const financeira = [
    "AGUARDANDO_AJUSTE_ORCAMENTARIO", "AGUARDANDO_AUTORIZACAO_FINANCEIRA", "AGUARDANDO_LIBERACAO_FINANCEIRA"
  ].includes(estado);
  const formulario = elemento("intervir");
  formulario.hidden = !tecnica && !financeira;
  if (formulario.hidden) return;

  const etapa = tecnica ? "revisao-tecnica" : "liberacao-financeira";
  if (formulario.dataset.etapa !== etapa || !mesmoPedido) formulario.reset();
  formulario.dataset.etapa = etapa;
  elemento("legenda-intervencao").textContent = tecnica ? "Revisão técnica" : "Decisão financeira";
  elemento("orientacao-intervencao").textContent = tecnica
    ? "A aprovação técnica encaminha o pedido à etapa financeira."
    : "O servidor revalida saldo e reserva ao registrar a decisão.";
  const seletor = elemento("decisao");
  const anterior = seletor.value;
  const opcoes = tecnica ? ["APROVAR", "REJEITAR"] : ["AUTORIZAR_RESERVA", "NEGAR_RESERVA", "LIBERAR_SEM_RESERVA"];
  seletor.replaceChildren(new Option("Selecione a decisão", ""), ...opcoes.map(d => new Option(decisoes[d], d)));
  if (mesmoPedido && opcoes.includes(anterior)) seletor.value = anterior;
}

function justificar(formulario) {
  const campo = formulario.elements.justificativa;
  const valor = campo.value.trim();
  campo.setCustomValidity(valor ? "" : "Informe uma justificativa.");
  return campo.reportValidity() ? valor : null;
}

function intervir(evento) {
  evento.preventDefault();
  const formulario = evento.currentTarget;
  const justificativa = justificar(formulario);
  if (!justificativa || !pedidoAtual) return;
  const decisao = formulario.elements.decisao.value;
  const acao = decisoes[decisao];
  const pedido = pedidoAtual.id;
  const orcamento = departamentos.get(departamentoAtual);
  const saldo = formulario.dataset.etapa === "liberacao-financeira"
    ? "\nSaldo consultado: " + reais(orcamento.saldoDisponivel) + " · Teto consultado: " + reais(orcamento.tetoMensal)
    : "";
  if (!window.confirm(acao + "?\nRequisição " + pedido + "\nCusto registrado: "
      + reais(pedidoAtual.custoTotalProjetado) + saldo + "\nJustificativa: " + justificativa)) return;
  const id = crypto.randomUUID();
  return executar(preparar("/requisicoes/" + pedido + "/" + formulario.dataset.etapa,
    { operacaoId: id, decisao, justificativa }, id, { pedido, recibo: true }));
}

function ajustar(evento) {
  evento.preventDefault();
  const formulario = evento.currentTarget;
  const campo = formulario.elements.novoTeto;
  const novoTeto = decimalMonetario(campo.value);
  campo.setCustomValidity(novoTeto === null ? "Informe um valor como 2500,00, com duas casas decimais." : "");
  if (!campo.reportValidity()) return;
  const justificativa = justificar(formulario);
  if (!justificativa || !departamentoAtual) return;
  const alvo = departamentos.get(departamentoAtual);
  if (!window.confirm("Ajustar teto de " + alvo.nome + " para " + reais(novoTeto) + "?\nTeto consultado: "
      + reais(alvo.tetoMensal) + "\nSaldo consultado: " + reais(alvo.saldoDisponivel)
      + "\nJustificativa: " + justificativa)) return;
  const id = crypto.randomUUID();
  return executar(preparar("/departamentos/" + alvo.id + "/ajustes-orcamentarios",
    { operacaoId: id, novoTeto, justificativa }, id, { ajuste: true, recibo: true }));
}

function submeter() {
  if (!pedidoAtual || pedidoAtual.estado !== "INICIAL") return;
  const id = pedidoAtual.id;
  return executar(preparar("/requisicoes/" + id + "/validacao", undefined, id, { pedido: id }));
}

function preparar(caminho, entrada, id, contexto) {
  return prepararOperacao(caminho, entrada,
    { id, usuarioId: sessao.usuarioId, departamento: departamentoAtual, ...contexto });
}

function estadoDosControles() {
  const bloqueado = operacoes.enviando || !!operacoes.pendente;
  document.querySelectorAll("[data-mutacao]").forEach(no => { no.disabled = bloqueado; });
  if (arquiteto) elemento("departamento").disabled = bloqueado;
  elemento("reconciliar").disabled = operacoes.enviando;
  elemento("repetir").disabled = bloqueado;
  elemento("repetir").hidden = bloqueado || !operacoes.ultima || operacoes.ultima !== ultimaExibida;
}

let ultimaExibida;
async function executar(operacao, repetir = false) {
  if (!operacao || operacoes.enviando) return;
  const envio = operacoes.executar(operacao, repetir);
  estadoDosControles();
  if (operacoes.enviando) {
    const secao = elemento("retorno-operacao");
    secao.hidden = false;
    secao.dataset.tipo = "";
    elemento("mensagem-operacao").textContent = "Enviando operação…";
    const alvo = operacao.pedido ?? departamentos.get(operacao.departamento)?.nome;
    elemento("id-operacao").textContent = "Identificador: " + operacao.id
      + (alvo && alvo !== operacao.id ? " · Alvo: " + alvo : "");
  }
  const resposta = await envio;
  estadoDosControles();
  if (!resposta) return;
  ultimaExibida = operacao;
  estadoDosControles();
  const secao = elemento("retorno-operacao");
  secao.hidden = false;
  secao.dataset.tipo = resposta.pendente ? "pendente" : resposta.sucesso ? "sucesso" : "falha";
  const codigo = resposta.corpo?.erro?.codigo;
  const resultado = resposta.corpo?.resultado ?? resposta.corpo?.situacao ?? codigo;
  const aviso = elemento("mensagem-operacao");
  if (resposta.pendente) {
    if (codigo === "SESSAO_NECESSARIA" || codigo === "USUARIO_DIFERENTE") {
      aviso.textContent = "Confirme a sessão da conta original, com o mesmo perfil e vínculo. Se precisar entrar novamente, use outra aba e mantenha esta página aberta.";
    } else if (codigo === "CSRF_INVALIDO") {
      aviso.textContent = "A proteção da sessão precisa ser renovada. Você pode reenviar a mesma operação.";
    } else {
      aviso.textContent = "A confirmação não chegou. Consulte o estado atual ou reenvie a mesma operação. Mantenha esta página aberta.";
    }
  } else {
    const mensagem = resultados[resultado] ?? resposta.corpo?.erro?.mensagem ?? resultado ?? "Operação registrada.";
    const repeticao = resposta.repeticao ? "Resposta da operação registrada. " : "";
    const avisoServidor = resposta.corpo?.avisos?.map(item => item.mensagem).join(" ") ?? "";
    aviso.textContent = (repeticao + mensagem + " " + avisoServidor).trim();
  }
  elemento("reconciliar").hidden = !resposta.pendente;
  elemento("consultar-operacao").hidden = false;
  elemento("renovar-sessao").hidden = !resposta.pendente;
  secao.scrollIntoView({ block: "nearest" });

  if (resposta.sucesso && (!resposta.repeticao || resposta.recuperada)) {
    if (operacao.criacao) {
      elemento("itens-novos").replaceChildren();
      adicionarItem();
    } else if (operacao.ajuste) {
      elemento("ajustar").reset();
    } else if (operacao.recibo && pedidoAtual?.id === operacao.pedido) {
      elemento("intervir").reset();
    }
  }
  if (!resposta.pendente) await consultarOperacao(operacao, resposta.sucesso);
}

async function consultarOperacao(operacao, focar = false) {
  if (!operacao) return;
  const tarefas = [pedidos()];
  if (arquiteto) {
    tarefas.push(atualizarOrcamento(operacao.departamento).catch(mostrarFalha));
    if (departamentoAtual === operacao.departamento) tarefas.push(auditoria());
  }
  if (operacao.pedido && (!arquiteto || operacao.departamento === departamentoAtual)) {
    tarefas.push(detalhe(operacao.pedido, focar, operacao.criacao && !focar));
  }
  await Promise.all(tarefas);
}

async function auditoria(acrescentar = false) {
  consultaAuditoria?.abort();
  const consulta = new AbortController();
  consultaAuditoria = consulta;
  const lista = elemento("eventos");
  const aviso = elemento("situacao-auditoria");
  const botao = elemento("mais-eventos");
  const parametros = new URLSearchParams();
  if (filtroAuditoria) parametros.set("requisicaoId", filtroAuditoria);
  if (acrescentar && cursorAuditoria) parametros.set("apos", cursorAuditoria);
  if (!acrescentar) {
    cursorAuditoria = null;
    lista.replaceChildren();
    aviso.textContent = "Carregando histórico…";
  }
  elemento("limpar-auditoria").hidden = !filtroAuditoria;
  elemento("atualizar-auditoria").hidden = false;
  botao.disabled = true;
  try {
    const pagina = await consultar("/departamentos/" + departamentoAtual + "/intervencoes?" + parametros, consulta.signal);
    if (consulta.signal.aborted) return;
    lista.append(...pagina.intervencoes.map(eventoAuditado));
    cursorAuditoria = pagina.proximoCursor;
    aviso.textContent = filtroAuditoria ? "Histórico da requisição " + filtroAuditoria : "Histórico do departamento";
    if (!lista.children.length) aviso.textContent += " · Nenhum evento encontrado.";
  } catch (erro) {
    if (consulta.signal.aborted) return;
    aviso.textContent = "Histórico indisponível.";
    mostrarFalha(erro);
  } finally {
    if (!consulta.signal.aborted) {
      botao.disabled = false;
      botao.hidden = !cursorAuditoria;
    }
  }
}

function eventoAuditado(evento) {
  const detalhe = document.createElement("details");
  const acao = acoesAuditadas[evento.acao] ?? evento.acao;
  const resultado = resultadosAuditados[evento.resultado] ?? evento.resultado;
  detalhe.append(texto("summary", acao + " · " + resultado));
  const dados = document.createElement("dl");
  const adicionar = (rotulo, valor) => { dados.append(texto("dt", rotulo), texto("dd", valor)); };
  adicionar("Instante (UTC)", evento.instante);
  adicionar("Responsável", evento.responsavelId);
  adicionar("Justificativa", evento.justificativa);
  adicionar("Operação", evento.operacaoId);
  if (evento.requisicaoId) adicionar("Requisição", evento.requisicaoId);
  for (const [chave, rotulo] of Object.entries(rotulosAuditoria)) {
    const valor = evento.dados[chave];
    if (!valor) continue;
    if (monetariosAuditoria.has(chave)) adicionar(rotulo, reais(valor));
    else if (chave.startsWith("estado")) adicionar(rotulo, rotuloEstado(valor));
    else if (chave === "motivoOriginal") adicionar(rotulo, rotuloMotivo(valor));
    else adicionar(rotulo, valor);
  }
  detalhe.append(dados);
  return detalhe;
}

async function iniciar() {
  try {
    atualizarCsrf(await consultar("/csrf"));
    sessao = await consultar("/sessao");
    if (sessao.perfil !== document.body.dataset.perfil) {
      window.location.replace("/portal");
      return;
    }
    elemento("mais-pedidos").addEventListener("click", () => pedidos(true));
    elemento("atualizar-pedidos").addEventListener("click", () => {
      void pedidos();
      if (pedidoAtual) void detalhe(pedidoAtual.id);
    });
    elemento("fechar-detalhe").addEventListener("click", fecharDetalhe);
    elemento("reconciliar").addEventListener("click", () => executar(operacoes.pendente, true));
    elemento("repetir").addEventListener("click", () => {
      if (window.confirm("Repetir a operação registrada com o mesmo identificador e os mesmos dados?")) {
        void executar(operacoes.ultima, true);
      }
    });
    elemento("consultar-operacao").addEventListener("click", () => consultarOperacao(ultimaExibida));
    window.addEventListener("beforeunload", evento => {
      if (operacoes.pendente || operacoes.enviando) {
        evento.preventDefault();
        evento.returnValue = "";
      }
    });
    if (arquiteto) {
      elemento("departamento").addEventListener("change", selecionarDepartamento);
      elemento("mais-departamentos").addEventListener("click", () => carregarDepartamentos(true));
      elemento("intervir").addEventListener("submit", intervir);
      elemento("ajustar").addEventListener("submit", ajustar);
      for (const campo of document.querySelectorAll("textarea, input[name=novoTeto]")) {
        campo.addEventListener("input", () => campo.setCustomValidity(""));
      }
      elemento("mais-eventos").addEventListener("click", () => auditoria(true));
      elemento("atualizar-auditoria").addEventListener("click", () => auditoria());
      elemento("filtrar-auditoria").addEventListener("click", () => {
        filtroAuditoria = pedidoAtual?.id ?? null;
        void auditoria();
      });
      elemento("limpar-auditoria").addEventListener("click", () => {
        filtroAuditoria = null;
        void auditoria();
      });
      await Promise.all([
        carregarDepartamentos(),
        consultar("/catalogo/recursos").then(pagina => {
          for (const recurso of pagina.recursos) recursos.set(recurso.id, recurso);
        }).catch(mostrarFalha)
      ]);
    } else {
      elemento("adicionar-item").addEventListener("click", adicionarItem);
      elemento("criar").addEventListener("submit", criar);
      elemento("submeter").addEventListener("click", submeter);
      await Promise.all([catalogo(), pedidos()]);
    }
  } catch (erro) {
    mostrarFalha(erro);
  }
}

void iniciar();
