# Risk Score — design doc

## Problema

Hoje o banco conclui uma transação sem nenhuma avaliação de risco: a única
validação é se os ativos solicitados estão disponíveis. O resultado esperado é
ter um **risk score** — uma nota de risco calculada por transação — que permita
aprovar, sinalizar ou bloquear uma operação antes de ela ser persistida, com o
score registrado para auditoria.

Nota: a demanda chegou como "implementar uma funcionalidade de risk score" sem
indicar o caso de uso final (risco de fraude na transação vs. score de crédito
do cliente). Este documento assume **risco por transação**; a decisão está
listada em Perguntas em aberto e muda o escopo das abordagens.

## Contexto atual

Tudo abaixo foi verificado no código do repositório `diogotamura/royal-reserve-bank`
(commit `74e692b`).

- **Não existe nada de risco/score/fraude no código.** Uma busca por
  `risk|score|fraud` em todo o repositório não retorna nenhum arquivo de código.
- **O fluxo de transação não tem ponto de decisão de risco.**
  `TransactionService.processTransaction` (`transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java`)
  gera um `transactionId`, chama `checkAssetAvailability` e, se os ativos estão
  disponíveis, salva no Postgres e publica em `notificationTopic`. A única
  rejeição possível é `IllegalArgumentException("Asset is not available...")`.
- **O payload da transação não identifica cliente nem conta.**
  `TransactionRequest` (`.../transaction/api/dto/TransactionRequest.java`) contém
  apenas uma lista de `TransactionItemsDto`; `TransactionItems`
  (`.../transaction/api/model/TransactionItems.java`) tem `assetCode`,
  `assetName` e `value` (um `int`). A entidade `Transaction`
  (`.../transaction/api/model/Transaction.java`) guarda `id`, `transactionId` e os
  itens — não há conta de origem, destino, cliente, canal, dispositivo,
  geolocalização ou timestamp.
- **Não há histórico consultável de transações.** `TransactionRepository`
  (`.../transaction/api/repository/TransactionRepository.java`) é um
  `JpaRepository` sem nenhum método de consulta, e o `transaction-api` não expõe
  endpoint de leitura: `TransactionController`
  (`.../transaction/api/controller/TransactionController.java`) tem apenas um
  `POST /api/transaction`.
- **O cadastro do cliente é raso.** `Account`
  (`account-api/src/main/java/com/royal/reserve/bank/account/api/model/Account.java`)
  tem `accountNumber` (IBAN gerado aleatoriamente em `AccountService.generateIBAN`),
  `accountHolderName`, `balance` e `currency`. Não há CPF/documento, data de
  abertura, KYC ou histórico. `AccountRepository` é um `MongoRepository` sem
  consultas próprias, e `AccountController` expõe `POST`, `GET` (todas as contas)
  e `DELETE` por nome do titular.
- **Padrão de chamada síncrona entre serviços:** Feign + Resilience4j. Referência:
  `AssetManagementClient` (`.../transaction/api/client/AssetManagementClient.java`,
  `@FeignClient` + `@Retry`) consumido no controller com `@CircuitBreaker`,
  `@TimeLimiter` e `@Retry` e um `fallbackMethod` que devolve mensagem amigável.
  Atenção: as anotações usam a instância `asset-management`, mas
  `config-files/transaction-api.properties` só configura a instância `inventory`
  (`resilience4j.circuitbreaker.instances.inventory.*`, linhas 16-28) — ou seja,
  o circuit breaker atual roda com os defaults do Resilience4j. Copiar esse
  padrão sem criar a instância correspondente repete o problema.
- **Assíncrono é Kafka.** O `transaction-api` produz `TransactionEvent` (apenas
  `transactionId`) e o `notification-api` consome em
  `NotificationApiApplication.handleNotification` — que hoje só faz `log.info`.
