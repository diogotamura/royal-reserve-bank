# Design doc — Risk score

Status: rascunho para refinamento · Repositório: `diogotamura/royal-reserve-bank`

## Problema

**Problema:** hoje uma transação é aceita ou recusada apenas por disponibilidade do ativo, sem
nenhuma avaliação de risco do cliente ou da operação.

**Resultado esperado:** cada transação (ou cliente) recebe um *risk score* que o banco possa usar
para aprovar, recusar ou encaminhar para revisão manual, com o score consultável e auditável.

## Contexto atual (verificado no código)

- A decisão de aceitar uma transação está em `TransactionService.processTransaction`
  (`transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java`):
  monta a transação, consulta disponibilidade dos ativos e, se disponível, salva e publica evento
  Kafka em `notificationTopic`; senão lança `IllegalArgumentException`. Não há qualquer regra de risco.
- A entrada externa é `POST /api/transaction`, exposta por `TransactionController`
  (`transaction-api/.../controller/TransactionController.java`), com `@CircuitBreaker`, `@TimeLimiter`
  e `@Retry` do Resilience4j (instância `asset-management`) e `fallbackMethod` de degradação.
- **Não existe identificação de cliente na transação.** `TransactionRequest`
  (`transaction-api/.../dto/TransactionRequest.java`) contém apenas `transactionItemsDtoList`, e
  `Transaction` (`transaction-api/.../model/Transaction.java`) guarda apenas `id`, `transactionId`
  (UUID gerado no serviço) e a lista de itens. `TransactionItems` tem `assetCode`, `assetName` e
  `value` (`int`).
- Contas vivem no `account-api` em MongoDB: `Account`
  (`account-api/.../model/Account.java`) tem `accountNumber` (IBAN gerado aleatoriamente em
  `AccountService.generateIBAN`), `accountHolderName`, `balance`, `currency`. A exclusão é feita por
  nome do titular (`AccountService.deleteAccountByAccountHolderName`), ou seja, não há CPF/ID de
  cliente estável no modelo.
- Comunicação síncrona entre serviços é por Feign + Resilience4j — referência:
  `transaction-api/.../client/AssetManagementClient.java` (`@FeignClient(name = "asset-management-api")`).
- Notificação é assíncrona por Kafka: produtor em `TransactionService`, consumidor em
  `NotificationApiApplication.handleNotification` (`@KafkaListener(topics = "notificationTopic")`).
- As variantes `*-docker.properties` em `config-files/` não duplicam o arquivo base: elas apenas
  sobrescrevem hosts (ex.: `config-files/transaction-api-docker.properties` troca datasource, Kafka,
  Redis e Eureka para nomes de container). Configuração nova de negócio vai no arquivo base, e o que
  aponta para infraestrutura precisa da variante `-docker` correspondente.
- Persistência é database-per-service: `account-api` MongoDB (`config-files/account-api.properties`),
  `transaction-api` PostgreSQL (`config-files/transaction-api.properties`), `asset-management-api`
  MySQL (`config-files/asset-management-api.properties`).
- Toda rota externa passa pelo gateway, declarada por índice em
  `config-files/api-gateway.properties` (`spring.cloud.gateway.routes[0..6]`, hoje até
  `asset-management-api` no índice 6), com autenticação JWT/JWKS Auth0 em
  `api-gateway/.../config/SecurityConfig.java` (`anyExchange().authenticated()`).
- Nenhum serviço de negócio valida token: só o gateway autentica; os serviços internos não recebem
  identidade do usuário (o `jwtFilter` do `SecurityConfig` guarda o token em campo da própria
  config e não o propaga adiante).
- Cada serviço tem seu módulo no `pom.xml` raiz (`modules`) e imagem via Jib, com serviço
  correspondente no `docker-compose.yml`.

