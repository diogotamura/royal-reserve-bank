# Perguntas e objeções recorrentes (cheat sheet)

Consolidado das M1s de Porto Seguro e Xertica, com as respostas que funcionaram. Use como consulta
rápida durante o Q&A.

## Contexto e requisitos

**"Como a ferramenta se adapta quando o cliente não entrega levantamento completo de requisitos?
Qual o mínimo necessário para o Devin operar?"** (Xertica, Carol)
O mínimo é o código indexado — a Wiki dá ao agente o contexto que o requisito não deu. Além disso,
use o modo chat (sem custo) para transformar um pedido vago em especificação: o Devin faz as
perguntas, aponta impacto e módulos afetados, e só então você dispara o agente. Requisito incompleto
deixa de ser bloqueio e passa a ser uma conversa de 5 minutos.

**"Documentação outras ferramentas também fazem, só dá um pouco mais de trabalho."** (Porto, Valdir)
Concorde e mude o eixo: o valor não é gerar markdown, é ter uma base indexada que alimenta execução
autônoma. A documentação aqui é insumo do agente, não entregável final — e é o que permite que o
agente migre 7 módulos sem alguém explicar a arquitetura para ele.

## Modelos e infraestrutura

**"Consigo conectar nos nossos próprios modelos?"** (Porto, Mario Kojima)
No Devin Desktop (IDE local), sim — aceita modelo/API próprios. Na nuvem, o modelo é separado por
design: a proposta é escalar sessões em paralelo com infraestrutura gerenciada, e isso exige
controle do stack de execução.

**"O diferencial está mais nos agentes do que na assistência de codificação em IDE?"**
(Porto, Kleyton)
Sim, é o ponto-chave. Os times mais produtivos hoje rodam várias sessões em paralelo na nuvem. Sair
do modelo "IDE com controle visual" para orquestração de agentes é a direção inevitável.

## Padronização e governança

**"'Org' no Devin é a organização do GitHub?"** (Porto, Kleyton)
Não. 'Org' no Devin é um grupo: repositórios indexados, knowledge e rules são compartilhados com
todos os membros do grupo. Integra com AD/SSO.

**"Isso está condicionado a todos os desenvolvedores trabalharem dentro do ecossistema do Devin
para seguirem os padrões?"** (Porto, Valdir)
Nas sessões de agente, o padrão é garantido por construção (rules org-level se sobrepõem às
individuais). Fora delas, o que se ganha é padrão versionado no repositório (`AGENTS.md`,
`.agents/skills/`) que também serve de referência para humanos.

**"Knowledge é cross-usuário? É como lições aprendidas?"** (Xertica, Rafael)
Sim. Pode viver em nível de org (grupo) ou de conta/empresa (cross-org), e o Devin sugere novos nós
proativamente durante as sessões.

## Qualidade, segurança e regressão

**"Como o Devin ajuda a corrigir vulnerabilidade, garantir retrocompatibilidade e rodar regressão
integrada?"** (Porto, Valdir — dor real com CloudSec travando a esteira)
Dois movimentos: (1) triagem em escala — agentes em paralelo, com contexto do repositório indexado,
separam o explorável do ruído em vez de despejar centenas de ocorrências; (2) Playbook de correção —
capture o padrão de fix já validado pelo time, incluindo a bateria de regressão exigida, e replique
como receita padronizada em todos os repositórios. Rules garantem que nenhum PR nasça sem essa
validação.

**"Como sei que o agente não quebrou nada?"**
Toda sessão é auditável: cada comando, cada diff, os testes executados e a gravação de tela da
validação. O PR chega com a evidência anexada, não com uma promessa.

## Integrações e automação

**"Consigo disparar o agente por fora, via Jira ou Slack?"** (Porto, Kleyton e William Campos)
Sim. Mencionar o Devin no Slack ou no card do Jira abre a sessão; automações disparam sem humano no
meio (ex.: cada ticket crítico de suporte gera uma sessão que implementa o fix e abre o PR).
Integrações: Jira, Slack, GitHub, GitLab, Confluence, MCP, Cloudflare, Datadog.

## Custo

**"A automação consome ACU?"** (Xertica, Rafael)
Sim: cada sessão disparada consome ACU, porque representa infraestrutura mais modelo em execução.
Consulta em modo chat contra a base indexada, não.

**"Como comparo custo com as outras ferramentas?"**
ACU combina tokens de LLM e infraestrutura (VM, browser, execução de testes), então não é comparável
a preço de token puro. Traga o comparativo de execução equivalente (~US$ 10,53 em Fusion contra
~US$ 35 no modelo top de linha, ~40% de redução com resultado equivalente) e o modelo próprio (SWE)
para tarefas de engenharia. Confirme os números vigentes antes de citar.

**"Como controlo gasto?"**
Fusion escolhe automaticamente o modelo mais barato quando a tarefa não exige alta complexidade;
Playbooks reduzem 10–20% por reaproveitar etapas já aprendidas; chat não consome; e Session Insights
mostra por sessão o consumo, a qualidade do prompt e o tempo economizado.
