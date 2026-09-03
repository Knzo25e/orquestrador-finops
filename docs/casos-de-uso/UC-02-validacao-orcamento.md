**Nome:** [RF-02] Validação Automática de Orçamento
**Ator:** Desenvolvedor, Sistema
**Objetivo:** Avaliar o custo da requisição frente ao orçamento e definir o roteamento.
**Pré-condições:** O Desenvolvedor finalizou a seleção de recursos no catálogo e submeteu o pedido.

**Fluxo Principal (Caminho Feliz):**
1. O Desenvolvedor envia a requisição de infraestrutura para análise.
2. O Sistema calcula o `custoTotalProjetado` somando os itens selecionados.
3. O Sistema consulta o `saldoDisponivel` atual do Departamento.
4. O Sistema verifica que o custo é menor ou igual ao saldo.
5. O Sistema altera o status da requisição para "Provisionamento Liberado".
6. O Sistema atualiza o saldo do Departamento descontando o valor.

**Fluxos Alternativos / Exceções:**
*   **Extensão 1 (Estouro de Orçamento):** No passo 4, se o `custoTotalProjetado` for maior que o `saldoDisponivel`, o Sistema altera o status para "Revisão Pendente". O fluxo automático encerra e a requisição vai para a fila do Arquiteto Cloud.

**Regras de Negócio e Cláusulas EARS:**
*   **[RN-02]** O sistema não pode aprovar automaticamente pedidos que ultrapassem o saldo disponível da equipe.
*   **WHILE** a requisição estiver em validação, **IF** o custo ultrapassar o saldo, **THEN** o sistema deve bloqueá-la e mudar o status para "Revisão Pendente".

**Pós-condições:** A requisição avança com status atualizado e, se aprovada, o cofre do departamento é descontado.