- **Rotas externas passam pelo gateway.** As rotas estão declaradas em
  `config-files/api-gateway.properties` (`spring.cloud.gateway.routes[0..6]`,
  hoje `account-api`, `transaction-api`, `asset-management-api`, discovery e
  config server), com validação de JWT via JWKS do Auth0.
- **Um serviço novo custa infra.** Módulos são declarados em `pom.xml`
  (`<modules>`), cada serviço tem seu próprio banco no `docker-compose.yml`
  (Mongo, Postgres, MySQL, Redis, Kafka) e configuração duplicada em
  `config-files/<serviço>.properties` e `config-files/<serviço>-docker.properties`.

## Premissas

1. O score é calculado de forma **síncrona no caminho da transação**, antes da
   persistência (se for assíncrono, o desenho muda).
2. Regras determinísticas são suficientes na primeira entrega; não há modelo de
   ML treinado nem base histórica de fraude disponível.
3. O score precisa ser persistido e auditável (requisito típico de compliance
   bancário), não apenas calculado em memória.
4. Não há motor de decisão ou bureau externo já contratado.

## Abordagens

### A. Regras de risco dentro do `transaction-api` (P)

Um `RiskScoreService` no próprio `transaction-api` calcula o score a partir dos
dados da transação e de um enriquecimento do `account-api`, e
`processTransaction` passa a decidir aprovar/sinalizar/bloquear. O score é
gravado em colunas novas na entidade `Transaction` (o `ddl-auto=update` de
`config-files/transaction-api.properties` cria o schema).

- Componentes: `transaction-api` (service, model, DTOs, controller), Postgres do
  transaction-api, `config-files/transaction-api*.properties`.
- Dependências: enriquecer `TransactionRequest` com identificação da conta;
  criar no `account-api` um endpoint de consulta por identificador — hoje
  `AccountController` só expõe `GET /api/account` retornando todas as contas —
  com DTO de resposta próprio; e um Feign client novo no `transaction-api`
  seguindo o padrão do `AssetManagementClient`, com uma instância Resilience4j
  de fato configurada.
- Esforço: **P**.
- Confiança: **85** — usa exatamente os padrões já existentes no serviço; o
  risco residual é de produto (as regras estarem certas), não técnico.

### B. Novo microserviço `risk-api` consultado via Feign (M)

Serviço dedicado, com banco próprio de scores e de configuração de regras,
exposto em `/api/risk` pelo gateway e consultado pelo `transaction-api` via
Feign com circuit breaker. O `transaction-api` só decide com base na resposta.

- Componentes: módulo novo no `pom.xml`, banco novo no `docker-compose.yml`,
  dois arquivos novos em `config-files/`, rota nova em
  `config-files/api-gateway.properties`, client + fallback no `transaction-api`.
- Dependências: definir o comportamento quando o `risk-api` está fora
  (fail-open vs. fail-closed) — o padrão atual do `fallbackMethod` é fail-open
  com mensagem amigável, o que é inaceitável para risco.
- Esforço: **M**.
- Confiança: **70** — arquiteturalmente é o caminho certo e replicável, mas
  adiciona latência no caminho crítico da transação, um serviço a operar e a
  decisão de fail-open/fail-closed, que precisa de validação com arquitetura.

### C. Scoring assíncrono por evento Kafka (M)

A transação é persistida como hoje e o `TransactionEvent` (hoje só com
`transactionId`) é enriquecido; um consumidor novo calcula o score depois e
marca a transação para revisão, notificando por Kafka.

- Componentes: `transaction-api` (evento), consumidor novo (módulo próprio ou
  dentro do `notification-api`), Kafka, banco de scores.
- Dependências: o `transaction-api` precisa de um endpoint/consumidor para
  receber o resultado do score; hoje não existe leitura nem atualização de
  transação.
- Esforço: **M**.
- Confiança: **45** — tecnicamente viável e alinhado ao Kafka já em uso, mas
  **não bloqueia** a operação: a transação já foi concluída quando o score sai.
  Só serve se o negócio aceitar detecção pós-fato.

