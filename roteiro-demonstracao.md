# Roteiro de Demonstração ao Vivo — Orquestrador FinOps

Guia prático de execução dos fluxos funcionais do portal web para validação e apresentação do sistema.

## 1. Pré-requisitos de Ambiente
- Instância MySQL ativa com as migrações `V001` a `V005` aplicadas (`src/main/resources/db/mysql/`).
- Servidor HTTP nativo (`FinopsServidor`) iniciado localmente.
- Acesso inicial via navegador em `/login.html`.

## 2. Sequência de Demonstração

### Etapa A — Submissão Aprovada Automaticamente (Perfil Desenvolvedor)
1. Autenticar com uma conta de **Desenvolvedor** (`desenvolvedor.html`).
2. Selecionar um departamento com saldo disponível e adicionar itens do catálogo cujo valor total fique **abaixo do teto**.
3. Submeter a requisição.
4. **Resultado esperado:** Requisição criada e aprovada automaticamente, com abatimento imediato do saldo orçamentário do departamento.

### Etapa B — Retenção por Limite Orçamentário (Perfil Desenvolvedor)
1. Ainda como **Desenvolvedor**, montar uma nova requisição no mesmo departamento com quantidade elevada de recursos, ultrapassando o saldo restante.
2. Submeter a requisição.
3. **Resultado esperado:** O motor orçamentário impede o estouro do teto e encaminha a requisição para revisão manual.

### Etapa C — Governança, Ajuste de Teto e Auditoria (Perfil Arquiteto)
1. Encerrar a sessão e autenticar com uma conta de **Arquiteto** (`arquiteto.html`).
2. Ajustar o teto orçamentário de um departamento e verificar a atualização do limite.
3. Localizar a requisição retida na Etapa B, preencher uma justificativa e registrar a decisão manual.
4. **Resultado esperado:** Estado da requisição atualizado conforme a decisão e registro imutável exibido na trilha de auditoria.
