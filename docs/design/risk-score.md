# Risk Score — Design Doc

Demanda original: "implementar uma nova funcionalidade de risk score" (royal-reserve-bank).
Status: brainstorm para refinamento. Nada implementado.

## Problema

Hoje uma transação é aceita ou recusada apenas em função da disponibilidade do ativo; não existe
nenhuma avaliação de risco do cliente ou da operação. Resultado esperado: cada transação (e/ou
cliente) passa a ter um risk score consultável, que o banco pode usar para aprovar, revisar ou
bloquear a operação.

## Contexto atual (verificado no código)

- Transação: `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java`
  — `processTransaction` gera um `transactionId` (UUID), verifica disponibilidade dos ativos via
  Feign (`checkAssetAvailability`), salva em PostgreSQL e publica em Kafka `notificationTopic`.
  A única regra de recusa é `IllegalArgumentException("Asset is not available...")`.
- Entrada da transação: `dto/TransactionRequest.java` tem apenas `List<TransactionItemsDto>`;
  `dto/TransactionItemsDto.java` tem `assetCode`, `assetName`, `value` (`int`).
  **Não existe identificação do cliente/conta na transação** — nem `accountNumber`, nem CPF, nem canal.
- Modelo persistido: `model/Transaction.java` (`t_transaction`) guarda só `id`, `transactionId` e itens
  (`model/TransactionItems.java`, `t_transaction_items`). Não há valor total, data/hora, moeda ou contraparte.
- Conta: `account-api/.../model/Account.java` tem `accountNumber` (IBAN gerado aleatoriamente em
  `AccountService.generateIBAN()`), `accountHolderName`, `balance`, `currency`. Não há histórico,
  data de abertura, país de residência nem documento — a busca é por nome
  (`deleteAccountByAccountHolderName`) e `AccountRepository` é um `MongoRepository` sem query própria.
- Endpoints existentes: `/api/account` (`AccountController`), `/api/transaction` (`TransactionController`),
  `/api/asset-management` (`AssetManagementController`). Todos expostos via
  `config-files/api-gateway.properties`, rotas `routes[0]`..`routes[6]`.
- Resiliência: `TransactionController` usa `@CircuitBreaker/@TimeLimiter/@Retry` name `asset-management`,
  com `fallbackMethod` que devolve mensagem genérica de erro. Os parâmetros em
  `config-files/transaction-api.properties` estão sob a instância `inventory`, e não `asset-management`.
- Assíncrono: `notification-api/.../NotificationApiApplication.java` consome `notificationTopic` e apenas
  faz `log.info`; o evento (`event/TransactionEvent.java`) carrega somente `transactionId`.
- Nenhuma ocorrência de "risk" no código, configuração ou docker-compose hoje (grep no repositório).
- Módulos declarados em `pom.xml`: config-server, discovery-server, api-gateway, account-api,
  asset-management-api, transaction-api, notification-api.

## Premissas

1. "Risk score" = score numérico (ex.: 0–100) com faixa/decisão (BAIXO/MÉDIO/ALTO) por transação,
   derivado de dados da conta + da transação.
2. Cálculo determinístico por regras na primeira entrega; sem modelo de ML e sem bureau externo.
3. O score deve ser auditável (persistido com a versão das regras que o gerou).
4. Sem quebra de contrato: clientes atuais de `POST /api/transaction` continuam funcionando.

## Perguntas em aberto

- O score bloqueia a transação, ou só registra/expõe para análise? Quem decide o corte?
- Score é da transação, do cliente, ou dos dois?
- Quais sinais o negócio considera risco (valor alto, ativo específico, saldo insuficiente, frequência,
  país)? Sem histórico persistido hoje, sinais de comportamento exigem novo armazenamento.
- Precisa de bureau/lista restritiva (PLD/sanções)? Isso muda compliance e custo.
- Qual a latência aceitável no fluxo de transação?
- Como identificar o cliente na transação (o payload atual não tem conta)? Sem isso, qualquer score é
  apenas sobre os itens da operação.

## Abordagens

### A. Score embutido no transaction-api (regras locais) — esforço P

Novo componente dentro de `transaction-api` (ex.: `service/RiskScoreService`) chamado por
`processTransaction` antes do `save`, com regras sobre os campos que já existem (soma de
`TransactionItems.value`, quantidade de itens, ativos). Score gravado em colunas novas de
`t_transaction` (`ddl-auto=update` já cria) e incluído no `TransactionEvent`.

- Componentes: transaction-api, `config-files/transaction-api*.properties` (thresholds).
- Dependências: nenhuma nova. Não precisa de conta/cliente.
- Confidence: **85** — mexe em um módulo só, sem nova infra; risco é o score ser pobre por falta de dados.

