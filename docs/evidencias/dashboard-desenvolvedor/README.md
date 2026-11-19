# Prévia de custo do Desenvolvedor

Ensaio de 19/11/2026 com Edge, JAR via HTTPS, dois perfis e MySQL 8.0.44 descartável. [Visão geral](visao-desenvolvedor.png), [formulário com dois itens](previa-desenvolvedor.png) e [janela de 1024 × 768](previa-projetor.png).

1. Em uma instância exclusiva de teste, carregar o catálogo acadêmico e entrar como Desenvolvedor. Na visão geral, conferir os nove recursos e a quantidade de pedidos carregados. Quando houver cursor, a contagem deve indicar que é parcial.
2. Após carregar o catálogo, desconectar a rede do navegador e abrir Nova requisição. Escolher três unidades do recurso de R$ 12,50, adicionar uma segunda linha e conferir a indicação de item incompleto. Selecionar duas unidades de S3 a R$ 12,79. Os subtotais são R$ 37,50 e R$ 25,58 e o total é R$ 63,08.
3. Navegar para a visão geral e voltar. O formulário e a estimativa persistem. Remover a segunda linha devolve R$ 37,50. Durante essas ações o ensaio registrou zero POST de negócio. Duas leituras do MySQL antes e depois confirmaram zero requisições, itens e eventos, teto e saldo de R$ 1.000,00.
4. Reconectar a rede para o fluxo persistente. Um pedido de 70 unidades do recurso original e duas unidades de S3 resultou em R$ 900,58 no detalhe do servidor. A resposta de criação perdida foi recuperada com o mesmo corpo e UUID. A regressão do fluxo manual também completou os dois perfis.

Regressão Java com MySQL isolado: 100 aprovados. Regressão JavaScript, executada com o comando abaixo: nove aprovados, incluindo os cenários no Edge, paginação parcial, linha incompleta e quantidade máxima. Conferidos 1366 × 768, 1024 × 768 e layout estreito. O projetor físico depende do ensaio acadêmico.

```powershell
node --test scripts/testar-*.mjs
```
