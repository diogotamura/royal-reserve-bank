# Risk score de transações — design doc

Status: proposta para refinamento · Repositório: `diogotamura/royal-reserve-bank`

## Problema

**Problema:** hoje uma transação é aceita ou recusada apenas por disponibilidade de ativo, sem nenhuma avaliação de risco do cliente ou do comportamento da operação.

**Resultado esperado:** o banco passa a calcular um *risk score* por transação (e/ou por cliente) e usa esse score para aprovar, recusar ou marcar a operação para revisão, com o motivo registrado e auditável.

## Contexto atual (verificado no código)

- A decisão de aceitar uma transação está em `TransactionService.processTransaction` (transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java): monta a `Transaction`, chama `checkAssetAvailability` e, se todos os ativos estão disponíveis, salva no Postgres e publica em `notificationTopic`. Se algum ativo não está disponível, lança `IllegalArgumentException`. **Não existe hoje nenhuma outra regra de decisão.**
- O endpoint público é `POST /api/transaction` em `TransactionController`, envolvido por `@CircuitBreaker`, `@TimeLimiter` e `@Retry` (instância `asset-management`), com `fallbackMethod` que devolve mensagem genérica de erro.
- O modelo de transação é mínimo: `Transaction` tem `id`, `transactionId` (UUID) e a lista de itens; `TransactionItems` tem `assetCode`, `assetName` e `value` (`int`). **Não existe identificador de cliente/conta na transação, nem valor total, nem data/hora, nem canal, nem país.** Ver `transaction-api/.../model/Transaction.java` e `model/TransactionItems.java`.
- O payload de entrada também não tem cliente: `TransactionRequest` só carrega `transactionItemsDtoList` (`transaction-api/.../dto/TransactionRequest.java`).
- A conta vive em outro serviço e outro banco: `Account` (MongoDB) tem `id`, `accountNumber` (IBAN gerado aleatoriamente em `AccountService.generateIBAN`), `accountHolderName`, `balance` e `currency`. Não há CPF/documento, data de abertura, histórico ou score. Ver `account-api/.../model/Account.java` e `account-api/.../service/AccountService.java`.
- O `AccountController` expõe apenas `POST`, `GET` (todas as contas) e `DELETE` por nome do titular. **Não existe consulta de conta por número/IBAN**, o que hoje impede enriquecer uma transação com dados da conta sem varrer a lista inteira.
- Comunicação síncrona entre serviços é feita por Feign com Resilience4j — referência: `AssetManagementClient` (`@FeignClient(name = "asset-management-api")`, `@Retry(name = "asset-management")`).
- Notificação é assíncrona por Kafka: `TransactionEvent` (só `transactionId`) é consumido em `NotificationApiApplication.handleNotification`.
- Toda rota externa passa pelo gateway: `config-files/api-gateway.properties` define as rotas `spring.cloud.gateway.routes[0..6]`; um serviço novo precisa de um novo índice de rota nos dois arquivos (`api-gateway.properties` e `api-gateway-docker.properties`).
- Bancos por serviço: transaction-api usa Postgres com `spring.jpa.hibernate.ddl-auto=update` (`config-files/transaction-api.properties`), account-api usa MongoDB, asset-management-api usa MySQL.

**Consequência importante:** não existe hoje nenhum vínculo entre transação e cliente no código. Qualquer risk score que dependa de histórico do cliente exige, primeiro, introduzir esse vínculo (identificador de conta/cliente no `TransactionRequest` e na entidade `Transaction`). Isso é pré-requisito das três abordagens.

## Premissas assumidas

1. O score é usado na decisão da transação (não é só um relatório).
2. Existe apetite para mudar o contrato de `POST /api/transaction` para incluir o identificador da conta/cliente.
3. As regras iniciais são determinísticas (limites de valor, velocidade de transações, listas restritivas), não modelo de machine learning.
4. Não há hoje fonte externa de bureau/antifraude contratada; se houver, muda o desenho (ver perguntas em aberto).
5. O volume atual é baixo o suficiente para uma chamada síncrona adicional dentro do fluxo de transação.

## Abordagens

### A. Scoring embutido no transaction-api

Módulo de scoring dentro do próprio transaction-api: um serviço de domínio que recebe a transação já montada, aplica regras configuráveis (limites por valor, contagem de transações recentes da mesma conta consultada no Postgres do próprio serviço) e devolve score + decisão, persistidos junto da transação.

- Componentes: `transaction-api` (novos campos em `Transaction`, novo serviço de regras, alteração em `processTransaction`), `config-files/transaction-api*.properties` para os limites.
- Esforço: **P/M**.
- Dependências: incluir identificador de conta no `TransactionRequest`.
- Confiança de sucesso na implantação: **80**.

