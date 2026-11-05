# Orquestrador FinOps

Portal interno de governança de nuvem da disciplina de Modelagem de Sistemas, curso de Engenharia Elétrica da Universidade Presbiteriana Mackenzie. O sistema registra pedidos de infraestrutura e aplica regras de orçamento e autorização. A entrega acadêmica é em **26/11/2026**. Repositório oficial [Knzo25e/orquestrador-finops](https://github.com/Knzo25e/orquestrador-finops).

O projeto usa Java 25, Maven Wrapper (Maven 3.10.0), Spring Boot 4.1.2 com Spring MVC no adaptador HTTP, JDBC com Connector/J 9.7.0 e MySQL/InnoDB. Confira as versões efetivas em [pom.xml](pom.xml) e na [configuração do Wrapper](.mvn/wrapper/maven-wrapper.properties).

## Fontes de verdade

- Consulte os [requisitos, regras e estados](docs/spec.md), a [arquitetura e os ADRs](docs/arquitetura.md) e o [mapa de Specs](docs/mapa-de-specs.md). Localize a Spec individual e o caso de uso em [docs/casos-de-uso](docs/casos-de-uso) e confira a Issue e o PR vinculados.
- Use requisitos e ADRs vigentes para orientar a solução e a Spec aprovada para o contrato do recorte. Uma decisão posterior explicitamente aprovada prevalece sobre o trecho que ela revisa. A integração de uma proposta em um PR, por si só, não aprova todas as decisões abertas.
- Use [README](README.md), [plano](docs/plan.md), [tarefas](docs/tasks.md) e [evidências](docs/evidencias) para localizar comandos, histórico e limites. Confirme o estado da implementação no código, nos testes e no histórico integrado.
- Em caso de divergência, identifique os trechos e as decisões aplicáveis, registre a questão como OPEN-XX, explique o impacto, apresente alternativas e recomende uma delas. Aguarde a decisão do responsável antes de implementar a parte afetada.

## Arquitetura e desenvolvimento

Os pacotes ficam em `src/main/java/br/mackenzie/finops` e seguem a separação do ADR-001.

- `dominio` contém entidades, regras monetárias e transições de estado em Java puro.
- `aplicacao` contém casos de uso, portas, contratos e política de autorização.
- `infraestrutura` contém os adaptadores HTTP, JDBC e em memória. Controllers traduzem entradas, respostas e erros públicos. Adaptadores JDBC coordenam transações e persistem decisões do domínio. Os scripts MySQL ficam em [src/main/resources/db/mysql](src/main/resources/db/mysql).
- Reutilize os casos de uso, portas e políticas existentes. Preserve contratos HTTP, compatibilidade com registros históricos e limites da Spec. Evite dependências, regras duplicadas e funcionalidades fora do escopo.
- Use as entradas autorizadas para operações expostas ao usuário. Os caminhos internos de compatibilidade e os provedores de identidade controlados dos testes não devem ser expostos como interface protegida.

## Invariantes

- Use valores monetários exatos. Preserve preços históricos dos itens e calcule o total no servidor. A criação grava em `INICIAL` e deixa o saldo intacto.
- A validação usa o departamento vinculado ao pedido. A aprovação automática exige custo dentro do saldo e saldo resultante estritamente acima da reserva de 10% do teto, arredondada com `HALF_UP` para duas casas. Encaminhamento à revisão preserva o saldo e registra o motivo.
- Nunca permita saldo negativo ou comprometimento duplicado. A verificação, a mudança de estado e o débito formam uma operação consistente, inclusive em concorrência. No fluxo JDBC vigente, preserve a ordem de bloqueios departamento → requisição.
- A aprovação técnica, quando implementada, deve permanecer separada da liberação financeira. Respeite as transições aprovadas e revalide as condições vigentes antes de liberar. `PROVISIONAMENTO_LIBERADO` indica autorização financeira. A integração real com a AWS foi postergada pelo ADR-004.
- Derive autor, perfil, situação ativa e vínculo de fonte confiável no servidor. Reavalie a autorização nas tentativas aplicáveis e preserve o isolamento por autoria e departamento atual para Desenvolvedores. Mantenha a autoria desconhecida dos registros legados.
- Preserve o UUID e a sequência de itens na repetição da criação. Diferencie repetição equivalente, conflito, acesso negado e confirmação desconhecida. Um resultado incerto do commit exige reconciliação conforme a Spec.

## Fluxo de trabalho

1. Inspecione a branch, alterações locais, documentação, código, testes e registros da tarefa.
2. Apresente objetivo, arquivos envolvidos, estratégia, testes, impactos e riscos. Obtenha autorização para implementar.
3. Implemente em branch de trabalho, com mudanças incrementais no escopo aprovado. Recomende revisão pelo Astra para arquitetura, regras financeiras, transações concorrentes, segurança, idempotência, contratos HTTP, novos estados ou contradições entre Specs.
4. Execute as verificações pertinentes, confronte os resultados com os critérios de aceite e atualize a documentação operacional.
5. Registre as alterações no Git e entregue o resumo com arquivos, evidências, limitações, decisões pendentes e próxima ação.

## Build e verificações

- Configure `JAVA_HOME` com o JDK 25 na sessão. No Windows, use `./mvnw.cmd -version`, `./mvnw.cmd compile` e `./mvnw.cmd verify`.
- O Maven executa os testes JUnit convencionais. Para incluir a integração HTTP com MySQL, execute `./mvnw.cmd "-Dtest=*Test,CriacaoHttpMysqlIT" test` com um `finops_test` isolado, scripts V001 e V002 aplicados e dados de cenário. Confira `FINOPS_IT_AUTOR`, `FINOPS_IT_DEPARTAMENTO` e `FINOPS_IT_RECURSO` no teste.
- Execute os verificadores com método `main` separadamente, conforme o [README](README.md) e as evidências da Issue pertinente. Os cenários JDBC exigem Connector/J no classpath, as variáveis `FINOPS_DB_URL`, `FINOPS_DB_USER` e `FINOPS_DB_PASSWORD` e uma base exclusiva de teste. Verifique a limpeza dos dados dos cenários.
- Nunca leia ou exponha segredos de arquivos `.env` ou inclua credenciais em logs, commits e PRs. Consulte [.github/copilot-instructions.md](.github/copilot-instructions.md) para as regras de confirmação de mudanças de dependências.

## Git e GitHub

- Após a aprovação da tarefa, use branch específica, preferencialmente sob `codex/`, commits pequenos e descritivos (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`) e push somente da branch de trabalho. Vincule a Issue e a Spec quando aplicável.
- Procure registros equivalentes antes de abrir Issues. Use Issues para trabalho independente, defeitos relevantes e decisões que exigem acompanhamento. Registre contexto, requisitos, critérios, dependências e prioridade.
- Abra PRs como Draft. Registre escopo, Issue e Spec, critérios comprovados, testes, evidências, riscos e decisões pendentes. Marcar o PR como pronto exige autorização.
- Obtenha autorização explícita para merge em `main` ou branch protegida, push direto em `main`, force push, rebase ou reset destrutivo, exclusão de branches, descarte de alterações locais, alteração de Specs ou decisões aprovadas, mudanças de regras financeiras ou de segurança e fechamento de Issues sem conclusão comprovada.

## Critérios de conclusão

O trabalho deve respeitar a Spec e as decisões aprovadas, comprovar cada critério de aceite aplicável, registrar comandos e resultados dos testes, explicitar verificações pendentes e atualizar a documentação pertinente. Priorize exigências do professor, fluxo demonstrável e correções que bloqueiem a entrega, preservando segurança e integridade financeira.
