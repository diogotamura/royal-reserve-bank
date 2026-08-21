# Risk Score — design doc

## Problema

Hoje o banco aprova uma transação apenas verificando se o ativo está disponível; não existe nenhuma avaliação de risco do cliente ou da operação. O resultado esperado é ter um **Risk Score** — uma nota de risco calculada por regras — disponível para consulta e, opcionalmente, usada como critério na aprovação da transação.

## Contexto atual (verificado no código)

- Sistema Spring Boot 3.0.6 multi-módulo, 7 módulos declarados em `pom.xml`: config-server, discovery-server, api-gateway, account-api, asset-management-api, transaction-api, notification-api.
- O fluxo de transação está em `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java`: gera um `transactionId` (UUID), monta os itens, chama `checkAssetAvailability` e só então persiste e publica o evento no tópico `notificationTopic`. A única regra de negócio de aprovação é `assetIsAvailable`.
- Chamada entre serviços é síncrona via Feign com Resilience4j: `transaction-api/.../client/AssetManagementClient.java` (`@FeignClient(name = "asset-management-api")`) e as anotações `@CircuitBreaker/@TimeLimiter/@Retry` em `transaction-api/.../controller/TransactionController.java`, com fallback `fallbackMethod`.
- Notificação é assíncrona por Kafka: `transaction-api/.../service/TransactionService.java` publica `TransactionEvent` e `notification-api/src/main/java/com/royal/reserve/bank/notification/api/NotificationApiApplication.java` consome com `@KafkaListener(topics = "notificationTopic")`. O evento carrega **apenas** `transactionId` (`transaction-api/.../event/TransactionEvent.java`).
- **A transação não tem vínculo com cliente nem data**: `transaction-api/.../model/Transaction.java` tem só `id`, `transactionId` e a lista de itens; `transaction-api/.../model/TransactionItems.java` tem `assetCode`, `assetName` e `value` (int). Não há `accountNumber`, `customerId` nem `createdAt`.
- **A conta não tem identificador de cliente nem dado cadastral de risco**: `account-api/.../model/Account.java` tem `id`, `accountNumber`, `accountHolderName`, `balance`, `currency`. Não há CPF/documento, data de nascimento, renda ou histórico.
- **A identidade do usuário autenticado não chega aos serviços de negócio**: `api-gateway/.../config/SecurityConfig.java` valida o JWT contra o JWKS do Auth0 e o `jwtFilter()` apenas guarda o token em um campo da configuração — não propaga claims nem header de identidade para os serviços downstream.
- Bancos por serviço: account-api MongoDB (`config-files/account-api.properties`), transaction-api PostgreSQL (`config-files/transaction-api.properties`), asset-management-api MySQL. Cache-aside: `RedisTemplate` em `account-api/.../service/AccountService.java`, Spring Cache (`@Cacheable("assetAvailability")`) em transaction-api e asset-management-api.
- Toda rota externa é declarada no gateway (`spring.cloud.gateway.routes[0..6]` em `config-files/api-gateway.properties`); os arquivos `-docker.properties` são apenas overrides de host/porta (ex.: `config-files/transaction-api-docker.properties`), e ambos precisam ser mantidos.
- Dívida relevante achada no caminho: as instâncias Resilience4j configuradas em `config-files/transaction-api.properties` se chamam `inventory`, mas as anotações usam `name = "asset-management"` — a configuração de circuit breaker/retry/timeout hoje não se aplica, valem os defaults. Um Risk Score síncrono herdaria esse problema.
- Segredos versionados: JWT expirado em `config-files/api-gateway.properties` e credenciais fixas no `docker-compose.yml` (dívida conhecida; qualquer config nova de Risk Score deve usar variável de ambiente).

Consequência prática: **não existe hoje nenhum dado de cliente ou histórico temporal para alimentar um score**. Qualquer abordagem exige antes ligar transação ↔ conta/cliente e carimbar data na transação.

## Premissas assumidas

1. "Risk Score" = nota numérica (ex.: 0–1000) por cliente/conta, derivada de regras determinísticas configuráveis, sem modelo de machine learning nesta primeira fase.
2. As regras iniciais usam dados que o próprio banco tem (valor da transação, frequência, saldo/conta), sem bureau externo.
3. O score precisa ser consultável por API (para app/back-office) e auditável (dá para explicar por que deu aquela nota).
4. Bloquear transação com base no score é opcional na fase 1 (feature flag), não requisito.

## Abordagens

### A) Score interno ao transaction-api

Um componente de risco dentro do `transaction-api` (serviço + tabela no PostgreSQL do próprio módulo), calculado no fluxo de `processTransaction` e exposto por um endpoint novo no `TransactionController`.

- Escopo: adicionar `accountNumber` + `createdAt` em `Transaction`, tabela de score/histórico, regras, endpoint de consulta.
- Componentes afetados: `transaction-api` (model, repository, service, controller), `config-files/transaction-api*.properties`; nenhuma rota nova no gateway se reusar `/api/transaction`.
- Esforço: **P/M**.
- Dependências: nenhuma nova infraestrutura.
- Confiança: **80**.

### B) Novo microserviço `risk-score-api` consultado de forma síncrona

