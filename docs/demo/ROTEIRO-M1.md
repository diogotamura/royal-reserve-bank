# Roteiro de Demo M1 — Devin no Royal Reserve Bank

Roteiro de primeira reunião (M1) construído a partir de duas demos reais (Porto Seguro e Xertica),
adaptado para ser executado ao vivo neste repositório: uma plataforma bancária Java 17 / Spring Boot
com 7 microsserviços, Kafka, Redis, MongoDB, MySQL, PostgreSQL, Eureka, API Gateway e Zipkin.

**Duração alvo:** 45–60 min (35 min de produto + 10–15 min de Q&A).
**Regra de ouro:** nada de slides. Descoberta rápida e produto na tela em menos de 5 minutos.

| Etapa | Tema | Tempo |
|---|---|---|
| 0 | Abertura e descoberta de dores | 5–8 min |
| 1 | Wiki: indexação e documentação automática | 6–8 min |
| 2 | Chat vs. Agente | 3–4 min |
| 3 | Execução real: upgrade Java 17→21 + Spring Boot | 8–10 min (roda em background) |
| 4 | Knowledge | 4 min |
| 5 | Playbooks | 4 min |
| 6 | Skills & Rules, Automações e Integrações | 5 min |
| 7 | Segurança | 3–4 min |
| 8 | Modelos, Fusion e ACU | 4 min |
| 9 | Fechamento e próximos passos | 3 min |

> **Antes de começar:** rode o [CHECKLIST-PRE-DEMO.md](./CHECKLIST-PRE-DEMO.md). Se a sessão da Etapa 3
> não estiver pré-aquecida, a demo perde ritmo.

---

## Etapa 0 — Abertura e descoberta de contexto (5–8 min)

**Objetivo:** descobrir 2 ou 3 dores concretas para amarrar toda a demo. Cada etapa seguinte deve
começar referenciando uma dor que a pessoa acabou de citar.

Fala de abertura (usada nas duas demos):

> "Eu vou pular slide e mostrar direto o produto, porque eu sou um pouco mais técnico e gosto de
> bater um papo enquanto vocês já vão vendo a ferramenta funcionando."

Perguntas de descoberta (faça 3 ou 4, não todas):

1. Rápido round de apresentações: papel, squad e stack de cada participante.
2. Quantos desenvolvedores no time e quantos repositórios? Monorepo ou multi-repo?
3. Onde o time perde mais tempo hoje: entender código legado, escrever teste, migração/upgrade,
   correção de vulnerabilidade, ou onboarding?
4. Como está a documentação dos sistemas críticos hoje?
5. Já usam alguma ferramenta de IA? Copilot/Cursor em IDE? O que funcionou e o que não funcionou?
6. Existe esteira de segurança que trava release (SAST/DAST/CloudSec)? Quantas ocorrências abertas?

**Como usar o que ouvir** (padrão observado nas duas M1s):

| Dor citada | Etapa por onde começar |
|---|---|
| "não temos documentação" / "conhecimento na cabeça das pessoas" | Etapa 1 (Wiki) |
| "requisito chega incompleto" | Etapa 1 + Etapa 2 (chat para especificar antes de executar) |
| "migração/upgrade parado há anos" | Etapa 3 (execução real) |
| "cada dev faz do seu jeito" / falta de padrão | Etapas 4 e 5 (Knowledge + Playbooks) |
| "esteira de vulnerabilidade travando entrega" | Etapa 7 (Segurança) |
| "custo de IA imprevisível" | Etapa 8 (ACU + Fusion) |

Na Xertica, essa etapa rendeu a frase que organizou a demo inteira: *"agilidade, zero documentação e
inteligência artificial… a gente não tem documentação, não tem velocidade e a qualidade da entrega é
duvidosa"*. Anote a frase equivalente do cliente e use-a de novo no fechamento.

---

## Etapa 1 — Wiki: indexação e documentação automática (6–8 min)

**Amarração:** "Você falou que não tem documentação. Então vamos começar exatamente por aí."

**O que mostrar, na ordem:**

1. Conectar repositório: GitHub, GitLab ou Bitbucket. Explique que a indexação serve a dois
   propósitos — pessoas tirarem dúvida, e **principalmente** os agentes usarem isso como contexto.
