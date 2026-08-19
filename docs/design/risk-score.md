# Risk Score — design doc

Status: rascunho para refinamento. Não há decisão de implementação tomada.

## Problema

**Problema:** hoje a plataforma aprova ou recusa uma transação apenas verificando se o ativo
envolvido tem valor maior que zero (`asset-management-api/src/main/java/com/royal/reserve/bank/asset/management/api/service/AssetManagementService.java`,
método `isAssetAvailable`), sem nenhuma avaliação de risco da operação ou do titular.

**Resultado esperado:** cada operação (ou cada titular) passa a ter um *risk score* calculável e
consultável, que o negócio possa usar para aprovar, recusar ou enviar para revisão manual.

> A demanda chegou como "implementar uma funcionalidade de risk score", sem definição de escopo.
> Este documento assume **risco de crédito/comportamento por conta e por transação**; ver
> "Perguntas em aberto" — a escolha entre risco de crédito e risco de fraude/AML muda a abordagem
> recomendada.

## Contexto atual (verificado no código)

| Componente | Papel hoje | Arquivo |
| --- | --- | --- |
| `transaction-api` | Único ponto onde uma operação é aprovada. `processTransaction` monta a transação, chama disponibilidade de ativo e, se positivo, salva e publica em Kafka | `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java` |
| `transaction-api` (entrada) | `POST /api/transaction`, assíncrono, com `@CircuitBreaker`/`@TimeLimiter`/`@Retry` e fallback | `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/controller/TransactionController.java` |
| `asset-management-api` | Regra de disponibilidade: `asset.getValue() > 0` | `asset-management-api/.../service/AssetManagementService.java` |
| `account-api` | CRUD de conta em MongoDB | `account-api/src/main/java/com/royal/reserve/bank/account/api/service/AccountService.java` |
| `notification-api` | Consumidor Kafka de `notificationTopic`; hoje apenas escreve log | `notification-api/src/main/java/com/royal/reserve/bank/notification/api/NotificationApiApplication.java` |
| `api-gateway` | Toda rota externa; rotas declaradas por índice | `config-files/api-gateway.properties` (linhas 10-43) |
| Comunicação síncrona | Feign + Resilience4j (`asset-management-api`) | `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/client/AssetManagementClient.java` |

**Limitações de dado que condicionam qualquer solução:**

- `Account` tem apenas `id`, `accountNumber`, `accountHolderName`, `balance`, `currency`
  (`account-api/src/main/java/com/royal/reserve/bank/account/api/model/Account.java`). Não existe
  documento/CPF, data de nascimento, renda, endereço nem qualquer dado de KYC.
- `Transaction` guarda somente `transactionId` e a lista de itens; `TransactionItems` tem
  `assetCode`, `assetName`, `value` (int) — **não existe conta de origem/destino, valor monetário
  da operação nem data/hora**
  (`transaction-api/.../model/Transaction.java`, `transaction-api/.../model/TransactionItems.java`).
- `TransactionRepository` é um `JpaRepository` sem nenhuma query própria
  (`transaction-api/.../repository/TransactionRepository.java`), portanto não há como consultar
  histórico por cliente hoje.

**Consequência direta:** não há como ligar uma transação a um titular no modelo atual. Qualquer
score que dependa de histórico do cliente exige, antes, um pré-requisito de modelagem:
adicionar identificação da conta, valor monetário e timestamp à transação.

## Abordagens

### A. Regras de risco dentro do `transaction-api` (score por operação)

Score calculado no próprio fluxo de `processTransaction`, com regras determinísticas sobre o que já
existe no payload (valor total dos itens, quantidade de itens, faixas configuráveis) e persistido na
própria transação.

- Escopo: novos campos em `Transaction`, componente de regras no `transaction-api`, thresholds em
  `config-files/transaction-api.properties` e `-docker.properties`.
- Componentes afetados: `transaction-api` (PostgreSQL).
- Esforço: **P**
- Dependências: nenhuma nova.
- Confiança: **85** — pouca peça móvel e nenhuma integração nova; o risco é de valor, não técnico:
  sem dado de cliente o score é fraco.

### B. Novo microserviço `risk-api` consultado de forma síncrona antes de aprovar

Módulo Maven novo com banco próprio, expondo `POST /api/risk-score`. O `transaction-api` chama via
Feign com Resilience4j (mesmo padrão do `AssetManagementClient`) antes de salvar; a decisão do score
entra como segunda condição ao lado de `assetIsAvailable`. O `risk-api` mantém histórico de scores e
consome dados de conta pelo `account-api` (também via Feign), respeitando database-per-service.

- Escopo: módulo novo em `pom.xml` (`<modules>`), entrada em `docker-compose.yml`, rota no
  `api-gateway` (`config-files/api-gateway.properties`, próximo índice livre `routes[7]` — e a
  variante `-docker`), cliente Feign no `transaction-api`, política de fallback.
- Componentes afetados: `transaction-api`, `api-gateway`, `config-server`/`config-files`,
  `docker-compose`, novo `risk-api`.
- Esforço: **G**
- Dependências: definição do banco do novo serviço; pré-requisito de modelagem (conta de origem e
  valor na transação) para o score valer algo.
