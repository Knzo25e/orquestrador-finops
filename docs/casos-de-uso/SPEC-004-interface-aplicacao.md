# SPEC-004 — Interface de consulta do catálogo e criação de requisições

## Identificação

- Issue: #81.
- Status: recorte funcional aprovado pelo responsável pelo projeto
  na Issue #81; detalhes do contrato em revisão.
  Decisões OPEN-004-02 a OPEN-004-07 e implementação pendentes.
- Objetivo: permitir consulta do catálogo e criação persistente
  de requisições por uma interface de aplicação, preservando
  autorização, idempotência e garantias transacionais.
- Dependências: SPEC-001, SPEC-002, SPEC-005 e SPEC-006.
- Relação com a SPEC-003: a validação financeira é uma operação
  distinta da criação.

## Rastreabilidade

- RF-01: consulta do catálogo e composição de requisições.
- RB-01: custos mensais definidos no catálogo persistente.
- RNF-02: isolamento entre departamentos.
- ADR-001: separação entre domínio, aplicação e infraestrutura.
- ADR-002: persistência MySQL.
- ADR-003: Java e futura integração com Spring Boot.
- Issue #76 e PR #77: autorização dos fluxos persistentes.
- RF-02 e RB-02/RB-05: referências para delimitar a validação
  financeira, sem incorporá-la implicitamente à criação.

## Escopo proposto

### Incluído

- Contrato REST para consulta do catálogo.
- Contrato REST para criação persistente de requisições.
- Validação e conversão das entradas da interface.
- Identificação estável da requisição nas novas tentativas.
- Respostas para criação confirmada, repetição equivalente,
  conflito, negativa de acesso e confirmação desconhecida.
- Representação de avisos após sucesso confirmado.
- Integração com os casos de uso autorizados da aplicação.
- Dependências de identidade confiável e coordenação da criação.
- Critérios de aceite e cenários de teste da interface.

Endpoints de consulta e listagem de requisições ficam fora deste
primeiro recorte, conforme a delimitação registrada neste documento.

### Fora deste recorte

- Implementação dos endpoints nesta etapa documental.
- Portal ou outra interface gráfica.
- Login, senhas, sessões, tokens e provedor real de identidade.
- Administração de usuários, departamentos e catálogo.
- Validação financeira acionada implicitamente pela criação.
- Aprovação técnica, ajuste de orçamento e autorização de reserva.
- Provisionamento real ou simulado de infraestrutura.

A exclusão da implementação de autenticação não permite expor
operações protegidas sem uma integração confiável de identidade.

## Relação entre criação e validação financeira

Propõe-se que a operação de criação persista uma nova requisição
em estado INICIAL, com autoria e departamento derivados da identidade.

A criação calcula e preserva os valores dos itens, mas não desconta
saldo, não executa roteamento e não autoriza provisionamento.

Uma repetição equivalente retorna o pedido existente, preservando
seus preços históricos e seu estado atual. Não deve restaurá-lo
para INICIAL nem repetir efeitos financeiros.

A submissão à validação automática pertence a uma operação distinta,
cuja exposição e autorização devem ser especificadas antes da
implementação. Portanto, este recorte não entrega sozinho todo o
fluxo de solicitação até a liberação financeira.

## Limites da base existente

O PR #77 integrou autorização à criação, consulta, listagem e repetição
persistentes.

A coordenação de identidade foi demonstrada com fonte controlada
no mesmo processo. Essa evidência não constitui autenticação de
produção nem comprova coordenação com um provedor externo.

Uma futura interface de usuário deve utilizar as entradas autorizadas.
A criação deve utilizar CriarRequisicaoCoordenadaUseCase, com uma
implementação confiável de ExecucaoComIdentidadeCoordenada.

Autor, perfil, situação ativa e departamento não podem ser aceitos
como declarações confiáveis enviadas pelo cliente.

A versão do Spring Boot e sua configuração permanecem pendentes.
Este documento não comprova implementação nem atendimento integral
às SPECs 004, 005 ou 006.

## UC-01 — Consultar catálogo e criar requisição

### Atores

- Desenvolvedor: consulta o catálogo e cria requisições.
- Arquiteto Cloud: pode consultar o catálogo, mas não criar requisições.

### Pré-condições

- Identidade obtida por uma integração confiável.
- Usuário ativo e com perfil reconhecido pela política.
- Para criação: Desenvolvedor com vínculo departamental válido.
- Persistência disponível para concluir a operação solicitada.

A consulta ao catálogo não exige vínculo departamental.
Essa permissão não concede autorização para criar requisições.

### Fluxo principal proposto

1. O usuário consulta o catálogo.
2. A aplicação verifica a identidade e a permissão de consulta.
3. A interface apresenta os recursos e seus custos mensais.
4. O Desenvolvedor seleciona recursos e informa as quantidades.
5. O cliente prepara um UUID para identificar a tentativa lógica
   de criação e o preserva nas novas tentativas.
6. O cliente envia o UUID e a sequência de itens.
7. A aplicação obtém a identidade dentro da coordenação e deriva
   dela autor e departamento.
8. A operação persistente verifica autorização, recursos e valores,
   calcula o total e grava o pedido e seus itens.
9. Após confirmação do commit, a interface informa a criação
   em estado INICIAL.

A geração do UUID e sua representação no contrato HTTP serão
detalhadas na seção de criação.

