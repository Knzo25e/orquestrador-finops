export function centavos(valor) {
  return BigInt(valor.replace(".", ""));
}

export function distribuicao(tetoMensal, saldoDisponivel) {
  const teto = centavos(tetoMensal);
  const comprometido = teto - centavos(saldoDisponivel);
  const percentual = teto === 0n ? null : Number((comprometido * 10000n + teto / 2n) / teto) / 100;
  const decimal = comprometido.toString().padStart(3, "0");
  return { comprometido: decimal.slice(0, -2) + "." + decimal.slice(-2), percentual };
}
