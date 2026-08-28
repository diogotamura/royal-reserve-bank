# Risk score — design doc

## Problema

**Problema:** hoje o banco processa transações sem nenhuma avaliação de risco: qualquer requisição válida é persistida e confirmada, sem sinal de fraude, exposição ou perfil do cliente.

**Resultado esperado:** existir um "risk score" (nota de risco) calculado e disponível para consulta, capaz de influenciar a decisão sobre uma transação (aprovar, revisar ou bloquear) e auditável depois.

## Contexto atual (verificado no código)

- A transação é processada em `TransactionService.processTransaction` (`transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java`): gera um `transactionId` (UUID), mapeia os itens, checa disponibilidade de ativo via Feign e, se disponível, salva e publica um evento Kafka. **Não existe nenhuma regra de risco, limite ou score nesse fluxo.**
- Único ponto de decisão hoje é `checkAssetAvailability`, que chama `AssetManagementClient` (`transaction-api/.../client/AssetManagementClient.java`) e considera disponível quando `value > 0` (`asset-management-api/src/main/java/com/royal/reserve/bank/asset/management/api/service/AssetManagementService.java`).
- A entrada externa é `POST /api/transaction` (`transaction-api/.../controller/TransactionController.java`), protegida por Circuit Breaker/Retry/TimeLimiter do Resilience4j (instância `asset-management`), com fallback que devolve mensagem genérica.
- **O modelo de transação não tem cliente nem conta:** `Transaction` (`transaction-api/.../model/Transaction.java`) tem apenas `id`, `transactionId` e itens; `TransactionItems` (`.../model/TransactionItems.java`) tem `assetCode`, `assetName` e `value` como `int`. O `TransactionRequest` (`.../dto/TransactionRequest.java`) só transporta a lista de itens.
- Conta vive isolada em `account-api` no MongoDB: `Account` (`account-api/.../model/Account.java`) tem `accountNumber`, `accountHolderName`, `balance`, `currency`; o repositório é um `MongoRepository` sem query por número de conta (`account-api/.../repository/AccountRepository.java`) e o controller expõe apenas criar, listar todas e deletar por nome (`account-api/.../controller/AccountController.java`).
- Notificação é assíncrona: `TransactionEvent` (só `transactionId`) é publicado em `notificationTopic` e consumido em `NotificationApiApplication.handleNotification` (`notification-api/.../NotificationApiApplication.java`), que apenas loga.
- Roteamento externo é declarado em `config-files/api-gateway.properties` (rotas `/api/account`, `/api/transaction`, `/api/asset-management`), com validação de JWT contra JWKS do Auth0. Cada serviço tem seu par `<serviço>.properties` / `<serviço>-docker.properties` em `config-files/`.
- Módulos do build em `pom.xml` (Java 17, Spring Boot 3.0.6): config-server, discovery-server, api-gateway, account-api, asset-management-api, transaction-api, notification-api.

**Consequência direta:** sem cliente/conta na transação e sem histórico consultável por cliente, nenhuma abordagem entrega score comportamental antes de o payload da transação passar a identificar a conta.

## Premissas

1. "Risk score" é risco de fraude/inadimplência da **transação**, expresso como nota 0–100 mais uma faixa (LOW/MEDIUM/HIGH).
2. A primeira versão pode usar regras determinísticas (valor, frequência, desvio do histórico), sem modelo de ML e sem bureau externo.
3. O score precisa ser persistido para auditoria e consultável por `transactionId`.
4. Nenhum dado pessoal adicional (documento, endereço) entra no escopo agora.

## Abordagens

### A — Score embutido no `transaction-api` (esforço P)

Regras de risco calculadas dentro do próprio `transaction-api`, no fluxo de `processTransaction`, usando o valor dos itens e o histórico já existente no PostgreSQL do serviço; score gravado em coluna/tabela nova e devolvido na resposta.

- Componentes: `transaction-api` (service, model, repository, DTO), migração de schema no PostgreSQL do serviço.
- Dependências: passar a identificar a conta no `TransactionRequest` para qualquer regra por cliente.
- Confiança: **85**

### B — Novo microserviço `risk-api` chamado sincronamente pelo `transaction-api` (esforço M)