### Fluxos alternativos

- Catálogo vazio: consulta bem-sucedida com coleção vazia.
- Identidade ausente, inativa ou não autorizada: operação negada.
- Falha na obtenção da identidade: operação interrompida,
  sem consultar ou modificar a persistência de negócio.
- Desenvolvedor sem vínculo: pode consultar o catálogo,
  mas não criar requisições.
- Entrada inválida ou recurso inexistente: criação rejeitada,
  sem persistência parcial.
- Repetição equivalente autorizada: retorna o pedido existente,
  preservando preços históricos e estado.
- UUID existente fora do escopo: negativa sem devolver o pedido.
- Conteúdo diferente para UUID existente e autorizado: conflito.
- Confirmação desconhecida: informa a incerteza; uma nova tentativa
  deve preservar o UUID e o conteúdo original.

### Pós-condições

- Criação confirmada: pedido e itens persistidos com autoria
  e departamento derivados da identidade.
- Criação não desconta saldo nem executa validação financeira.
- Repetição não reinicia o estado nem altera os preços históricos.
- Negativas de acesso não modificam o pedido existente.
- Confirmação desconhecida não permite afirmar que houve rollback.

## Consulta do catálogo — contrato proposto

### Operação

GET /api/v1/catalogo/recursos

A operação retorna os recursos disponíveis no catálogo persistente.
Não permite cadastrar, alterar ou remover recursos.

### Autorização

- Desenvolvedor ativo: permitido.
- Arquiteto Cloud ativo: permitido.
- Ausência de vínculo departamental: não impede esta consulta.
- Identidade ausente, inativa ou perfil não reconhecido: negado.
- Falha do provedor: interrompe a operação.

A aplicação deve utilizar PoliticaAutorizacao.podeConsultarCatalogo.
O adaptador HTTP não deve duplicar essa regra.

### Representação de sucesso

A resposta contém um objeto com a coleção recursos.
Cada recurso contém somente:

- id: UUID.
- nome: nome do recurso.
- custoMensal: valor decimal representado como texto,
  sem separador de milhares e com duas casas decimais.

Exemplo de recurso:

    {
      "id": "8c7d3962-441c-4b78-a2fd-03f9b53ef6dd",
      "nome": "Recurso de exemplo",
      "custoMensal": "12.50"
    }

Catálogo vazio:

    {
      "recursos": []
    }

Não retornar entidades Java diretamente. A representação pública
não inclui detalhes JDBC, configuração de conexão ou dados
orçamentários dos departamentos.

CPU, RAM e armazenamento não integram esta representação inicial.
Esses atributos aparecem no modelo documental, mas sua persistência
e exposição exigem um recorte próprio.

### Ordenação e abrangência

Neste primeiro contrato, a consulta retorna a coleção completa,
ordenada pelo UUID canônico em ordem crescente.

Filtros, paginação e contagem total não fazem parte deste recorte.
Antes da implementação, deve ser registrado o tamanho esperado
do catálogo e validada a viabilidade da resposta completa.

Se for necessária paginação, seu contrato deverá ser especificado
antes da implementação, sem truncamento silencioso da coleção.

### Preços apresentados e preços da criação

O preço consultado não constitui uma reserva de preço.

A criação utiliza os valores lidos pela operação transacional.
Uma alteração do catálogo entre consulta e criação pode produzir
um total diferente daquele estimado pelo cliente.

O cliente não fornece preços, subtotais ou total como valores
autoritativos. A resposta da criação apresenta os valores
efetivamente persistidos.

Repetições equivalentes preservam os preços históricos do pedido,
mesmo que o catálogo tenha sido alterado.

### Dependências de implementação

O contrato atual RecursoCatalogoRepository oferece adicionar
e buscarPorId. Não oferece listagem.

A implementação desta operação exige:

- Caso de uso autorizado para consulta do catálogo.
- Contrato de aplicação para leitura da coleção persistente.
- Implementação JDBC da listagem com ordenação definida.
- Controle e fechamento dos recursos de consulta.
- Mapeamento para a representação pública.
- Testes de autorização, catálogo vazio, ordenação, valores
  monetários e falhas de persistência.

Essas capacidades são requisitos desta proposta, não funcionalidades
já implementadas.

Os códigos HTTP e o formato comum de erros estão descritos
na seção Resultados e erros HTTP — proposta.

## Criação de requisição — contrato proposto

### Operação

POST /api/v1/requisicoes

O corpo da requisição utiliza JSON e contém somente:

- requisicaoId: UUID que identifica a tentativa lógica de criação.
- itens: sequência não vazia de recursos e quantidades.
- Cada item contém recursoId e quantidade.

Exemplo:

    {
      "requisicaoId": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
      "itens": [
        {
          "recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f",
          "quantidade": 2
        }
      ]
    }

Autor, departamento, perfil, situação ativa, preço, subtotal,
total e estado não são campos aceitos na entrada.

Campos desconhecidos, inclusive dentro dos itens, devem ser
rejeitados. Não devem ser ignorados silenciosamente.

### Identificação e novas tentativas

O cliente gera um UUID aleatório antes do primeiro envio e mantém
esse identificador associado ao conteúdo da solicitação.

O mesmo UUID deve ser reutilizado quando houver:

- Perda da resposta.
- Interrupção da conexão.
- Confirmação de criação desconhecida.
- Nova tentativa da mesma solicitação.

Uma nova intenção de criação utiliza outro UUID.

O identificador não autentica o usuário nem concede acesso ao pedido.
Toda tentativa passa novamente pela autorização.

Este contrato utiliza requisicaoId como identificador de repetição.
Não introduz uma segunda chave de idempotência em cabeçalho HTTP.

### Validação estrutural

- O corpo deve ser um objeto JSON.
- requisicaoId é obrigatório e não pode ser nulo.
- itens é obrigatório, não pode ser nulo e deve conter pelo menos
  um elemento.
- Nenhum elemento de itens pode ser nulo.
- recursoId é obrigatório em cada item.
- UUIDs devem utilizar a representação canônica com 36 caracteres,
  letras minúsculas e hífens.
- quantidade deve ser um número inteiro entre 1 e 2147483647.
- Quantidades em texto, fracionárias, nulas, iguais a zero ou
  negativas devem ser rejeitadas.
- Não converter silenciosamente valores fracionários em inteiros.
- Propriedades JSON duplicadas devem ser rejeitadas para evitar
  interpretações ambíguas da entrada.

Limites operacionais de tamanho do corpo e número de itens deverão
ser definidos antes da implementação. O limite do tipo inteiro
não representa, por si só, um limite operacional adequado.

### Sequência dos itens

A ordem dos itens faz parte do conteúdo da solicitação.

Um mesmo recurso pode aparecer em posições diferentes.
Esses itens permanecem separados.

A interface não deve ordenar, agrupar ou eliminar itens repetidos
antes de delegar à aplicação.

Nas novas tentativas, o cliente deve preservar a sequência,
os identificadores dos recursos e suas quantidades.

### Identidade e autorização

O adaptador utiliza CriarRequisicaoCoordenadaUseCase.

Autor e departamento são obtidos da identidade confiável durante
a execução coordenada. Não são derivados do corpo JSON nem de
cabeçalhos de identidade livremente preenchidos pelo cliente.

Somente o Desenvolvedor ativo, com vínculo departamental válido,
pode criar requisições.

A coordenação deve permanecer válida durante autorização,
transação e eventual reconciliação após disputa pelo UUID.

A simples leitura inicial da identidade não atende a essa garantia.
Todos os caminhos que alteram seus atributos de autorização devem
participar do protocolo definido pela integração confiável.

### Resultado de uma criação nova

Uma criação nova somente é apresentada como confirmada após
confirmação do commit.

O pedido é persistido em INICIAL, com os itens e valores calculados
pela operação transacional.

A operação não desconta saldo, não executa validação financeira
e não provisiona infraestrutura.

### Repetição de uma criação

A operação verifica a autorização sobre o pedido existente antes
de comparar seu conteúdo ou devolvê-lo.

Uma repetição equivalente autorizada:

- Retorna resultado JA_EXISTENTE.
- Preserva autoria, departamento, sequência e valores históricos.
- Preserva o estado e o motivo de revisão armazenados.
- Não repete efeitos financeiros.

O estado retornado pode ser diferente de INICIAL caso o pedido
tenha sido processado por outra operação.

Uma repetição autorizada com conteúdo diferente produz conflito.
Uma repetição fora do escopo produz negativa sem devolver o pedido,
seus itens ou seus valores.

Após mudança de vínculo, a autoria anterior não concede acesso
ao pedido do departamento antigo.

### Confirmação desconhecida e avisos após sucesso

Confirmação desconhecida significa que a aplicação não conseguiu
determinar se o commit foi efetivado.

A resposta não deve afirmar que a criação foi desfeita.
Uma tentativa de rollback não elimina essa incerteza.

O cliente deve preservar o UUID e o conteúdo original para uma
nova tentativa, que continuará sujeita à autorização vigente.

Quando a operação retorna sucesso com aviso de falha no fechamento,
a interface preserva o resultado confirmado e representa o aviso
separadamente. Não deve convertê-lo em falha de criação.

O formato das respostas e os códigos HTTP estão descritos
na seção Resultados e erros HTTP — proposta.

## Resultados e erros HTTP — proposta

### Respostas de sucesso

| Operação e resultado | Código HTTP |
|---|---:|
| Consulta do catálogo, inclusive vazio | 200 |
| Criação nova com commit confirmado: CRIADA | 201 |
| Repetição equivalente autorizada: JA_EXISTENTE | 200 |

As respostas utilizam Content-Type: application/json.

### Representação da criação

A resposta contém:

- situacao: CRIADA ou JA_EXISTENTE.
- requisicao: representação pública do pedido.
- avisos: coleção de avisos; vazia quando não houver aviso.

A representação do pedido contém:

- id.
- estado.
- motivoRevisao, podendo ser nulo.
- custoTotalProjetado.
- itens, preservando a sequência original.

Cada item contém:

- recursoId.
- quantidade.
- precoUnitario.
- subtotal.

Os valores monetários são textos decimais com duas casas,
sem separador de milhares. Preços e subtotais são os valores
históricos do pedido, não os preços atuais do catálogo.