Módulo novo com banco próprio, chamado pelo `transaction-api` via Feign + Resilience4j (mesmo padrão do `AssetManagementClient`) antes de persistir a transação, e exposto no gateway para consulta externa.

- Escopo: novo módulo no `pom.xml`, serviço + banco, cliente Feign no transaction-api, rota `spring.cloud.gateway.routes[7]` no par `api-gateway.properties` / `-docker.properties`, novo par `config-files/risk-score-api*.properties`, serviço e banco no `docker-compose.yml`, imagem Jib.
- Componentes afetados: novo módulo, transaction-api, api-gateway (config), docker-compose, config-files.
- Esforço: **G**.
- Dependências: decidir o banco do novo serviço; corrigir o naming Resilience4j para o circuit breaker realmente valer; definir comportamento quando o risco está indisponível (fail-open vs fail-closed) — decisão de compliance.
- Confiança: **60**.

### C) Novo microserviço `risk-score-api` orientado a evento (assíncrono)

Mesmo módulo novo, mas em vez de entrar no caminho crítico da transação ele consome eventos Kafka e recalcula o score após cada transação; o app e o back-office leem o score por API.

- Escopo: novo módulo + banco + rota no gateway; enriquecer `TransactionEvent` (hoje só `transactionId`) com conta, valor e data, ajustando produtor (`transaction-api`) e o mapeamento `spring.json.type.mapping` nos dois lados (`config-files/transaction-api.properties`, `config-files/notification-api.properties` como referência do padrão).
- Componentes afetados: novo módulo, transaction-api (evento), api-gateway (config), docker-compose, config-files.
- Esforço: **M/G**.
- Dependências: contrato do evento versionado; score é *near-real-time*, logo não serve para bloquear a transação que o originou.
- Confiança: **70**.

## Trade-offs

| | A) interno ao transaction-api | B) serviço síncrono | C) serviço por evento |
|---|---|---|---|
| Complexidade | Baixa | Alta | Média/Alta |
| Risco operacional | Baixo | Alto: entra no caminho crítico do pagamento | Baixo: fora do caminho crítico |
| Impacto em sistemas existentes | Concentrado em 1 módulo | transaction-api, gateway, compose, config | transaction-api (contrato de evento), gateway, compose, config |
| Reversibilidade | Alta (flag + remover endpoint) | Média (contrato entre serviços já publicado) | Média/Alta (consumidor pode ser desligado) |
| Latência na transação | + cálculo local | + 1 hop de rede com retry/timeout | zero |
| Evolução (ML, bureau, outros produtos) | Ruim: risco vira refém do domínio de transação | Boa | Boa |

## Riscos

- **Técnico — dados inexistentes:** sem `accountNumber`/`createdAt` na transação e sem identificador de cliente na conta, nenhuma regra de velocidade ou histórico é implementável. Essa é a real primeira entrega, independente da abordagem.
- **Técnico — identidade não propagada:** o gateway não repassa claims (`SecurityConfig.jwtFilter`), então "score do cliente logado" não é possível hoje sem mudar a borda de autenticação.
- **Técnico — resiliência ilusória:** o mismatch de nomes Resilience4j em `config-files/transaction-api.properties` significa que a abordagem B entraria no caminho crítico sem a proteção que o time acredita ter. Precisa validação de arquitetura.
- **Técnico — precisão monetária:** `TransactionItems.value` é `int`; qualquer regra por valor herda esse tipo. Definir se vira `BigDecimal` (como `Account.balance`).
- **Compliance:** decisão automatizada sobre cliente exige explicabilidade e trilha de auditoria (guardar entrada, versão da regra e resultado); dados pessoais adicionais (documento, renda) puxam LGPD, retenção e mascaramento. Fail-open vs fail-closed na indisponibilidade do risco é decisão conjunta com risco/compliance, não técnica.
- **Segurança:** não replicar o padrão de segredo versionado; configuração nova via variável de ambiente.

## Recomendação

Começar pela abordagem **A**, entregando primeiro o vínculo transação ↔ conta e o carimbo de data, com o score calculado e exposto apenas para consulta (sem bloquear transação). É a única opção que gera valor sem publicar contrato entre serviços antes de as regras de risco estarem estáveis, e mantém o caminho aberto para extrair um `risk-score-api` (abordagem C) quando risco virar domínio próprio. A abordagem B não deve ser adotada nesta fase: colocar avaliação de risco no caminho crítico do pagamento com o circuit breaker hoje inoperante é risco desproporcional ao ganho.

## Perguntas em aberto

1. Risk Score de **quê**: do cliente, da conta ou da transação individual? A resposta muda o modelo de dados.
2. O score deve **bloquear** transação nesta fase, ou é só informativo/consultivo?
3. Quais fatores de risco o negócio quer na v1 (valor, frequência, saldo, tempo de relacionamento, bureau externo)?
4. Quem consome: app do cliente, back-office, ou ambos? Tem tela?
5. Existe faixa/classificação oficial (ex.: baixo/médio/alto) e política já aprovada por risco/compliance?
6. Podemos coletar dados pessoais adicionais (documento, renda) ou a v1 fica restrita a dados transacionais?
7. Qual a exigência de auditoria e retenção do histórico de score?