2. Abrir a Wiki do `royal-reserve-bank` e navegar por:
   - visão de arquitetura da solução (microsserviços + infraestrutura);
   - mapeamento de componentes: `api-gateway`, `discovery-server`, `config-server`, `account-api`,
     `asset-management-api`, `transaction-api`, `notification-api`;
   - padrões arquiteturais detectados no código: Circuit Breaker (Resilience4j no `transaction-api`),
     Cache-aside (Redis), event-driven (Kafka em KRaft no `notification-api`), database-per-service
     (Mongo / MySQL / PostgreSQL), Service Discovery (Eureka), Configuração Centralizada;
   - stack de observabilidade: Micrometer, Prometheus, Grafana, Zipkin;
   - diagramas de fluxo por microsserviço (ex.: fluxo de transferência chamando `account-api` e
     `asset-management-api` via Feign com fallback).
3. Fazer uma pergunta em modo chat contra a base indexada, ao vivo — ver
   [PROMPTS.md](./PROMPTS.md#etapa-1--wiki), seção Wiki.
4. Explicar atualização configurável: por rotina (diária) ou por evento/webhook a cada merge.
   Suporta multi-repo e monorepo.
5. Deixar explícito: **consulta em modo chat contra a base indexada não consome ACU.**

**Frase de efeito:** "Ninguém escreveu uma linha dessa documentação. Ela nasceu do código, e continua
viva a cada merge — e é isso que o agente lê antes de escrever qualquer linha de código."

**Objeção esperada (Porto, Valdir):** *"documentação outras ferramentas também fazem, só dá um pouco
mais de trabalho."*
Resposta: concorde e mude o eixo — o valor não está em gerar markdown, está em ter contexto
indexado alimentando execução autônoma. Emende direto na Etapa 2/3: "vamos ver o que o agente faz
com esse contexto."

---

## Etapa 2 — Modos de interação: Chat vs. Agente (3–4 min)

**O que mostrar:**

- **Modo Chat:** conversa com o repositório indexado — estimativa, complexidade, impacto,
  dependências. Sem custo de ACU. Ótimo para tech lead/PO antes de abrir tarefa.
- **Modo Agente:** sobe uma VM na nuvem (tecnologia proprietária) em segundos, clona o repositório e
  executa a tarefa de forma autônoma. É paralelizável: dá para rodar 10 sessões simultâneas.
- **Boa prática (mostre isso, diferencia muito):** use o chat para gerar o prompt otimizado da tarefa
  e só então disparar o agente. Economiza ACU e melhora a qualidade do resultado.

**Perguntas esperadas:**

- *"Consigo conectar nos nossos próprios modelos?"* (Porto, Mario Kojima) → No Devin Desktop (IDE
  local) sim, aceita BYO-model/API própria. Na nuvem o modelo é separado por design, porque a
  proposta é escalar tarefas em paralelo com infraestrutura gerenciada.
- *"O diferencial está mais nos agentes do que na assistência em IDE?"* (Porto, Kleyton) → Sim, é o
  ponto-chave. Os times mais produtivos hoje rodam várias sessões em paralelo na nuvem; sair do
  modelo "IDE com controle visual" para orquestração de agentes é a direção do mercado.

---

## Etapa 3 — Execução real: upgrade Java 17→21 + Spring Boot (8–10 min de tela)

Esta é a etapa que vende. **Dispare a sessão no início da demo** (ou tenha uma sessão já concluída
como plano B) e volte a ela aqui — ver [PROMPTS.md](./PROMPTS.md#etapa-3--migração) para o prompt exato.

**Cenário:** subir Java 17 → 21 e Spring Boot 3.0.6 → 3.3.x em 7 módulos Maven, sem ninguém editar
`pom.xml` na mão.

**O que narrar enquanto o cliente vê a sessão:**

1. Devin lê a Wiki e os `pom.xml` (parent + 7 módulos) e monta um plano de execução antes de tocar
   em código.
2. Sobe a VM, clona o repositório e trata as dependências transitivas: Spring Cloud
   2022.0.2 → 2023.x, `testcontainers-bom`, imagem base do Jib (`eclipse-temurin` 17-jre → 21-jre),
   `java-jwt`/`jwks-rsa`.
3. Compila, **escolhe e roda os testes relevantes sem receber instrução de teste** — inclusive os de
   integração com Testcontainers.
4. Sobe a stack (`docker compose`) e valida os serviços de pé: Eureka registrando os 7 serviços,
   Zipkin recebendo traces, endpoints de actuator/Prometheus respondendo.
5. Grava a navegação como **evidência em vídeo** anexada à sessão.
6. Abre o **Pull Request** automaticamente, com descrição do que mudou e por quê.
7. Mostre que todo o histórico da sessão é auditável, incluindo as gravações de tela e cada comando
   executado.

**Ponto de venda a verbalizar:** "Esse é o tipo de tarefa que fica dois trimestres no backlog. Aqui
ela é uma sessão de agente — e dá para disparar essa mesma sessão em 30 repositórios em paralelo,
que é exatamente o que vamos ver no Playbook."

**Objeção esperada (Porto, Valdir):** *"como isso me ajuda a corrigir vulnerabilidade, garantir
retrocompatibilidade e rodar regressão integrada?"*
Resposta: puxe para Playbooks (Etapa 5) — capturar uma sessão bem-sucedida, com os testes e a
validação de regressão que **você** aprovou, e transformar em receita replicável. E complemente com
Rules (Etapa 6): "todo PR precisa passar por X antes de ser aberto" vale para todas as sessões.

---

## Etapa 4 — Knowledge (4 min)

**O que mostrar:**

- Conhecimento gerado durante as sessões (regra de nomenclatura, contrato de API, formato de erro,
  comando de build certo deste repo) fica salvo em nós reutilizáveis por toda a organização.
- Escopo: nó pode valer para um repositório, para a org (grupo) ou para a conta/empresa (cross-org).
- Regras org-level se sobrepõem às individuais — padrão único aplicado a todas as sessões.
- Devin **sugere proativamente** novos nós durante a execução, a partir de padrões que ele observou
  (ex.: "exigir teste de validação antes de mover o card no Jira").

Nós de knowledge prontos para esta demo estão em
[KNOWLEDGE-E-PLAYBOOKS.md](./KNOWLEDGE-E-PLAYBOOKS.md).

**Perguntas esperadas:**

- *"Isso é cross-usuário? É tipo lições aprendidas acumuladas?"* (Xertica, Rafael) → Sim: pode ficar
  em nível de org (todos do grupo) ou de conta/empresa (cross-org).
- *"Está condicionado a todos os desenvolvedores trabalharem dentro do ecossistema do Devin?"*
  (Porto, Valdir) → Exatamente: o padrão é garantido nas sessões de agente; o conhecimento é
  reaproveitado e sugerido automaticamente pelo próprio Devin.
- *"'Org' no Devin é a mesma coisa que organização no GitHub?"* (Porto, Kleyton) → Não: 'org' no
  Devin é um grupo. Repositórios indexados, knowledge e rules são compartilhados com quem está no
  grupo, com integração possível via AD/SSO.

---

## Etapa 5 — Playbooks (4 min)

**O que mostrar:**

1. Pegue a sessão de upgrade da Etapa 3 e gere o Playbook a partir dela.
2. Percorra a estrutura: overview, inputs necessários, passos, alertas/atenções e ações proibidas.
3. Dispare (ou mostre disparado) o mesmo playbook em vários repositórios em paralelo.
4. Economia estimada de 10–20% em ACU/tokens versus rodar a mesma tarefa do zero, porque as etapas
   já aprendidas são reaproveitadas.
5. Playbooks são editáveis e compartilhados por toda a engenharia — quem escreve é o tech lead, quem
   consome é o time inteiro.

O playbook de referência desta demo (upgrade Java/Spring Boot multi-repo, com validação e gravação de
evidência) está em [KNOWLEDGE-E-PLAYBOOKS.md](./KNOWLEDGE-E-PLAYBOOKS.md).

**Pergunta esperada (Xertica, Rafael):** *"a automação consome ACU?"* → Sim: cada sessão disparada
consome ACU, porque representa infraestrutura mais modelo em execução. Consulta em chat, não.

---

## Etapa 6 — Skills & Rules, Automações e Integrações (5 min)

**O que mostrar:**

- **Rules** aplicadas a todas as sessões (ex.: "todo PR deve rodar `mvn -B verify` nos módulos
  afetados"; "não alterar `config-files/*.properties` sem sinalizar"). Neste repositório os padrões
  vivem versionados em `AGENTS.md` e em `.agents/skills/` — mostre no próprio GitHub.
- **Skills** versionadas no repo: procedimentos testados que o agente segue sempre igual (subir a
  stack local, rodar os testes de integração com Testcontainers, upgrade de dependência).
- **Automações** — templates prontos: triagem de bug, triagem de ticket de suporte, atualização de
  dependências, gestão de backlog, status report semanal.
- **Caso real para citar:** cliente dispara uma sessão automaticamente a cada ticket de suporte
  crítico; a sessão implementa o fix e abre o PR.
- **Integrações:** Jira, Slack, GitHub, GitLab, Confluence, servidores MCP, logs de Cloudflare,
  Datadog.
- **Session Insights:** feedback pós-sessão sobre qualidade do prompt, consumo de ACU, tempo
  economizado e uso de knowledge.

**Pergunta esperada (Porto, Kleyton e William):** *"consigo chamar o agente por fora, pelo Jira ou
Slack?"* → Sim. Mencionar o Devin no Slack ou no card do Jira dispara a tarefa, e automações fazem
isso sem humano no meio.

---

## Etapa 7 — Segurança (3–4 min)

**O que mostrar:**

- A suíte de segurança roda agentes em paralelo simulando caminhos de ataque multi-camada
  atravessando **todos** os repositórios — diferente de scanner que olha um repositório isolado.
  Identifica e propõe/aplica a correção.
- Devin gera as regras de scan a partir do próprio repositório, reduzindo configuração manual.
- Caso para citar: no Itaú, hoje cerca de 70% das vulnerabilidades são corrigidas automaticamente.
- Neste repositório há material real para explorar ao vivo: JWT hardcoded em
  `config-files/api-gateway.properties`, credenciais de infraestrutura em `docker-compose.yml`,
  dependências antigas (Spring Boot 3.0.6). Use como exemplo concreto de achado.

**Pergunta esperada (Porto, Valdir — CloudSec):** *"a ferramenta de vulnerabilidade gera muitas
ocorrências e trava a esteira; como o Devin ajuda a corrigir e garantir regressão integrada?"*
Resposta: dois movimentos. (1) Triagem em escala: agentes em paralelo separam o que é exploitável do
que é ruído, com o contexto do repositório indexado. (2) Playbook de correção: capture o padrão de
correção já validado pelo time — incluindo a bateria de regressão que vocês exigem — e replique como
receita padronizada em todos os repos.

---

## Etapa 8 — Modelos, Fusion e ACU (4 min)

**O que mostrar:**

- **ACU** é a unidade da Cognition, combinando tokens de LLM **mais** infraestrutura (a VM, o
  browser, a execução de teste). Não é comparável a preço de token puro.
- **Fusion mode:** orquestra dois modelos por sessão (executor + sidekick) e escolhe automaticamente
  o modelo mais barato quando a tarefa não exige alta complexidade. Elimina a carga cognitiva de
  escolher modelo manualmente.
- **Comparativo de custo citado nas demos:** performance próxima ao modelo top de linha por
  ~US$ 10,53 contra ~US$ 35 da execução equivalente — na conversa com a Xertica isso foi resumido
  como ~40% de redução de custo com resultado equivalente. Confirme os números vigentes antes da
  demo.
- **Modelo próprio da Cognition (SWE)**, treinado internamente, com custo mais agressivo para
  tarefas de engenharia de software.

---

## Etapa 9 — Fechamento e próximos passos (3 min)

1. Retome a frase de dor da Etapa 0 e amarre com o que foi mostrado.
2. Ofereça o próximo passo concreto: POC guiada em um repositório real do cliente — indexação +
   Wiki, 1 playbook de tarefa recorrente deles, e 3 a 5 sessões reais medidas em ACU e tempo
   economizado.
3. Combine acessos necessários (repo, SSO, Jira/Slack) e a data da M2.

---

## Planos B (o que fazer se algo falhar ao vivo)

| Risco | Plano B |
|---|---|
| Sessão de upgrade travando ou lenta | Tenha uma sessão já concluída com PR aberto e gravação, e abra essa |
| Stack `docker compose` não sobe na hora | Mostre a gravação de tela da sessão anterior como evidência |
| Wiki reindexando durante a call | Use a Wiki de outro repositório já indexado |
| Internet/latência ruim | Screenshots do PR, do playbook e do Session Insights salvos localmente |

Detalhes de preparação, comandos e verificações: [CHECKLIST-PRE-DEMO.md](./CHECKLIST-PRE-DEMO.md).