Exemplo de criação confirmada:

    {
      "situacao": "CRIADA",
      "requisicao": {
        "id": "0116c1b1-c55e-4ce7-b9e2-0dad46269507",
        "estado": "INICIAL",
        "motivoRevisao": null,
        "custoTotalProjetado": "25.00",
        "itens": [
          {
            "recursoId": "ebcd32eb-7ac9-4938-be36-25efeb24083f",
            "quantidade": 2,
            "precoUnitario": "12.50",
            "subtotal": "25.00"
          }
        ]
      },
      "avisos": []
    }

A resposta não expõe saldo, teto mensal, credenciais, configuração
de conexão ou a estrutura completa das entidades Java.

### Estados públicos

O adaptador deve mapear explicitamente os estados existentes,
sem depender da serialização direta de getStatus().

| Estado público | Significado |
|---|---|
| INICIAL | Pedido criado, sem validação financeira concluída |
| PROVISIONAMENTO_LIBERADO | Liberação financeira registrada; não comprova provisionamento |
| REVISAO_PENDENTE | Pedido encaminhado para revisão |

Os motivos públicos de revisão são SALDO_INSUFICIENTE e RESERVA.
Nos estados que não admitem motivo, motivoRevisao é nulo.

Estados adicionais do fluxo manual exigem evolução explícita
do contrato. Não devem ser inventados ou convertidos silenciosamente
para um dos estados acima.

### Aviso após sucesso

Quando o resultado transacional indicar falha de fechamento
posterior ao sucesso, manter 201 ou 200 conforme a situação e incluir:

    {
      "codigo": "FALHA_NO_FECHAMENTO",
      "mensagem": "A operação foi concluída, mas houve uma falha ao encerrar seus recursos."
    }

Esse objeto integra a coleção avisos.
O aviso não significa confirmação desconhecida e não exige
uma nova criação.

### Formato comum de erro

As respostas de erro contêm um objeto erro com código estável
e mensagem pública, sem detalhes internos.

Exemplo:

    {
      "erro": {
        "codigo": "ACESSO_NEGADO",
        "mensagem": "Operação não autorizada."
      }
    }

Não incluir stack traces, SQL, nomes de tabelas, credenciais
ou conteúdo de pedidos não autorizados.

### Classificação dos erros

| Situação | HTTP | Código público |
|---|---:|---|
| JSON malformado, campos inválidos, desconhecidos ou duplicados | 400 | ENTRADA_INVALIDA |
| Credenciais ausentes ou inválidas, conforme integração de autenticação | 401 | AUTENTICACAO_NECESSARIA |
| Identidade autenticada sem permissão para a operação | 403 | ACESSO_NEGADO |
| Repetição fora do escopo autorizado | 403 | ACESSO_NEGADO |
| Conteúdo diferente para UUID existente e autorizado | 409 | CONFLITO_IDENTIFICADOR |
| Corpo acima do limite documentado | 413 | CORPO_MUITO_GRANDE |
| Tipo de conteúdo não suportado | 415 | TIPO_DE_CONTEUDO_NAO_SUPORTADO |
| Entrada estruturalmente válida, mas rejeitada por regra de negócio | 422 | SOLICITACAO_INVALIDA |
| Confirmação do commit desconhecida | 503 | CONFIRMACAO_CRIACAO_DESCONHECIDA |
| Indisponibilidade temporária identificada de uma dependência | 503 | SERVICO_INDISPONIVEL |
| Falha interna sem classificação pública mais específica | 500 | FALHA_INTERNA |

O catálogo vazio é sucesso, não erro 404.

Um recurso inexistente em uma nova solicitação é uma rejeição
de negócio, sem persistência parcial.

Não mapear toda IllegalArgumentException para 400, nem toda
falha de persistência para 503. A integração deve distinguir
validação da entrada, rejeição de negócio, indisponibilidade
e erro interno.

O resultado de confirmação desconhecida tem precedência sobre
uma classificação genérica de falha de persistência.

### Autenticação e autorização

A resposta 401 depende da escolha do mecanismo de autenticação
e deve incluir o desafio WWW-Authenticate correspondente.

O mecanismo inicial aprovado utiliza sessão no servidor com cookie,
conforme OPEN-004-02.

O contrato de respostas para autenticação ausente ou inválida,
incluindo o uso de 401 e WWW-Authenticate, ainda exige definição
compatível com esse mecanismo.

Não anunciar Basic ou Bearer se esses mecanismos não forem
efetivamente oferecidos pela aplicação.

AcessoNegadoException não deve ser convertido automaticamente
em 401: também representa negativas de autorização.

Falhas técnicas do provedor não devem ser apresentadas como
credenciais inválidas. Devem interromper a operação e receber
classificação de indisponibilidade ou falha interna.

A ordem entre autenticação e leitura do corpo será definida
na integração HTTP.

Falhas de autenticação, negativas de permissão identificáveis
pela identidade e erros estruturais da entrada devem impedir
a chamada à persistência de negócio.

Rejeições que dependem dos dados armazenados, como recurso
inexistente, conflito de conteúdo ou pedido fora do escopo,
podem exigir consultas à persistência. Essas rejeições não
devem produzir alterações persistidas nos dados de negócio.

### Resposta de confirmação desconhecida

Exemplo:

    {
      "erro": {
        "codigo": "CONFIRMACAO_CRIACAO_DESCONHECIDA",
        "mensagem": "Não foi possível confirmar o resultado da criação. Preserve o identificador e o conteúdo para uma nova tentativa.",
        "requisicaoId": "0116c1b1-c55e-4ce7-b9e2-0dad46269507"
      }
    }