**Dívida relevante encontrada:** as propriedades de Resilience4j em
`config-files/transaction-api.properties` usam a instância `inventory`
(`resilience4j.circuitbreaker.instances.inventory.*`), enquanto as anotações do código usam
`asset-management` — a configuração versionada não se aplica ao circuit breaker real. Repetir esse
padrão numa chamada de risco deixaria o novo circuit breaker com defaults silenciosos.

## Premissas assumidas

1. "Risk score" aqui é **risco da transação** (fraude/AML), avaliado no momento da transação. Se a
   demanda for score de crédito do cliente (limite/aprovação), o desenho muda: passa a depender de
   histórico e bureau externo, não do fluxo de transação.
2. A v1 pode ser baseada em regras determinísticas (valor, ativo, velocidade), sem modelo de ML.
3. O score precisa ser persistido e consultável para auditoria.
4. Não há hoje motor de decisão ou bureau externo contratado.

## Perguntas em aberto

1. Score de **fraude por transação** ou de **crédito por cliente**? (define tudo o resto)
2. O score **bloqueia** a transação (decisão síncrona) ou apenas classifica para revisão posterior?
3. Quem é o "cliente" no sistema? Hoje não existe ID de cliente estável nem vínculo transação→conta;
   sem isso, qualquer score é calculado sobre a transação isolada, sem histórico.
4. Existe fonte externa de risco (bureau, lista restritiva, PEP) a integrar? Há contrato/latência
   acordada?
5. Requisitos de compliance: retenção do score, explicabilidade da decisão, trilha de auditoria.
6. Volume esperado de transações por segundo (define se dá para calcular no caminho síncrono).

## Pré-requisito comum a todas as abordagens

Incluir identificação de conta/cliente em `TransactionRequest` e `Transaction` e persistir esse
vínculo. Sem isso não há sujeito de risco: não existe histórico por cliente nem regra de velocidade.
Esforço: **P**, mas é mudança de contrato da API pública `/api/transaction`.

## Abordagens

### A — Regras de risco dentro do `transaction-api`

Um componente novo no próprio `transaction-api` calcula o score antes do `save`, com regras sobre os
dados já disponíveis (valor total dos itens, ativo, limites por faixa) e grava o score na tabela de
transação (`t_transaction`, JPA com `ddl-auto=update`).

- Escopo: `TransactionService`, `Transaction`, `TransactionRequest`/`TransactionItemsDto`,
  configuração de limites em `config-files/transaction-api.properties`.
- Componentes afetados: `transaction-api` apenas.
- Esforço: **P**
- Dependências: pré-requisito de identificação do cliente para regras além de "valor da operação".
- Confidence: **85**

### B — Novo microserviço `risk-api` consultado síncronamente

Serviço novo (módulo Maven + serviço no `docker-compose.yml` + rota no gateway + banco próprio),
consultado pelo `transaction-api` via Feign com circuit breaker e fallback, no mesmo padrão de
`AssetManagementClient`. O `risk-api` guarda score, features e decisão, e expõe consulta por
transação/cliente.

- Escopo: novo módulo em `pom.xml`, `config-files/risk-api.properties` + variante `-docker`,
  nova rota `spring.cloud.gateway.routes[7]` em `config-files/api-gateway.properties`,
  novo `RiskClient` + instância Resilience4j no `transaction-api`, novo banco no compose.
- Componentes afetados: `transaction-api`, `api-gateway` (config), `config-server` (arquivos),
  infraestrutura Docker, Eureka.
- Esforço: **M/G**
- Dependências: decisão sobre política de fallback (fail-open x fail-closed) quando o `risk-api`
  estiver indisponível — é decisão de negócio/compliance, não técnica.
- Confidence: **70**

### C — `risk-api` assíncrono por Kafka (scoring pós-transação)

O `transaction-api` continua decidindo só por disponibilidade e o `risk-api` consome eventos de
transação do Kafka (mesmo padrão do `notification-api`), calcula o score fora do caminho crítico e
o disponibiliza para consulta/fila de revisão manual.

