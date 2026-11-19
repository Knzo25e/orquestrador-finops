export function estimarItem(preco, quantidade) {
  if (!preco || !Number.isInteger(quantidade) || quantidade < 1 || quantidade > 2147483647) return null;
  return BigInt(preco.replace(".", "")) * BigInt(quantidade);
}

export function decimalDeCentavos(centavos) {
  const digitos = centavos.toString().padStart(3, "0");
  return digitos.slice(0, -2) + "." + digitos.slice(-2);
}
