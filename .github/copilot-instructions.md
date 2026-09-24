# Instruções do Agente - Projeto Orquestrador FinOps

1. **Spec-Driven Development (SDD):** Nunca gere código de implementação sem antes consultar e referenciar a Spec aprovada. O código deve ser subordinado à Spec.
2. **Decisões em Aberto:** Não preencha lacunas de arquitetura (como escolha de banco de dados ou bibliotecas) silenciosamente. Registre como OPEN-XX.
3. **Segurança:** Peça confirmação humana (ask) antes de executar comandos no terminal que alterem dependências (ex: `npm install`, `mvn install`) ou comandos Git destrutivos.
4. **Segredos:** É expressamente proibido ler ou expor arquivos de variáveis de ambiente (`.env`) que contenham senhas reais.