### B. Novo microserviço `risk-score-api`

Serviço dedicado com banco próprio, chamado de forma síncrona pelo transaction-api via Feign + Resilience4j (mesmo padrão do `AssetManagementClient`), registrado no Eureka e exposto no gateway para consulta do score de um cliente.

- Componentes: novo módulo Maven no `pom.xml`, novo container e banco no `docker-compose.yml`, `config-files/risk-score-api.properties` + variante `-docker`, nova rota `spring.cloud.gateway.routes[7]` nos dois arquivos do gateway, novo Feign client no transaction-api com fallback.
- Esforço: **G**.
- Dependências: identificador de conta na transação; definição de dono do dado de risco; decisão sobre o que fazer quando o serviço de risco está fora (fallback aprova ou recusa?).
- Confiança de sucesso na implantação: **65**.

### C. Scoring assíncrono por evento (score pós-transação)

A transação continua decidindo apenas por disponibilidade de ativo; o `TransactionEvent` passa a carregar os dados necessários e um consumidor Kafka calcula o score e marca a transação para revisão/estorno quando o risco é alto.

- Componentes: `TransactionEvent` (transaction-api e a cópia em notification-api — hoje são duas classes com o mesmo nome), novo consumidor (módulo novo ou dentro do notification-api), persistência do resultado.
- Esforço: **M**.
- Dependências: processo operacional de revisão/estorno; aceitação do negócio de que a transação de risco é concluída antes de ser avaliada.
- Confiança de sucesso na implantação: **70**.

## Trade-offs

| Critério | A. Embutido | B. Serviço dedicado | C. Assíncrono |
| --- | --- | --- | --- |
| Complexidade | Baixa | Alta (módulo, banco, rota, compose, config em 2 variantes) | Média |
| Risco técnico | Baixo; risco de acoplar regra de risco ao domínio de transação | Médio; mais um ponto de falha no caminho crítico da transação | Médio; consistência eventual |
| Impacto em sistemas existentes | Contrato do `POST /api/transaction` e schema Postgres | Contrato + gateway + Eureka + docker-compose + config server | Contrato do evento Kafka e do consumidor |
| Reversibilidade | Alta (feature flag na config) | Baixa/média (infra e rota provisionadas) | Média |
| Bloqueia transação de risco antes de concluir | Sim | Sim | **Não** |

## Riscos técnicos e de compliance

- **Bloqueio de fato:** sem identificador de cliente na transação, nenhuma regra baseada em histórico é implementável. Este é o primeiro item de refinamento.
- **Fallback do circuit breaker:** o `fallbackMethod` atual devolve mensagem genérica de sucesso aparente para o cliente; em cenário de risco, "falhou ao avaliar" não pode virar "aprovado" silenciosamente. Precisa de decisão explícita (fail-open vs. fail-closed) com segurança/compliance.
- **Dado pessoal e sensível:** score de risco é dado sensível (LGPD). Requer definição de retenção, quem pode ler, e se pode transitar em evento Kafka. Validar com segurança/privacidade.
- **Auditoria:** decisão de recusa por risco precisa registrar motivo e versão da regra; hoje não há trilha de auditoria na transação.
- **Cache:** `@Cacheable("assetAvailability")` já cacheia decisão de disponibilidade; cachear score por cliente pode servir decisão desatualizada. Definir TTL explicitamente.
- **Dívida de configuração:** `config-files/api-gateway.properties` versiona JWT e o `docker-compose.yml` traz credenciais fixas. Qualquer novo serviço deve nascer com segredo em variável de ambiente, não replicando esse padrão.

## Recomendação

Começar pela **abordagem A**, com o identificador de conta adicionado ao contrato da transação e as regras atrás de flag na configuração. É a única que entrega decisão de risco no caminho crítico sem provisionar infraestrutura nova, e mantém reversibilidade total enquanto as regras de negócio ainda estão sendo calibradas. A extração para a **abordagem B** fica natural depois, quando houver dono claro do domínio de risco e regras estáveis.

## Perguntas em aberto

1. O score decide a transação (recusa automática) ou apenas marca para revisão manual?
2. Score é por transação, por cliente ou os dois?
3. Quais são as variáveis de risco no dia 1 (valor, frequência, país, tipo de ativo, tempo de conta)?
4. Se a avaliação de risco falhar, a transação aprova ou recusa?
5. Existe bureau/antifraude externo a integrar, ou tudo é regra interna?
6. Quem é o dono do dado de risco e por quanto tempo ele é retido?
7. Podemos alterar o contrato de `POST /api/transaction` (quebra clientes existentes) ou é preciso versionar a rota?