- Escopo: novo módulo `risk-api` consumidor Kafka; enriquecer `TransactionEvent`
  (`transaction-api/.../event/TransactionEvent.java` hoje só transporta `transactionId`, e o
  contrato é acoplado por `spring.json.type.mapping` nas properties dos dois lados); nenhuma
  mudança no fluxo de decisão.
- Componentes afetados: `transaction-api` (evento), novo `risk-api`, Kafka, gateway (rota de
  consulta).
- Esforço: **M**
- Dependências: aceitar que a transação já foi efetivada quando o score sai.
- Confidence: **80**

## Trade-offs

| Critério | A — regras no transaction-api | B — risk-api síncrono | C — risk-api assíncrono |
|---|---|---|---|
| Complexidade | Baixa | Alta (novo serviço, banco, rota, resiliência) | Média |
| Risco operacional | Baixo | Alto: entra na latência e na disponibilidade de `POST /api/transaction` | Baixo: fora do caminho crítico |
| Impacto em sistemas existentes | Só `transaction-api` | `transaction-api`, gateway, config, compose, Eureka | `transaction-api` (evento) + gateway |
| Bloqueia transação de risco alto | Sim | Sim | Não (só detecta depois) |
| Evolução para ML / regras complexas | Ruim: regra acoplada ao core transacional | Boa: domínio isolado e versionável | Boa |
| Reversibilidade | Alta (feature flag e remoção simples) | Média: contrato entre serviços já publicado | Alta: desligar o consumidor |

## Riscos

- **Técnico — latência e disponibilidade:** em B, o `POST /api/transaction` passa a depender de mais
  um serviço; o `fallbackMethod` atual de `TransactionController` responde sucesso textual genérico,
  então um fallback mal desenhado pode aprovar transação sem avaliação de risco.
- **Técnico — configuração de resiliência:** as properties de Resilience4j versionadas apontam para
  a instância `inventory`, divergente das anotações (`asset-management`); um novo circuit breaker
  precisa de nome coerente entre anotação e properties, sob pena de rodar com defaults.
- **Técnico — ausência de sujeito de risco:** sem ID de cliente/conta na transação, regras de
  velocidade e histórico são impossíveis; qualquer entrega antes disso é score de transação isolada.
- **Técnico — contrato Kafka:** em C, o mapeamento de tipos por properties nos dois lados
  (`spring.kafka.*.properties.spring.json.type.mapping`) torna a evolução do evento sensível a
  deploy fora de ordem.
- **Compliance — decisão automatizada:** score que recusa operação exige explicabilidade, retenção e
  trilha de auditoria; precisa de validação de compliance/jurídico.
- **Compliance — dado sensível:** se entrarem dados pessoais ou de bureau, é preciso definir base
  legal, retenção e mascaramento; validar com segurança.
- **Segurança/arquitetura:** serviços internos não autenticam requisições (só o gateway); um
  `risk-api` com dados de risco precisa de decisão explícita de arquitetura sobre isso, e a
  configuração do gateway hoje versiona JWT no repositório (`config-files/api-gateway.properties`),
  padrão que não deve ser repetido no novo serviço.

## Recomendação

Fazer **C** e evoluir para **B**: criar o `risk-api` já como serviço próprio, mas consumindo eventos
de transação, para ganhar o domínio de risco isolado sem colocar um novo ponto de falha dentro de
`POST /api/transaction`. Assim que as regras estiverem calibradas em produção em modo observação,
a mesma API passa a ser consultada síncronamente pelo `transaction-api`, com política de fallback
aprovada por compliance. Antes de qualquer uma das duas, entregar o pré-requisito de identificação
de conta/cliente na transação — sem ele o score não tem sujeito e não mede risco de ninguém.

## Perguntas que travam o início

As de número 1, 2 e 3 acima precisam de resposta do negócio antes do refinamento técnico.