O identificador é o da solicitação recebida.
A resposta não confirma existência nem ausência do pedido.

Não utilizar 202: não há processamento assíncrono ou acompanhamento
em segundo plano garantido por este contrato.

Não indicar que uma repetição será necessariamente autorizada:
a identidade e o vínculo podem mudar entre tentativas.

### Limite de ocultação da existência

A negativa de repetição não expõe o pedido nem informa seu autor,
departamento ou conteúdo.

Entretanto, a criação com UUID livremente escolhido pode produzir
respostas distintas para UUID disponível e UUID ocupado fora do escopo.
Portanto, este POST não oferece garantia absoluta de ocultação
da existência de identificadores.

Essa limitação foi aceita para o primeiro recorte acadêmico,
conforme OPEN-004-04. A SPEC-006 deve registrar a distinção entre
ocultação na consulta e proteção do conteúdo na criação e repetição.

A equivalência entre pedido inexistente e pedido fora do escopo
na consulta por UUID é uma garantia distinta, que não deve ser
atribuída automaticamente ao POST.

## Consulta e listagem de requisições — delimitação proposta

Este recorte especifica somente:

- GET /api/v1/catalogo/recursos.
- POST /api/v1/requisicoes.

Não inclui endpoints para buscar uma requisição, listar pedidos,
contar resultados ou paginar requisições.

ConsultarRequisicaoAutorizadaUseCase e
ListarRequisicoesAutorizadasUseCase já existem na aplicação,
mas sua exposição HTTP exige contrato e testes próprios.

### Consequências para o cliente

O cliente recebe a representação do pedido na resposta da criação
ou da repetição equivalente autorizada.

Este recorte não oferece acompanhamento posterior do estado
por uma operação de consulta HTTP.

Uma nova tentativa do POST exige preservar o UUID e o conteúdo
original. Não substitui uma interface geral de consulta.

O POST não aceitará corpo vazio ou conteúdo parcial para funcionar
como busca por UUID.

A resposta de criação não anunciará uma rota de consulta inexistente.
O cabeçalho Location não será incluído enquanto não houver uma URI
de recurso definida no contrato.

### Requisitos para uma extensão futura

Uma futura consulta por UUID deverá:

- Utilizar o caso de uso autorizado da aplicação.
- Retornar a mesma resposta pública para pedido inexistente
  e pedido fora do escopo do Desenvolvedor.
- Permitir ao Arquiteto ativo consultar pedidos de outros
  departamentos, inclusive sem autoria conhecida.
- Não expor entidades Java diretamente.

Uma futura listagem deverá:

- Aplicar autorização e filtros antes de devolver resultados.
- Restringir o Desenvolvedor à própria autoria e ao departamento atual.
- Definir a seleção de departamento permitida ao Arquiteto.
- Especificar ordenação, limites e eventual paginação.
- Aplicar o mesmo escopo às contagens e aos metadados.
- Documentar o comportamento diante de mudanças de identidade
  durante a consulta.

Esses requisitos não representam endpoints implementados nesta etapa.

## Critérios de aceite propostos

Os critérios abaixo deverão ser verificados na futura implementação.
Não representam testes HTTP já executados.

### Catálogo

- CAT-01: Desenvolvedor ativo pode consultar o catálogo.
- CAT-02: Arquiteto ativo pode consultar o catálogo.
- CAT-03: Ausência de vínculo departamental não impede a consulta
  por um perfil ativo autorizado.
- CAT-04: Identidade ausente, inativa ou inválida impede o acesso.
- CAT-05: Falha do provedor impede acesso à persistência de negócio.
- CAT-06: Catálogo vazio retorna 200 com recursos vazio.
- CAT-07: Recursos são retornados na ordenação definida.
- CAT-08: Valores monetários preservam precisão e duas casas decimais.
- CAT-09: A resposta contém somente os campos públicos definidos.

### Entrada de criação

- CRI-01: Autor e departamento são derivados da identidade confiável.
- CRI-02: Arquiteto, Desenvolvedor inativo e Desenvolvedor sem vínculo
  não chegam à persistência de criação.
- CRI-03: Campos desconhecidos ou duplicados são rejeitados.
- CRI-04: UUIDs e quantidades inválidos são rejeitados.
- CRI-05: Lista ausente, nula, vazia ou contendo item nulo é rejeitada.
- CRI-06: A sequência dos itens e os recursos repetidos são preservados.
- CRI-07: Limites de corpo e quantidade de itens são aplicados
  conforme os valores aprovados no contrato.

### Persistência e repetição

- PER-01: Criação nova confirmada retorna 201 e situação CRIADA.
- PER-02: O pedido novo fica em INICIAL, sem desconto de saldo.
- PER-03: Repetição equivalente autorizada retorna 200 e JA_EXISTENTE.
- PER-04: Repetição preserva preços históricos, estado e motivo.
- PER-05: Conteúdo diferente para pedido autorizado retorna 409.
- PER-06: Pedido fora do escopo produz negativa sem devolver seus dados.
- PER-07: Reconciliação após duplicidade verifica novamente o acesso.
- PER-08: Falha durante a gravação não deixa persistência parcial
  quando o rollback é confirmado.
- PER-09: Confirmação desconhecida retorna o código específico,
  sem afirmar que houve rollback.