## Trade-offs

| | Complexidade | Risco | Impacto em sistemas existentes | Reversibilidade |
|---|---|---|---|---|
| A. Regras no `transaction-api` | Baixa | Baixo; engrossa um serviço que já concentra a orquestração | Só `transaction-api` + seu Postgres | Alta — remover o service e as colunas |
| B. `risk-api` dedicado | Alta | Latência e novo ponto de falha no caminho da transação | Gateway, `pom.xml`, docker-compose, `config-files`, `transaction-api` | Média — desligar a rota e o client |
| C. Scoring assíncrono | Média | Alto para o negócio: não impede a transação | `transaction-api` (evento) + consumidor novo | Alta — parar o consumidor |

## Riscos

- **Dados disponíveis limitam os fatores possíveis.** Regras puramente
  intrínsecas à operação (valor do item, soma dos itens, `assetCode` em lista
  restritiva) já são calculáveis com o que `TransactionRequest` e
  `TransactionItems` carregam hoje. O que **não** é possível sem mudar o modelo
  é qualquer fator ligado ao cliente ou ao comportamento — velocidade de
  transações, desvio do padrão histórico, reincidência, exposição por conta —
  porque não há conta, cliente, canal nem timestamp no payload e não há
  histórico consultável (`TransactionRepository` sem queries, sem endpoint de
  leitura). Se a resposta à pergunta 3 (fatores do score) incluir qualquer fator
  comportamental, ampliar o contrato e o modelo vira pré-requisito das três
  abordagens.
- **Isolamento de dados.** O score precisa de dados de conta, que vivem no Mongo
  do `account-api`; o acesso tem de ser via API, nunca ao banco do outro serviço.
- **Compliance / explicabilidade.** Decisão automatizada que bloqueia operação
  costuma exigir motivo registrado, retenção e trilha de auditoria — validar com
  segurança/compliance antes de escolher o formato do score.
- **Fail-open vs. fail-closed.** O `fallbackMethod` atual do
  `TransactionController` devolve sucesso mascarado em caso de falha. Para risco,
  o comportamento precisa ser decidido explicitamente com arquitetura.
- **Segurança de configuração.** `config-files/api-gateway.properties` versiona
  um JWT e o `docker-compose.yml` traz credenciais fixas; qualquer configuração
  nova do risk score deve ir para variável de ambiente, não repetir o padrão.
- **Cache.** `checkAssetAvailability` é `@Cacheable("assetAvailability")`;
  decisão de risco não deve ser cacheada por engano no mesmo estilo.

## Recomendação

Começar pela **abordagem A**. Se os fatores do score forem comportamentais (o
caso mais provável em risco de fraude), ela precisa ser precedida de um passo de
ampliar o contrato e o modelo da transação (conta/cliente, valor, timestamp);
com fatores puramente intrínsecos à operação, esse passo não é necessário. A é a
que entrega decisão
síncrona no ponto onde a transação é aprovada, sem criar serviço, banco, rota e
ponto de falha novos. Se o volume de regras crescer ou o score passar a ser
consumido por outros canais, o `RiskScoreService` extrai-se depois para o
`risk-api` da abordagem B com o mesmo contrato.

## Perguntas em aberto

1. O score é **risco de fraude na transação** ou **score de crédito do cliente**?
   Isso decide o desenho inteiro.
2. O score **bloqueia** a operação, apenas **sinaliza** para revisão, ou os dois
   (faixas de decisão)?
3. Quais fatores compõem o score e quem é o dono das regras?
4. O score deve ser consumido por outros canais além da transação (app,
   backoffice, relatórios)?
5. Existe bureau externo ou motor de decisão contratado a integrar?
6. Se o cálculo de risco falhar, a transação passa ou é recusada?
7. Há exigência de retenção/auditoria das decisões e por quanto tempo?
8. Existe base histórica de fraude confirmada (necessária se o objetivo final é
   modelo estatístico)?