### B. Novo microserviço `risk-score-api` consumido por Feign — esforço M

Módulo novo no `pom.xml` seguindo o padrão dos demais (Eureka + config-server + banco próprio +
Spring Cache), expondo `POST /api/risk-score`. `transaction-api` chama via novo Feign client no padrão de
`client/AssetManagementClient.java`, com circuit breaker e fallback (score neutro / rota de revisão
manual). Rota nova `routes[7]` em `config-files/api-gateway.properties` para consulta pelo back-office.

- Componentes: novo módulo, `pom.xml`, transaction-api (client + service), api-gateway (rota),
  `docker-compose.yml` (serviço + banco), `config-files/risk-score-api.properties` e
  `risk-score-api-docker.properties` (as duas variantes, conforme padrão do repo).
- Dependências: banco próprio (database-per-service), imagem no docker-compose.
- Confidence: **70** — arquitetura alinhada ao repo, mas é a opção com mais superfície nova
  (infra, config em duas variantes, registro no Eureka) e depende de definir contrato de dados.

### C. Score assíncrono via Kafka (pós-transação) — esforço M

`transaction-api` continua decidindo só por disponibilidade; um consumidor novo (serviço próprio ou
extensão do padrão de `notification-api`) lê o evento e calcula o score fora do caminho crítico.
Exige enriquecer `TransactionEvent`, que hoje só tem `transactionId`, ou dar ao consumidor uma forma de
ler a transação — o que colide com database-per-service se ele acessar o PostgreSQL do transaction-api.

- Componentes: transaction-api (evento), novo consumidor, Kafka (tópico), banco do score.
- Dependências: contrato de evento; endpoint de leitura de transação (não existe hoje — o
  `TransactionController` só tem `POST`).
- Confidence: **55** — não serve para bloquear em tempo real e requer um endpoint de leitura inexistente.

## Trade-offs

| | Complexidade | Risco | Impacto em sistemas existentes | Reversibilidade |
|---|---|---|---|---|
| A. Embutido | Baixa | Baixo (latência interna, sem I/O novo) | Só transaction-api + schema | Alta (feature flag/threshold em properties) |
| B. Serviço novo | Média/Alta | Médio (mais um hop, novo ponto de falha, mitigado por fallback) | pom, gateway, docker-compose, config x2 | Média (remover módulo e rota) |
| C. Assíncrono | Média | Médio (score chega depois da transação já efetivada) | Contrato de evento + consumidor | Média |

## Riscos técnicos e de compliance

- **Dado de cliente ausente**: sem identificação de cliente na transação, o score só reflete a operação.
  Adicionar identificador é pré-requisito para qualquer score de cliente — validar com arquitetura.
- **Dado pessoal**: se entrarem CPF/país/histórico, entra LGPD (retenção, mascaramento em log — hoje
  `AccountService` loga o nome do titular). Validar com segurança.
- **Auditoria/explicabilidade**: decisão de crédito/bloqueio exige guardar entradas, versão das regras e
  resultado; sem isso não há como justificar recusa ao cliente.
- **Segredo/config**: se houver integração externa, credencial via variável de ambiente — não repetir o
  padrão do JWT versionado em `config-files/api-gateway.properties`.
- **Resiliência (B)**: `transaction-api` hoje já tem circuit breaker cujos parâmetros estão configurados
  na instância `inventory` e não `asset-management`; replicar esse padrão sem corrigir propaga a falha
  de configuração para o novo client.
- **Falso bloqueio**: score que bloqueia sem fase de observação pode recusar transação legítima. Sugerido
  shadow mode (calcula e registra, não bloqueia) antes de ativar decisão.

## Recomendação

Começar pela abordagem **A**, em shadow mode e com thresholds em configuração: entrega score auditável
mexendo em um módulo só, e expõe rápido o que falta de dado para o negócio decidir. Extrair para
`risk-score-api` (abordagem B) quando as regras estabilizarem ou quando entrar fonte externa —
o contrato do serviço deve ser desenhado já com essa evolução em mente. A abordagem C não atende se o
score precisar influenciar a decisão da transação.

Pré-requisito para score de cliente (independente da abordagem): incluir identificação de conta/cliente
no `TransactionRequest` e persistir data/hora e valor total na transação.

## Plano de testes