- PER-10: Aviso de fechamento após sucesso preserva o código
  de sucesso e o resultado confirmado.

### Identidade e coordenação

- SEG-01: Cabeçalhos ou campos enviados pelo cliente não permitem
  escolher livremente autor, perfil ou departamento.
- SEG-02: A criação utiliza a entrada coordenada da aplicação.
- SEG-03: A integração mantém a autorização válida durante a
  transação e eventual reconciliação.
- SEG-04: Mudanças dos atributos de autorização participam
  do mesmo protocolo de coordenação.
- SEG-05: O alcance da garantia é demonstrado no ambiente
  efetivamente utilizado, sem extrapolar testes locais
  para múltiplos processos.
- SEG-06: O tratamento de confirmação desconhecida explicita
  o alcance da coordenação quando o resultado remoto permanece
  indeterminado.

### Representação e erros

- HTTP-01: Respostas seguem os campos e códigos públicos definidos.
- HTTP-02: Estados e motivos são mapeados explicitamente.
- HTTP-03: Respostas não expõem SQL, stack traces, credenciais
  ou dados de pedidos não autorizados.
- HTTP-04: Falhas técnicas de identidade não são apresentadas
  como credenciais inválidas.
- HTTP-05: Respostas 401 utilizam o desafio correspondente
  ao mecanismo de autenticação aprovado.
- HTTP-06: Confirmação desconhecida não é convertida em erro
  genérico nem em processamento assíncrono garantido.
- HTTP-07: A implementação não anuncia endpoints de acompanhamento
  que não fazem parte do contrato.

### Evidências exigidas

- Registrar separadamente testes da aplicação, JDBC e HTTP.
- Executar testes do adaptador HTTP real; testes com adaptadores
  controlados não comprovam o comportamento do framework.
- Verificar preservação dos dados e limpeza por conexão independente
  nos cenários que utilizarem MySQL.
- Registrar ambiente, conjuntos executados e limitações.
- Não contar as 648 verificações da Issue #76 como testes HTTP.
- Não somar reexecuções como verificações distintas.

## Decisões pendentes e condições para implementação

Esta especificação é uma proposta. A aprovação do documento não
comprova implementação nem elimina as dependências abaixo.

### OPEN-004-01 — Recorte aprovado

Status: resolvida quanto ao recorte funcional, conforme confirmação
do responsável pelo projeto nesta etapa da Issue #81.

A primeira entrega HTTP compreenderá:

- Consulta autorizada do catálogo.
- Criação persistente de requisições em INICIAL.
- Tratamento de repetição, conflitos e confirmação desconhecida.
- Criação sem validação financeira ou desconto de saldo implícitos.

Consulta e listagem HTTP de requisições, acompanhamento do estado
e submissão à validação financeira ficam para entregas posteriores,
com contratos e critérios de aceite próprios.

A limitação foi aceita: esta primeira entrega não disponibiliza
todo o fluxo de solicitação, acompanhamento e liberação financeira.

A aprovação refere-se ao recorte funcional. Não resolve as decisões
OPEN-004-02 a OPEN-004-07, não aprova automaticamente todos os detalhes
do contrato e não autoriza exposição sem identidade confiável
e coordenação adequada.

### OPEN-004-02 — Autenticação e origem da identidade

#### Diretriz aprovada pelo responsável pelo projeto

Adotar contas próprias do projeto, com autenticação implementada
em Java/Spring Security, desde que não exista exigência explícita
do professor para integração institucional ou com provedor externo.

Manter no MySQL da aplicação os dados de autorização do usuário:

- Identificador estável.
- Perfil.
- Situação ativa.
- Vínculo departamental, quando aplicável.

A autenticação identifica o usuário. Os atributos usados na
autorização são obtidos da fonte confiável mantida no servidor,
não de campos ou cabeçalhos livremente preenchidos pelo cliente.

A consulta desses atributos deve respeitar a coordenação exigida
para a operação. Uma leitura isolada antes da transação não
comprova validade do vínculo durante a criação.

#### Mecanismo inicial aprovado

Adotar autenticação por sessão no servidor, integrada ao Spring
Security, com identificador de sessão transportado por cookie.

Planejar inicialmente portal e API na mesma origem.
Uma implantação em origens distintas exige revisão dessa decisão
e das configurações de cookies, CORS e proteção CSRF.

A sessão identifica o usuário autenticado. Perfil, situação ativa
e departamento utilizados para autorizar operações devem ser
obtidos do MySQL da aplicação, conforme o protocolo de cada operação.

Atributos eventualmente armazenados na sessão não substituem
a consulta à fonte vigente de autorização.

Na criação, a leitura dos atributos e a gravação do pedido devem
participar da coordenação definida em OPEN-004-03. A existência
de uma sessão autenticada não garante essa coordenação.

Manter proteção contra fixação de sessão e proteção CSRF,
incluindo os fluxos de login, logout e operações que alteram dados.

A integração deve definir HTTPS, atributos do cookie, expiração,
invalidação e comportamento do cliente diante de sessão encerrada.

Esta decisão seleciona o mecanismo inicial. Não representa
implementação de login, sessão ou portal.

#### Pendências para concluir a decisão

A diretriz não define ainda:

- Contratos de login, logout e obtenção do token CSRF.
- Atributos do cookie e armazenamento das sessões no servidor.
- Respostas da API para sessão ausente, expirada ou invalidada,
  incluindo revisão do tratamento de 401 e WWW-Authenticate
  para o mecanismo escolhido.
- Armazenamento seguro e verificação das credenciais.
- Criação inicial das contas, recuperação e alteração de credenciais.
- Expiração, encerramento e invalidação da autenticação.
- Integração entre o usuário autenticado e os contratos da aplicação.
- Tratamento de falhas e respostas de autenticação.
- Confirmação de eventuais exigências do professor.

A implementação de autenticação e persistência dos usuários exige
um recorte próprio, com critérios de aceite e testes. Não será
considerada concluída pela aprovação desta diretriz.

#### Requisitos da integração

Definir a integração responsável por autenticar o usuário
e fornecer sua identidade à aplicação.

A definição deve incluir:

- Mecanismo de autenticação e desafio WWW-Authenticate.
- Origem confiável de usuário, perfil, situação ativa e departamento.
- Tratamento de credenciais ausentes, inválidas ou expiradas.
- Distinção entre negativa de acesso e falha técnica do provedor.
- Composição das entradas autorizadas, sem exposição dos caminhos
  internos de compatibilidade.

Testes com identidades controladas podem validar o adaptador HTTP
em ambiente de teste, mas não autorizam declarar a interface
protegida para uso real.

### OPEN-004-03 — Coordenação e confirmação desconhecida

Definir a implementação confiável de
ExecucaoComIdentidadeCoordenada e seu alcance.

Todos os escritores dos atributos de autorização devem participar
do protocolo, inclusive em outros processos quando aplicável.

A definição deve tratar explicitamente a perda de comunicação
durante o commit. O término da chamada Java com confirmação
desconhecida não demonstra que a transação remota já terminou.

Antes de declarar a garantia de autorização durante a gravação,
demonstrar como o protocolo trata uma possível conclusão remota
após a liberação da coordenação local.

A fonte controlada utilizada na Issue #76 não comprova essa
garantia em cenários distribuídos ou de resultado remoto
ainda indeterminado.

#### Proposta de coordenação transacional no MySQL

Utilizar a mesma conexão e transação InnoDB para obter os atributos
de autorização, verificar o acesso e persistir a requisição.

A sessão fornece o identificador autenticado do usuário.
Perfil, situação ativa e departamento são recuperados do registro
persistente do usuário por uma leitura com bloqueio compartilhado,
proposta como SELECT ... FOR SHARE.

A consulta deve localizar o usuário por identificador único.
Usuário inexistente, inativo ou sem permissão impede a criação.

O bloqueio deve permanecer até o encerramento da transação.
Não liberar a proteção entre a autorização e a gravação.

Alterações de perfil, situação ativa e departamento devem atualizar
o mesmo registro protegido. Se esses atributos forem distribuídos
entre tabelas, o protocolo deverá proteger todos os registros
relevantes e definir uma ordem de aquisição dos bloqueios.

Nenhum caminho de alteração pode contornar esse protocolo.

#### Ordem entre criação e alteração da identidade

Se a criação obtiver primeiro o bloqueio, a alteração incompatível
aguarda o encerramento da transação.

Se a alteração for confirmada antes da leitura protegida,
a criação considera os novos atributos.

A mudança posterior não transfere autoria ou departamento
dos pedidos anteriormente confirmados.

#### Reconciliação após duplicidade

Após encerrar a tentativa que encontrou duplicidade, a reconciliação
abre uma nova transação e repete a leitura protegida do usuário.

A nova tentativa utiliza os atributos então vigentes e verifica
novamente o acesso ao pedido existente antes de comparar conteúdo
ou devolver dados.

Pode haver mudança de vínculo entre as duas transações.
Nesse caso, a reconciliação pode ser negada, mesmo que a primeira
tentativa tenha começado com uma identidade autorizada.

O contrato de coordenação atual deve ser revisado para expressar
essa garantia por tentativa transacional, sem prometer um bloqueio
contínuo entre conexões distintas.

#### Confirmação desconhecida

Uma falha de comunicação durante o commit não comprova que
a transação foi desfeita nem que seus bloqueios foram liberados.

A proposta depende da coordenação mantida pelo próprio InnoDB:
enquanto a transação remota estiver ativa, uma alteração conflitante
deve aguardar ou falhar por limite de espera.

Após a transação terminar, seus efeitos e a liberação dos bloqueios
devem respeitar a ordem transacional do banco.

A resposta ao cliente continua sendo de confirmação desconhecida.
A proteção do vínculo não permite afirmar qual foi o resultado
do commit quando sua confirmação não foi recebida.

#### Impacto na implementação existente

A implementação atual abre conexões dentro da criação JDBC.
A integração proposta exige reorganizar a propriedade da transação
para que leitura da identidade e gravação compartilhem a mesma
conexão.

Não basta envolver a criação existente com um bloqueio obtido
em outra conexão.

A solução deve preservar o isolamento do domínio em relação ao JDBC,
a autorização das repetições e os contratos de confirmação desconhecida.

O isolamento transacional e a combinação entre leituras com bloqueio
e consultas do pedido deverão ser definidos e verificados
antes da implementação.

#### Verificações necessárias

