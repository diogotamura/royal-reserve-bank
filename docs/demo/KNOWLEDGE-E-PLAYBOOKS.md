# Knowledge, Playbooks e Automações da demo

Conteúdo pronto para provisionar na conta antes da demo. Os nós de knowledge e o playbook abaixo
foram escritos a partir do código real deste repositório — durante a demo eles aparecem como
"conhecimento que a organização já acumulou", que é exatamente a narrativa das Etapas 4 e 5.

---

## 1. Knowledge notes sugeridos

### 1.1 Build e execução local do Royal Reserve Bank
**Escopo:** `when working in repo diogotamura/royal-reserve-bank`

- Projeto Maven multi-módulo. O `config-server` sobe primeiro: os outros serviços leem configuração
  dele.
- Build de tudo menos o config-server: `mvn clean install -pl '!config-server'`.
- Infraestrutura de apoio (Mongo, MySQL, PostgreSQL, Kafka, Redis, Zipkin, Prometheus, Grafana):
  `docker compose -f docker-compose-infrastructure-services.yml up -d`.
- Stack completa em containers: `docker compose up -d`.
- Ordem de subida dos serviços: config-server → discovery-server → api-gateway → APIs de negócio.
- Testes de integração usam Testcontainers e exigem um Docker daemon funcional.

### 1.2 Padrões de arquitetura a preservar
**Escopo:** `when working in repo diogotamura/royal-reserve-bank`

- Database-per-service: `account-api` usa MongoDB, `transaction-api` PostgreSQL,
  `asset-management-api` MySQL. Nunca faça um serviço acessar o banco de outro.
- Comunicação síncrona entre serviços somente via Feign client com fallback de Resilience4j; o
  `transaction-api` é a referência.
- Notificação é assíncrona por Kafka (`notification-api`), nunca chamada direta.
- Configuração nova sempre vai para `config-files/<serviço>.properties` e
  `config-files/<serviço>-docker.properties` — as duas variantes, senão a stack em Docker quebra.
- Rotas externas passam obrigatoriamente pelo `api-gateway`; não exponha porta de serviço de negócio.

### 1.3 Critério de PR pronto para revisão
**Escopo:** `when working in repo diogotamura/royal-reserve-bank`

- Build dos módulos afetados passando.
- Testes dos módulos afetados executados, com o resultado colado na descrição do PR.
- Mudança que toca subida de serviço, configuração ou dependência precisa de validação com a stack no
  ar (Eureka registrando os serviços e `/actuator/health` respondendo) e evidência anexada.
- Não versionar segredo: token, senha e chave vão para variável de ambiente.

### 1.4 Segredos e configuração sensível
**Escopo:** `when working in repo diogotamura/royal-reserve-bank`

- Existe dívida conhecida: JWT hardcoded em `config-files/api-gateway.properties` e credenciais fixas
  no `docker-compose.yml`. Ao mexer nesses arquivos, migre para variável de ambiente em vez de
  reproduzir o padrão atual.
- Validação de JWT usa Auth0 como issuer (JWKS remoto). Não troque o issuer sem sinalizar.

---

## 2. Playbook: upgrade de Java e Spring Boot em repositório Maven

Use este texto ao criar o playbook. Ele é a versão generalizada da sessão da Etapa 3, pronta para
rodar em qualquer repositório Java/Spring da organização.

**Nome:** Upgrade de Java e Spring Boot (Maven multi-módulo)

**Overview**
Sobe a versão de Java e do `spring-boot-starter-parent` de um repositório Maven, junto com as
dependências transitivas necessárias, validando build, testes e subida da aplicação antes de abrir o
PR. Pensado para rodar em vários repositórios em paralelo.

**Inputs necessários**
1. Repositório alvo.
2. Versão de Java de destino (ex.: 21).
3. Versão de Spring Boot de destino (ex.: última estável da linha 3.3.x).
4. Se há restrição de janela de compatibilidade (ex.: manter suporte a um cliente interno).

**Passos**
1. Ler a Wiki/documentação indexada do repositório e mapear módulos, dependências e testes
   existentes. Não editar nada antes de ter o plano.
2. Levantar a matriz de compatibilidade: Spring Boot ↔ Spring Cloud ↔ Java ↔ Testcontainers ↔
   plugins de build (Jib, javadoc, surefire/failsafe) e imagem base de runtime.
3. Atualizar o `pom.xml` raiz (parent, `java.version`, properties de versão) e depois os módulos.
4. Compilar e corrigir breaking changes de compilação um a um, sem alterar regra de negócio nem
   contrato de API.
5. Rodar os testes dos módulos afetados, incluindo integração com Testcontainers. Corrigir
   quebras causadas pelo upgrade; se um teste falha por problema pré-existente, comprovar isso na
   branch base antes de afirmar.
6. Subir a aplicação (dev server e/ou `docker compose`), abrir no browser e navegar pelos fluxos
   principais — dashboards de serviço, health checks e uma operação de negócio ponta a ponta —
   **gravando a tela como prova de que a mudança não causou impacto negativo**. Anexar a gravação
   à sessão.
7. Abrir o PR com: o que mudou, matriz de versões antes/depois, resultado dos testes, evidência de
   execução e riscos residuais.
8. Acompanhar o CI e corrigir falhas mecânicas (formatação, imports, dependência faltando) até ficar
   verde.

**Pontos de atenção**
- Upgrade de Spring Boot costuma arrastar Spring Cloud; a versão errada compila e falha em runtime no
  gateway ou no discovery.
- Imagem base de container precisa subir junto com o Java, senão o build passa e o container morre.
- Testcontainers antigo não roda em JDK novo.
- Property renomeada entre versões de Spring Boot: revisar `config-files/` (as duas variantes, local
  e docker).

**Ações proibidas**
- Alterar regra de negócio, contrato de API ou schema de banco.
- Desabilitar, marcar como ignorado ou reescrever teste para fazer o build passar.
- Fazer commit de segredo ou de credencial.
- Force push em branch compartilhada.

---

## 3. Rules sugeridas (org-level)

- Todo PR gerado por sessão precisa incluir o resultado dos testes executados na descrição.
- Mudança em dependência exige validação com a aplicação no ar e evidência gravada.
- Nunca versionar segredo; usar variável de ambiente.
- Não alterar `config-files/*.properties` sem atualizar a variante `-docker` correspondente.

---

## 4. Automações sugeridas para mostrar na Etapa 6

| Automação | Gatilho | O que faz |
|---|---|---|
| Atualização de dependências | Semanal | Abre PR de upgrade de dependências com testes rodados |
| Triagem de bug | Novo issue/ticket com label crítica | Investiga, aponta causa provável e abre PR de fix quando trivial |
| Status report de engenharia | Semanal, para o Slack | Resume PRs abertos, sessões rodadas e ACU consumida |
| Documentação viva | Merge na main (webhook) | Reindexa o repositório e atualiza a Wiki |

Para a demo, ter **uma** automação real ligada (o status report no Slack é a mais fácil de mostrar) é
mais convincente do que descrever quatro no papel.
