export function prepararOperacao(caminho, entrada, contexto) {
  return Object.freeze({
    ...contexto,
    caminho,
    corpo: entrada === undefined ? undefined : JSON.stringify(entrada)
  });
}

export function criarControleDeOperacoes(enviar) {
  let enviando = false;
  let pendente = null;
  let ultima = null;

  async function executar(operacao, repetir = false) {
    if (enviando || (pendente && pendente !== operacao)) return null;
    const recuperando = pendente === operacao;
    enviando = true;

    try {
      const resposta = await enviar(operacao, repetir || recuperando);
      const codigo = resposta.corpo?.erro?.codigo;
      const requerIntervencao = ["SESSAO_NECESSARIA", "CSRF_INVALIDO", "USUARIO_DIFERENTE"].includes(codigo);
      if (resposta.status >= 500 || [408, 429].includes(resposta.status) || requerIntervencao || (recuperando && [403, 404].includes(resposta.status))) {
        pendente = operacao;
        return { ...resposta, pendente: true, repeticao: repetir || recuperando };
      }

      pendente = null;
      const sucesso = resposta.status >= 200 && resposta.status < 300;
      if (operacao.recibo && (sucesso || resposta.corpo?.erro?.operacaoId === operacao.id)) ultima = operacao;
      return { ...resposta, sucesso, recuperada: recuperando, repeticao: repetir || recuperando };
    } catch {
      pendente = operacao;
      return { pendente: true, repeticao: repetir || recuperando };
    } finally {
      enviando = false;
    }
  }

  return {
    executar,
    get enviando() { return enviando; },
    get pendente() { return pendente; },
    get ultima() { return ultima; }
  };
}

export function decimalMonetario(texto) {
  const partes = /^(\d{1,17})[,.](\d{2})$/.exec(texto.trim());
  if (!partes) return null;
  return partes[1].replace(/^0+(?=\d)/, "") + "." + partes[2];
}