Base já existente no repo: suíte com 55 testes (account-api 21, transaction-api 25,
asset-management-api 8, notification-api 1). Padrão de teste unitário em
`transaction-api/src/test/.../unit/service/TransactionServiceTest.java`, testes com MockMvc em
`unit/controller/` e `integration/controller/TransactionControllerIT.java` (`@WebMvcTest` com
`@MockBean TransactionService`), e integração com Testcontainers reais (ex.: `MongoDBContainer` no
account-api). `mvn -B test` só passa com config-server e infra no ar (senão
`ConfigClientFailFastException` em `localhost:8888`) — não confundir com teste quebrado.
Classes com sufixo `IT` não rodam em `mvn test` (Failsafe não configurado no `pom.xml`), então
o que precisa rodar em CI deve seguir o sufixo `Test`.

### Comum às três abordagens

1. **Unitário das regras de score** — tabela de casos entrada→score→faixa, incluindo bordas exatas dos
   thresholds, lista vazia de itens, valor zero e valor máximo. É onde deve ficar a maior parte da
   cobertura, sem Spring context.
2. **Idempotência/determinismo** — mesma entrada gera sempre o mesmo score e a mesma versão de regra
   registrada (importante para auditoria).
3. **Contrato HTTP** — MockMvc no padrão dos testes atuais: transação continua retornando 201 com
   payload legado (sem campos de risco), garantindo retrocompatibilidade.
4. **Regressão do fluxo atual** — asset indisponível continua caindo em
   `IllegalArgumentException("Asset is not available...")` e não é mascarado pelo score.
5. **Shadow mode** — com a flag desligada, nenhum comportamento observável muda; com ela ligada, o score
   é persistido/publicado mas não bloqueia.

### A. Score embutido no transaction-api

- Unitário de `RiskScoreService` (itens 1–2 acima) e de `TransactionService.processTransaction`
  verificando que o score é calculado antes do `save` e que o valor entra no `TransactionEvent`.
- Persistência: teste de repositório confirmando que as colunas novas de `t_transaction` são gravadas
  (`ddl-auto=update` cria o schema).
- Thresholds vindos de `config-files/transaction-api.properties` e da variante `-docker`: teste com
  `@TestPropertySource` cobrindo default e valor sobrescrito, e conferência manual de que as duas
  variantes têm as mesmas chaves (divergência quebra só em Docker).
- Cache: `@CacheEvict("assetAvailability")` já existente não deve cachear score.

### B. Novo risk-score-api

- Tudo de A, mais:
- **Contrato do client** — teste do Feign client com WireMock/servidor stub: resposta OK, 4xx, 5xx,
  timeout e corpo malformado.
- **Resiliência** — abrir o circuit breaker e verificar o fallback (score neutro ou rota de revisão),
  garantindo que indisponibilidade do risk-score-api não derruba a transação. Atenção: os parâmetros
  em `config-files/transaction-api.properties` estão sob a instância `inventory`, não
  `asset-management` — o teste deve validar que a instância do novo client está de fato configurada.
- **Serviço novo** — unitários + integração com Testcontainers do banco próprio, no padrão dos ITs
  atuais; nenhum acesso ao banco de outro serviço (database-per-service).
- **Stack no ar** — subir `docker compose up -d`, confirmar registro no Eureka
  (http://localhost:8761/eureka/apps) e `/actuator/health`, e chamar a rota nova pelo gateway
  (`routes[7]` em `config-files/api-gateway.properties`). Requer token válido: o JWT versionado em
  `config-files/api-gateway.properties` está expirado, então sem token novo a chamada retorna 401.
  Cenário de smoke pode reaproveitar `postman/postman-collection.json`.

### C. Assíncrono via Kafka

- Tudo de A (nas partes de cálculo), mais:
- **Contrato do evento** — serialização/desserialização do `TransactionEvent` enriquecido, com teste de
  compatibilidade para consumidor antigo (o `notification-api` hoje só lê `transactionId`).
- **Consumidor** — teste de integração com Testcontainers do Kafka: evento publicado gera score
  persistido; reprocessamento do mesmo evento não duplica (idempotência por `transactionId`).
- **Eventual consistency** — teste explícito de que a transação é efetivada antes do score existir, e
  medição do atraso; se o negócio precisar bloquear, este cenário é a evidência de que C não serve.

### Aceite (independente da abordagem)

- Build dos módulos afetados passando e testes dos módulos afetados executados, com o resultado na
  descrição do PR.
- Quando a mudança toca dependência, configuração ou subida de serviço: validação com a stack no ar
  (Eureka registrando os serviços e `/actuator/health` respondendo), com evidência anexada.
- Shadow mode observado em ambiente de teste com amostra de transações antes de ligar decisão, com
  taxa de falso bloqueio revisada pelo negócio.