Serviço dedicado com banco próprio, exposto via gateway (`/api/risk`) e consumido pelo `transaction-api` por Feign client com Circuit Breaker/fallback, no mesmo padrão de `AssetManagementClient`. O `transaction-api` aplica a política (aprovar/revisar/bloquear) conforme a faixa retornada.

- Componentes: novo módulo no `pom.xml`, novo par de properties em `config-files/`, rota nova no `api-gateway`, serviço + banco no `docker-compose.yml`, Feign client e política no `transaction-api`.
- Dependências: conta/cliente no payload da transação; definição de política de decisão pelo negócio; fallback definido para quando o `risk-api` estiver fora (hoje o fallback do controller devolve mensagem genérica de erro).
- Confiança: **70**

### C — Novo `risk-api` alimentado por eventos Kafka (esforço M/G, score assíncrono)

O `risk-api` consome `notificationTopic` (ou um tópico novo de transações), calcula o score depois do fato e o expõe por consulta; a transação não é bloqueada em tempo real.

- Componentes: novo módulo e banco, consumidor Kafka, enriquecimento do `TransactionEvent` (hoje só `transactionId`, o que obriga o consumidor a buscar a transação em outro serviço — proibido pelo isolamento de banco por serviço).
- Dependências: enriquecer o evento sem quebrar o consumidor atual do `notification-api`.
- Confiança: **60**

## Trade-offs

| | Complexidade | Risco | Impacto em sistemas existentes | Reversibilidade |
|---|---|---|---|---|
| A — embutido no transaction-api | Baixa | Baixo; risco de acoplar regra de negócio ao serviço transacional | Só `transaction-api` + schema | Alta (feature flag / remover regra) |
| B — `risk-api` síncrono | Média | Médio; nova dependência no caminho crítico da transação | Gateway, docker-compose, config-files, transaction-api | Média (desligar chamada, serviço fica órfão) |
| C — `risk-api` por eventos | Média/Alta | Baixo para latência, alto para o produto: não bloqueia transação suspeita | Contrato do evento Kafka e `notification-api` | Média |

## Riscos

- **Caminho crítico (B):** a chamada de risco entra no fluxo de criação de transação; sem timeout e fallback explícitos, indisponibilidade do `risk-api` degrada a transação. Precisa de decisão do negócio: falha aberta (aprova) ou fechada (bloqueia).
- **Modelo de dados (todas):** a transação não identifica conta nem cliente; mudar `TransactionRequest`/`Transaction` altera contrato público de `POST /api/transaction`.
- **Isolamento de dados:** score por cliente tende a exigir dados de conta que vivem no MongoDB do `account-api` — o acesso tem que ser por API/Feign, nunca por banco cruzado.
- **Compliance/segurança:** score de risco é decisão automatizada sobre cliente — precisa de trilha de auditoria (entradas, versão da regra, resultado) e validação com segurança/jurídico sobre retenção e explicabilidade. Validar com arquitetura: contrato do novo serviço, política de fallback e alteração do contrato do evento Kafka.
- **Configuração:** qualquer propriedade nova tem que entrar nas duas variantes (`<serviço>.properties` e `<serviço>-docker.properties`), senão a stack em Docker quebra.

## Recomendação

Começar pela **abordagem A**, precedida da inclusão de conta/cliente no payload e no modelo de transação. Ela entrega score auditável e decisão em tempo real sem introduzir uma dependência nova no caminho crítico, e o cálculo pode ser extraído para um `risk-api` (abordagem B) quando as regras crescerem ou passarem a exigir dados externos. A abordagem C não resolve o caso de uso principal, já que não impede a transação suspeita.

## Perguntas em aberto

1. O score deve **bloquear** transação ou apenas sinalizar/encaminhar para revisão manual? Quem define os cortes das faixas?
2. Risco é da transação, do cliente ou dos dois?
3. Quais sinais estão disponíveis e autorizados (valor, frequência, histórico, geolocalização, dispositivo)? Bureau externo está no escopo futuro?
4. Se o cálculo falhar, aprova ou bloqueia?
5. Qual retenção e qual nível de explicabilidade são exigidos por compliance?
6. Há ticket/documento de origem e prazo?