- Confiança: **70** — arquitetura conhecida e replicável, mas é o caminho com mais superfície nova
  (config, gateway, compose, imagem) e o que mais depende do pré-requisito de dados. Ponto de atenção
  técnico: latência no caminho crítico com `resilience4j.timelimiter...timeout-duration=3s`
  (`config-files/transaction-api.properties`).

### C. Scoring assíncrono via Kafka (fora do caminho crítico)

O `transaction-api` continua aprovando como hoje; o score é calculado por um consumidor do evento
publicado em `notificationTopic` (hoje só o `notification-api` consome, e apenas loga). O score fica
disponível para consulta e para alertas, mas não bloqueia a operação.

- Escopo: enriquecer `TransactionEvent` (`transaction-api/.../event/TransactionEvent.java` e a cópia
  em `notification-api/.../event/TransactionEvent.java` — hoje só carrega `transactionId`), consumidor
  de scoring, armazenamento do score.
- Componentes afetados: `transaction-api` (produtor), novo consumidor, `notification-api` (contrato do
  evento compartilhado).
- Esforço: **M**
- Dependências: contrato do evento precisa ser evoluído nas duas cópias em conjunto.
- Confiança: **75** — baixo risco operacional e nenhum impacto em latência; a confiança não é maior
  porque o `TransactionEvent` é duplicado entre módulos e mudança de contrato mal coordenada quebra o
  consumidor.

## Trade-offs

| | A. Regras no transaction-api | B. `risk-api` síncrono | C. Scoring assíncrono |
| --- | --- | --- | --- |
| Complexidade | Baixa | Alta | Média |
| Risco operacional | Baixo | Alto — entra no caminho crítico de aprovação | Baixo — não bloqueia |
| Impacto em sistemas existentes | Só `transaction-api` | `transaction-api`, gateway, config, compose | Contrato do evento + `notification-api` |
| Reversibilidade | Alta — feature flag/threshold | Baixa — serviço, banco e rota novos | Média — consumidor pode ser desligado |
| Bloqueia transação de risco alto | Sim | Sim | Não |
| Evolui para modelo/motor externo | Não | Sim | Sim |

## Riscos

**Técnicos**

- Score no caminho síncrono (B) some com o `timeout-duration=3s` e cai no `fallbackMethod` do
  `TransactionController`, que hoje devolve mensagem genérica de sucesso-parcial — precisa definir se
  falha de score aprova (fail-open) ou recusa (fail-closed).
- `spring.jpa.hibernate.ddl-auto=update` (`config-files/transaction-api.properties`) faz o schema
  evoluir sozinho; novos campos de score entram sem migração controlada — inaceitável para dado
  auditável e precisa ser decidido com arquitetura.
- Qualquer configuração nova tem que ir para as duas variantes (`<serviço>.properties` e
  `<serviço>-docker.properties`), sob pena de quebrar a stack em Docker.
- Cache: `checkAssetAvailability` é `@Cacheable`; score **não** deve ser cacheado da mesma forma sem
  política de expiração explícita.

**Compliance / segurança — exige validação com segurança e arquitetura**

- Score de crédito ou de fraude é decisão automatizada sobre pessoa: LGPD exige base legal,
  rastreabilidade da decisão e possibilidade de revisão. Nada disso existe hoje.
- Introduzir dado pessoal sensível (documento, renda) no `account-api` amplia o escopo de dados
  pessoais do sistema; o `docker-compose.yml` ainda usa credenciais fixas de banco e o
  `config-files/api-gateway.properties` versiona um JWT — dívida conhecida que deve ser tratada antes
  de armazenar dado sensível.
- Se houver bureau externo (Serasa/Boa Vista) a decisão passa a depender de terceiro: contrato,
  latência e retenção de dados precisam de aprovação.

## Recomendação

Começar por **A** (regras de risco no `transaction-api`, com threshold configurável e score
persistido), tratando **B** como evolução natural quando houver motor de risco de verdade. O sistema
hoje não tem identidade de cliente nem valor/data na transação, então investir num `risk-api`
completo antes desse pré-requisito de modelagem produziria um serviço sofisticado pontuando dados que
não existem. A alternativa **C** é a segunda escolha se o negócio aceitar score apenas informativo.

**Pré-requisito para qualquer abordagem:** adicionar conta de origem, valor monetário e timestamp à
`Transaction` — sem isso não há como escorar cliente nem histórico.

## Perguntas em aberto

1. Risk score de **crédito** (capacidade de pagamento do titular) ou de **fraude/AML** (transação
   suspeita)? A resposta muda modelo de dados e abordagem.
2. O score **bloqueia** a transação, ou é apenas informativo/consultável?
3. Qual a granularidade: score por transação, por conta, ou os dois?
4. Fonte dos dados: só o que existe internamente, ou entra bureau externo?
5. Regras determinísticas definidas pelo negócio ou modelo estatístico/ML?
6. Quem consome o score: apenas o motor de decisão interno, um analista via API, ou o cliente final?
7. Há exigência regulatória de auditar e explicar cada decisão de score (retenção, trilha)?
8. Existe apetite para alterar o modelo de `Transaction` e `Account` neste ciclo (pré-requisito acima)?
