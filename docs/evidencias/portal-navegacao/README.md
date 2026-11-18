# Ensaio da navegação por perfil

Ensaio de 17/11/2026 em Edge, com JAR via HTTPS, usuários fictícios e MySQL 8.0.44 descartável. Usar a configuração de testes do guia do projeto. Reservar uma instância própria para o ensaio visual com nove recursos, dois perfis e dois departamentos. Executar a regressão Java separadamente em um finops_test exclusivo.

## Roteiro para repetir

1. Como Desenvolvedor, abrir o catálogo pelo teclado, conferir os preços e iniciar um pedido. Selecionar dois itens, alternar de área e voltar. Conferir as seleções e quantidades antes de criar.
2. Acionar a criação duas vezes rapidamente. Conferir um único pedido. Em ensaio com perda da resposta posterior à criação, trocar de área e recuperar a operação pendente. Comparar UUID e corpo antes e depois, e conferir um único efeito.
3. Abrir o detalhe de um pedido, submeter à validação e voltar à lista. Conferir o estado retornado e a navegação pelo histórico do navegador.
4. Como Arquiteto, iniciar um ajuste, trocar de departamento e cancelar a confirmação. Conferir alvo e rascunho. Testar um ajuste abaixo do comprometido e depois um ajuste válido.
5. Revisar pedidos, aprovar um e rejeitar outro com justificativa. Negar o uso da reserva e depois executar uma liberação autorizada. Conferir o débito e os registros na auditoria após repetição.
6. Conferir expiração de sessão e bloqueio do acesso pelo perfil indevido. Navegar pelo teclado e conferir área ativa, foco e disposição a 1366 × 768, 1024 × 768 e 390 × 844.

## Evidências

A suíte Java com integração MySQL passou com 100 testes, sem falhas, erros ou testes ignorados. Os cinco testes de operações do portal passaram. O ensaio funcional em navegador completou os fluxos dos dois perfis. Para a regressão, com o ambiente de integração configurado, executar:

```powershell
./mvnw.cmd "-Dtest=*Test,*IT" test
node --test scripts/testar-operacoes-portal.mjs
```

[Catálogo](catalogo.png) · [Visão geral do Arquiteto](visao-arquiteto.png) · [Orçamento em 1024 × 768](orcamento-projetor.png) · [Arquiteto em tela estreita](arquiteto-completo-mobile.png)

A verificação de acessibilidade cobriu teclado e foco. A legibilidade no projetor físico e a experiência com leitor de tela ainda dependem de ensaio.
