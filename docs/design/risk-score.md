# Risk Score — Design Doc

Status: rascunho para refinamento
Demanda de origem: "implementar uma nova funcionalidade de risk score" (sem detalhamento adicional)

## Problema

Hoje o banco autoriza uma transação olhando apenas se o ativo está disponível; não existe nenhuma
noção de risco do cliente ou da operação.

- Problema: não há como diferenciar uma transação de baixo risco de uma suspeita antes de efetivá-la.
- Resultado esperado: um score de risco (0–100, ou faixa baixo/médio/alto) associado a cliente e/ou
  transação, disponível para consulta e utilizável como critério de decisão no fluxo de transação.

O escopo exato depende de respostas do negócio (ver "Perguntas em aberto"). Este doc assume o caso
mais provável: **score de risco por transação, calculado no momento da transação, com histórico consultável**.

## Contexto atual (verificado no código)

Sistema de microsserviços Spring Boot 3.0.6 / Java 17, módulos declarados em `pom.xml`
(`config-server`, `discovery-server`, `api-gateway`, `account-api`, `asset-management-api`,
`transaction-api`, `notification-api`).

Fluxo de transação:

- `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/controller/TransactionController.java`
  expõe `POST /api/transaction`, executa de forma assíncrona (`CompletableFuture`) e é protegido por
  `@CircuitBreaker`, `@TimeLimiter` e `@Retry` (Resilience4j) com `fallbackMethod`.
- `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java`
  gera um `transactionId` (UUID), mapeia os itens, chama `checkAssetAvailability(...)` e, **somente se
  todos os ativos estiverem disponíveis**, salva a transação e publica um evento em
  `notificationTopic` via `KafkaTemplate`. Caso contrário lança `IllegalArgumentException`.
  Esse é o único ponto de decisão existente no fluxo.
- A chamada síncrona para o serviço de ativos é feita por Feign em
  `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/client/AssetManagementClient.java`
  (`@FeignClient(name = "asset-management-api")`), padrão a ser copiado por qualquer nova integração síncrona.
- `asset-management-api/src/main/java/com/royal/reserve/bank/asset/management/api/service/AssetManagementService.java`
  apenas responde se `asset.getValue() > 0`.
- `notification-api/src/main/java/com/royal/reserve/bank/notification/api/NotificationApiApplication.java`
  consome `notificationTopic` com `@KafkaListener` e apenas loga o `transactionId`.

Modelo de dados — limitação central:

- `transaction-api/.../model/Transaction.java` tem apenas `id`, `transactionId` e
  `transactionItemsList`. **Não existe identificador de cliente ou de conta na transação.**
- `transaction-api/.../dto/TransactionRequest.java` recebe somente `transactionItemsDtoList`; o
  request atual não carrega nenhuma informação de quem está transacionando.
- `account-api/.../model/Account.java` (MongoDB) tem `id`, `accountNumber` (IBAN gerado
  aleatoriamente em `AccountService.generateIBAN()`), `accountHolderName`, `balance`, `currency` —
  sem documento, data de abertura, histórico ou qualquer atributo tipicamente usado em risco.
- `account-api/.../controller/AccountController.java` expõe apenas `POST`, `GET` (todas as contas) e
  `DELETE` por nome do titular. **Não há busca de conta por identificador**, então um serviço de risco
  não tem como consultar uma conta específica hoje.

Consequência: qualquer score "por cliente" exige antes ligar transação ↔ conta (campo novo no
request/modelo de transação) e expor uma consulta de conta por identificador em `account-api`.

Ingresso e configuração:

- Toda rota externa é declarada no gateway, em `config-files/api-gateway.properties`
  (`spring.cloud.gateway.routes[0..6]`, uma rota por API). A variante
  `config-files/api-gateway-docker.properties` só sobrescreve `server.port`, o Eureka e o Zipkin — as
  rotas ficam apenas no arquivo default. Propriedades novas de um serviço, porém, precisam ir para as
  duas variantes (`<serviço>.properties` e `<serviço>-docker.properties`), senão a stack em Docker quebra.