- Alteração de vínculo aguarda uma criação já protegida.
- Criação iniciada após uma alteração confirmada utiliza o novo vínculo.
- Desativação e mudança de perfil respeitam o mesmo protocolo.
- Reconciliação reavalia a identidade em sua nova transação.
- Falhas e rollback não deixam gravações parciais confirmadas.
- Perda da resposta do commit preserva a classificação de incerteza.
- A coordenação funciona entre conexões e processos distintos
  que utilizam a mesma fonte MySQL.
- Limites de espera e deadlocks encerram a tentativa com tratamento
  explícito, sem repetir automaticamente uma operação de resultado incerto.

Status: proposta técnica a revisar e verificar.
Não constitui garantia já implementada.

### OPEN-004-04 — Limitação de existência aceita no recorte acadêmico

Status: limitação aceita pelo responsável pelo projeto para
o primeiro recorte acadêmico, considerando o prazo de entrega
e a preservação dos contratos existentes.

Manter o UUID da requisição fornecido pelo cliente como identificador
estável para criação e repetição.

O POST pode produzir resultados distintos para UUID disponível
e UUID ocupado por pedido fora do escopo. Portanto, não garante
ocultação completa da existência de identificadores.

Essa limitação não autoriza devolver conteúdo, autoria, departamento,
itens ou valores de pedidos fora do escopo, nem modificar esses pedidos.

Toda tentativa continua sujeita à autenticação e à autorização.
UUIDs aleatórios não substituem essas verificações.

Na futura consulta HTTP por UUID, pedido inexistente e pedido
fora do escopo deverão receber a mesma resposta pública
para o Desenvolvedor.

A aceitação aplica-se ao recorte acadêmico. Uma implantação
com usuários ou dados reais exige reavaliar essa limitação
e os demais requisitos de segurança antes da exposição.

Não será introduzida nesta etapa uma chave de repetição separada
do identificador do pedido.

### OPEN-004-05 — Limites operacionais

Definir e justificar:

- Tamanho máximo do corpo HTTP.
- Número máximo de itens por requisição.
- Limite operacional de quantidade por item, caso seja inferior
  ao limite do tipo inteiro.
- Tamanho esperado do catálogo e viabilidade da resposta completa.
- Limites de espera e comportamento diante de dependências lentas.

Distinguir excesso de tamanho do corpo, tratado com 413,
de violações dos limites dos campos ou da coleção.

Os limites devem aparecer no contrato e nos testes.
Não escolher valores apenas por conveniência da implementação.

### OPEN-004-06 — Integração HTTP e classificação de falhas

Definir a versão do Spring Boot e sua compatibilidade com
o ambiente Java e Maven do projeto.

Planejar:

- Serialização e validação dos DTOs.
- Rejeição de propriedades desconhecidas e duplicadas.
- Validação estrita de UUIDs e quantidades.
- Mapeamento explícito de estados e motivos.
- Distinção entre entrada inválida, rejeição de negócio,
  indisponibilidade e falha interna.
- Preservação da confirmação desconhecida e dos avisos de sucesso.
- Ordem entre autenticação, leitura e validação do corpo.

Se os contratos atuais não permitirem distinguir essas falhas
com segurança, propor ajustes tipados na aplicação.
Não classificar erros por comparação de textos de exceções.

### OPEN-004-07 — Consulta persistente do catálogo

Definir o caso de uso autorizado, a porta de leitura e
a implementação JDBC necessários à listagem.

A implementação deve manter autorização na aplicação,
ordenar os resultados conforme o contrato e encerrar os
recursos de consulta.

Não considerar a busca atual por UUID equivalente à listagem.

## Estratégia de entrega

1. Revisar os detalhes dos contratos dentro do recorte funcional aprovado.
2. Registrar as decisões pendentes e suas dependências.
3. Separar a implementação em issues com critérios verificáveis.
4. Implementar e verificar a consulta autorizada do catálogo.
5. Integrar o adaptador HTTP em ambiente controlado de teste.
6. Validar a integração real de identidade e coordenação antes
   de disponibilizar operações protegidas para uso real.

A ordem poderá ser ajustada na revisão, preservando as dependências.
Nenhuma etapa deve ser declarada concluída apenas pela existência
de contratos ou testes de uma camada diferente.

## Situação e evidências

- Documento em elaboração na Issue #81.
- Autorização persistente integrada à main pelo PR #77.
- A Issue #76 registra 648 verificações distintas da base existente.
- Essas verificações não comprovam os contratos HTTP desta proposta.
- Nenhum endpoint ou teste HTTP foi implementado nesta etapa.
- Recorte funcional aprovado pelo responsável pelo projeto;
  decisão registrada em OPEN-004-01.
- Revisão dos detalhes do contrato e resolução ou encaminhamento
  das decisões OPEN-004-02 a OPEN-004-07 ainda necessários.

## Referências

- [Issue #81](https://github.com/Knzo25e/orquestrador-finops/issues/81).
- [PR #77](https://github.com/Knzo25e/orquestrador-finops/pull/77).
- [Requisitos do sistema](../spec.md).
- [Arquitetura e ADRs](../arquitetura.md).
- [Mapa de specs](../mapa-de-specs.md).
- [SPEC-002](SPEC-002-criar-requisicao.md).
- [SPEC-006](SPEC-006-perfis-acesso.md).
- [Evidências da Issue #76](../evidencias/issue-76/autorizacao-requisicoes.md).