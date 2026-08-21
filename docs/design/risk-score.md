# Design Doc — Risk Score de Transações

## Problema

O banco não possui hoje nenhum mecanismo de avaliação de risco: toda transação é aprovada desde que os ativos estejam disponíveis, sem considerar perfil do cliente, valor ou padrão de comportamento. **Resultado esperado:** cada transação (e/ou cliente) recebe um risk score que pode ser consultado e usado para bloquear ou sinalizar operações de alto risco.

## Contexto atual

- O fluxo de transação está em `transaction-api/src/main/java/com/royal/reserve/bank/transaction/api/service/TransactionService.java` (`processTransaction`): valida disponibilidade de ativos via Feign client (`transaction-api/.../client/AssetManagementClient.java`, com Resilience4j Retry/CircuitBreaker no controller `transaction-api/.../controller/TransactionController.java`), persiste em PostgreSQL e publica `TransactionEvent` no tópico Kafka `notificationTopic`. Não existe hoje nenhuma verificação de risco, valor ou identidade do pagador.
- A transação (`transaction-api/.../model/Transaction.java`) guarda apenas `transactionId` e itens (`TransactionItems`: assetCode, assetName, value). **Não há vínculo entre transação e conta/cliente** — a `TransactionRequest` não carrega número de conta.
- As contas vivem no `account-api` (MongoDB), modelo `account-api/.../model/Account.java` com `accountNumber` (IBAN), `accountHolderName`, `balance` e `currency`; cache-aside com RedisTemplate em `account-api/.../service/AccountService.java`.
- Toda rota externa passa pelo `api-gateway` (`config-files/api-gateway.properties`, rotas `lb://account-api` e `lb://transaction-api`) com validação JWT/JWKS (Auth0).
- O evento Kafka consumido pelo `notification-api` (`notification-api/.../event/TransactionEvent.java`) carrega apenas o `transactionId`.

## Premissas e perguntas em aberto

Premissas: (1) o score inicial pode ser baseado em regras simples (valor, frequência, histórico), sem ML; (2) o score é calculado no momento da transação; (3) transações de alto risco são bloqueadas ou sinalizadas conforme um threshold configurável.

Perguntas para o negócio:
1. O score é da **transação**, do **cliente/conta**, ou ambos?
2. Quais fatores de risco importam (valor, frequência, tipo de ativo, geografia)? Existe política de compliance já definida?
3. Transação de alto risco deve ser **bloqueada**, **sinalizada para revisão manual** ou apenas **registrada**?
4. Hoje transações não referenciam conta — podemos alterar o contrato da API (`TransactionRequest`) para incluir o número da conta? Isso é pré-requisito para qualquer score por cliente.

## Abordagens

### A. Motor de regras embutido no transaction-api (esforço P)

Novo componente `RiskScoreService` dentro do `transaction-api`, chamado por `processTransaction` antes de salvar. Score por regras (ex.: soma dos `value` dos itens acima de um limite configurado em `config-files/transaction-api*.properties`, quantidade de transações recentes consultada no próprio PostgreSQL). Score persistido em coluna nova na tabela `t_transaction` e retornado na resposta.
- Componentes: transaction-api, config-files.
- Dependências: nenhuma externa; requer incluir conta na `TransactionRequest` se o score considerar o cliente.

### B. Novo microserviço risk-assessment-api (esforço M)

Novo módulo Maven seguindo o padrão dos serviços existentes (Eureka, config-server, banco próprio — database-per-service). O `transaction-api` o consulta de forma síncrona via Feign client com Resilience4j (mesmo padrão de `AssetManagementClient`) e usa fallback conservador (aprovar com score neutro ou recusar, a definir com o negócio) quando o serviço estiver fora. Expõe `GET /api/risk-score/{accountNumber}` via rota nova no api-gateway.
- Componentes: novo serviço, transaction-api (client + chamada), api-gateway (rota), config-files (2 arquivos novos), docker-compose.
- Dependências: definição da política de fallback; conta na `TransactionRequest`.

### C. Score assíncrono via Kafka (esforço M/G)

O `transaction-api` continua aprovando como hoje; um consumidor novo (no risk-assessment-api ou no notification-api) escuta os eventos de transação, calcula o score a posteriori e o expõe para consulta/alertas. Exige enriquecer o `TransactionEvent`, que hoje só carrega `transactionId`.
- Componentes: novo consumidor, transaction-api (evento enriquecido), notification-api (alertas), config-files.
- Dependências: decisão de que score **não bloqueia** transação (é pós-fato por natureza).

## Trade-offs

| Critério | A (embutido) | B (serviço síncrono) | C (assíncrono) |
|---|---|---|---|
| Complexidade | Baixa | Média | Média/Alta |
| Risco técnico | Baixo | Médio (latência + fallback no caminho crítico) | Médio (consistência eventual) |
| Impacto no existente | Só transaction-api | transaction-api + gateway + compose | Evento e consumidores |
| Bloqueia transação em tempo real | Sim | Sim | Não |
| Evolução (ML, novos fatores) | Limitada | Boa | Boa |
| Reversibilidade | Alta (feature flag) | Média | Alta |

## Riscos

- **Falta de vínculo transação↔conta**: qualquer score por cliente exige mudança de contrato na `TransactionRequest` — breaking change para consumidores da API; validar com quem consome o gateway.
- **Fallback no caminho crítico (B)**: decidir com arquitetura se, com o serviço de risco fora, transações são aprovadas (risco de fraude) ou recusadas (indisponibilidade); seguir o padrão Resilience4j já usado.
- **Compliance**: critérios de score e retenção do histórico de decisões precisam de validação com segurança/compliance (auditoria de por que uma transação foi bloqueada).
- **Segredos**: novas configs vão em `config-files/<serviço>.properties` **e** `<serviço>-docker.properties`; não versionar credenciais novas (dívida conhecida no repo).

## Recomendação

Começar pela **abordagem A** (motor de regras no transaction-api) atrás de flag de configuração, incluindo desde já o número da conta na `TransactionRequest`. Ela entrega valor rápido no ponto exato onde a transação é decidida e não adiciona dependência de rede no caminho crítico. Se os fatores de risco crescerem ou outro serviço precisar consultar o score, extrair para a abordagem B, cujo padrão de integração (Feign + Resilience4j) já existe no repo.

## Confidence score

- **A — motor embutido: 85/100.** Toca um único serviço, padrão de config e persistência já existentes; incerteza restante é a mudança de contrato da request.
- **B — serviço dedicado: 70/100.** Padrões (Eureka, Feign, Resilience4j, config-server) todos já demonstrados no repo, mas mais peças móveis (compose, gateway, banco novo) e política de fallback em aberto.
- **C — assíncrono: 55/100.** Viável tecnicamente (Kafka já em uso), mas não atende bloqueio em tempo real e depende de enriquecer o evento; maior risco de não atender a expectativa do negócio.