- Autenticação é feita no gateway como OAuth2 Resource Server contra JWKS do Auth0
  (`api-gateway/.../config/SecurityConfig.java`), com `anyExchange().authenticated()`. O JWT está
  versionado em `config-files/api-gateway.properties` (dívida conhecida, e já expirado).
- Serviços de negócio não são expostos diretamente; ver `docker-compose.yml` (um serviço + banco por
  API: mongo, mysql, postgres, além de `redis`, `notification-api-kafka`, `zipkin`, `prometheus`, `grafana`).

Dívidas que afetam este trabalho:

- Em `config-files/transaction-api.properties`, as instâncias Resilience4j configuradas se chamam
  `inventory` (`resilience4j.circuitbreaker.instances.inventory.*`, `timelimiter`, `retry`), mas as
  anotações no controller e no Feign client usam o nome `asset-management`. Ou seja, a configuração
  versionada **não se aplica** e valem os defaults. Ao adicionar uma nova integração resiliente, criar
  a instância com o nome realmente usado na anotação.
- Os módulos `config-server`, `discovery-server` e `api-gateway` só têm classes `*IT`, e o plugin
  Failsafe não está configurado no `pom.xml`: esses testes não rodam em `mvn test`.

## Premissas assumidas

1. Score é da **transação** (podendo agregar histórico do cliente), não um score de crédito regulatório.
2. O cálculo é baseado em dados internos (valor, itens, histórico, conta); sem bureau externo na fase 1.
3. Volume é baixo (sistema de demonstração), sem requisito de latência agressivo além do
   `TimeLimiter` do fluxo atual.
4. Nenhuma decisão automática de bloqueio na fase 1 além do que o negócio confirmar (ver A/B abaixo).

## Abordagens

### A. Score embutido no `transaction-api` (P)

Regra de score calculada dentro de `TransactionService.processTransaction`, persistida em novas
colunas/tabela no Postgres do próprio serviço, e devolvida na resposta da transação.

- Escopo: novo componente de cálculo em `transaction-api`, campos novos em `Transaction`/`TransactionRequest`,
  endpoint `GET /api/transaction/{transactionId}/risk-score`, rota já existente no gateway (mesmo path base).
- Componentes afetados: `transaction-api`, `config-files/transaction-api*.properties` (se houver parâmetros
  de regra), `config-files/api-gateway*.properties` só se um path novo for necessário.
- Esforço: P. Dependências: nenhuma nova.
- Confiança: **85**.

### B. Novo microsserviço `risk-score-api` chamado sincronamente na transação (M)

Serviço próprio, com banco próprio, chamado por `transaction-api` via Feign + Resilience4j (mesmo
padrão de `AssetManagementClient`), antes do `transactionRepository.save`.

- Escopo: módulo novo no `pom.xml`, banco próprio no `docker-compose.yml`, `config-files/risk-score-api.properties`
  e `-docker.properties`, rota nova em `config-files/api-gateway.properties`, `RiskScoreClient` em `transaction-api`,
  instância Resilience4j nomeada corretamente, e o campo de identificação de cliente/conta no request.
- Componentes afetados: `transaction-api`, `api-gateway` (config), `docker-compose.yml`, `pom.xml`, config-files.
- Esforço: M. Dependências: expor consulta de conta por identificador em `account-api` se o score usar
  dados da conta (hoje não existe — só `GET` de todas as contas).
- Confiança: **70**.

### C. Novo microsserviço `risk-score-api` alimentado por eventos Kafka (G)

`transaction-api` publica o evento da transação; o `risk-score-api` consome, calcula e guarda o score,
que é consultado depois por API. O fluxo de transação não é bloqueado.

- Escopo: tudo de B, mais um tópico/evento novo e enriquecimento do evento publicado
  (`TransactionEvent` hoje só carrega `transactionId` — ver `transaction-api/.../event/TransactionEvent.java`
  e a cópia em `notification-api/.../event/TransactionEvent.java`, que precisam continuar compatíveis;
  há `spring.json.type.mapping` apontando para as classes nas duas pontas).
