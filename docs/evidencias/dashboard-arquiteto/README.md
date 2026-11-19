# Dashboard do Arquiteto

Ensaio de 19/11/2026, em Edge, HTTPS e MySQL 8.0.44 descartável. [Orçamento inicial](visao-arquiteto.png), [painel com cinco intervenções](painel-completo.png) e [janela de 1024 × 768](painel-projetor.png).

Para repetir, use dois departamentos e o catálogo acadêmico em instância exclusiva de teste. Abra a visão geral de Engenharia com teto e saldo de R$ 1.000,00. Confira nove preços e o histórico vazio. Alterne rapidamente para outro departamento e volte. Uma resposta atrasada do outro departamento deve ser descartada. Uma consulta de atividades com erro deve exibir indisponibilidade.

No fluxo manual, a aprovação técnica preservou o saldo. A liberação de R$ 900,58 deixou R$ 99,42. O ajuste do teto para R$ 2.500,00 e a liberação posterior de R$ 1.200,00 deixaram R$ 399,42 e utilização de 84,02%. A repetição do comando confirmou o mesmo resultado. O histórico completo apresentou dez eventos e o painel mostrou cinco. O filtro da área Auditoria continuou independente.

A regressão Java incluiu os 100 testes convencionais e de integração com MySQL do guia, com zero falhas. Para a regressão de interface, execute na raiz:

```powershell
node --test scripts/testar-*.mjs
```

Oito testes aprovados, incluindo teto zero, saldo zero, centavos, arredondamento e valores grandes. As capturas são do navegador, sem ensaio em projetor físico.