- Esforço: G. Dependências: idem B; consistência eventual precisa ser aceita pelo negócio.
- Confiança: **55**.

## Trade-offs

| Critério | A (embutido) | B (serviço síncrono) | C (serviço por eventos) |
| --- | --- | --- | --- |
| Complexidade | Baixa | Média | Alta |
| Risco de quebrar o fluxo de transação | Médio (código no caminho crítico) | Médio (mitigado por circuit breaker/fallback) | Baixo (fora do caminho crítico) |
| Impacto em sistemas existentes | Só `transaction-api` | `transaction-api`, gateway, compose, pom | Idem B + contrato do evento Kafka compartilhado com `notification-api` |
| Reversibilidade | Alta (remover o componente) | Média (rota, módulo e banco a desfazer) | Baixa (evento versionado, dados já produzidos) |
| Bloqueia a transação em caso de risco alto | Sim, natural | Sim, natural | Não (score chega depois) |
| Evolução para modelo/bureau externo | Ruim (acopla risco ao core de transação) | Boa | Boa |

## Riscos

- **Modelo de dados insuficiente** (maior risco): sem identificação de cliente na transação e sem
  consulta de conta por id, qualquer score "por cliente" fica limitado a heurísticas sobre o valor da
  própria transação. Mudar `TransactionRequest` é mudança de contrato público na rota do gateway.
- **Caminho crítico**: `TransactionController` já opera com `TimeLimiter`; adicionar uma chamada
  síncrona (B) consome esse orçamento de tempo e cai no `fallbackMethod`, que hoje devolve sucesso
  textual genérico — precisa ser decidido se falha no scoring aprova ou recusa a transação (fail-open
  vs fail-closed). Hoje o fallback é efetivamente fail-open.
- **Configuração Resilience4j já divergente** (nomes `inventory` vs `asset-management`): repetir o
  padrão atual entrega um circuit breaker com defaults, não com o que está versionado.
- **Compliance / segurança** — precisa de validação com segurança e arquitetura:
  - score de risco é dado sensível: quem pode ler o endpoint? Hoje o gateway só distingue
    autenticado/não autenticado, sem escopos ou papéis (`SecurityConfig.java`).
  - explicabilidade e retenção da decisão (por que a transação foi recusada), exigência típica de
    auditoria antifraude/AML.
  - qualquer credencial de serviço novo não deve repetir o padrão atual de segredo versionado
    (`config-files/api-gateway.properties`, `docker-compose.yml`).
- **Observabilidade**: `prometheus-configuration.yml` tem um `job_name` por serviço existente; um
  módulo novo precisa de entrada lá para não nascer cego.

## Recomendação

Começar por **A** como fase 1: entrega o score visível e auditável sem criar módulo, banco e rota
novos, e cabe no fluxo já existente. A limitação real hoje é de dados, não de arquitetura —
enquanto a transação não carrega cliente/conta, um serviço dedicado (B) só adiciona infraestrutura
sem melhorar a qualidade do score. Migrar para **B** quando o score passar a depender de histórico do
cliente ou de fonte externa, aproveitando que o contrato do cálculo já estará isolado.

## Perguntas em aberto

1. Score de **quem**: cliente/conta, transação, ou ambos?
2. O score **bloqueia** a transação (recusa automática acima de um limite) ou é apenas informativo?
3. Quais variáveis o negócio quer no cálculo? Nenhum dado de histórico por cliente existe hoje.
4. Se o cálculo falhar, a transação deve ser aprovada ou recusada (fail-open vs fail-closed)?
5. Faixa e semântica do score (0–100? baixo/médio/alto?) e quem define os limites.
6. Quem pode consultar o score (cliente final no app, ou apenas operação interna)?
7. Há exigência regulatória/AML por trás da demanda, com requisitos de retenção e auditoria?
8. Podemos alterar o contrato de `POST /api/transaction` para incluir o identificador da conta?
