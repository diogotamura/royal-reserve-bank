# Prompts prontos para a demo M1

Prompts para copiar e colar durante a demo, na ordem do [ROTEIRO-M1.md](./ROTEIRO-M1.md).
Repositório alvo: `diogotamura/royal-reserve-bank`.

Convenção: **[CHAT]** roda em modo chat contra a base indexada (não consome ACU) e **[AGENTE]** abre
uma sessão com VM (consome ACU).

---

## Etapa 1 — Wiki

Perguntas com efeito visual bom porque a resposta cita arquivos e serviços reais:

**[CHAT]**
```
Explique o fluxo completo de uma transferência neste sistema, do API Gateway até a
notificação por Kafka. Diga quais serviços participam, quais bancos de dados são
tocados e onde está o circuit breaker.
```

**[CHAT]**
```
Sou um desenvolvedor novo no time. Preciso adicionar um campo "currency" na criação de
conta. Quais arquivos eu tenho que mudar, em quais serviços, e o que quebra em cascata?
```

**[CHAT]**
```
Quais padrões arquiteturais estão implementados neste repositório e onde exatamente
cada um está no código?
```

---

## Etapa 2 — Chat gerando o prompt do agente

Boa prática que diferencia: usar o chat para especificar antes de gastar ACU.

**[CHAT]**
```
Quero subir este projeto de Java 17 para Java 21 e de Spring Boot 3.0.6 para a última
3.3.x, incluindo Spring Cloud e Testcontainers. Antes de executar: monte o prompt ideal
para uma sessão de agente fazer isso, listando os módulos afetados, as dependências
transitivas que precisam subir junto, os riscos de breaking change e como validar o
resultado. Não execute nada agora.
```

Mostre a resposta e diga: "esse prompt otimizado é o que eu vou jogar no agente agora — o chat me
custou zero."

---

## Etapa 3 — Migração

Prompt principal da demo. Dispare no **início** da reunião e volte a ele na Etapa 3.

**[AGENTE]**
```
Faça o upgrade deste projeto Maven multi-módulo de Java 17 para Java 21 e do parent
spring-boot-starter-parent de 3.0.6 para a última versão estável da linha 3.3.x.

Escopo:
- pom.xml raiz e os 7 módulos (config-server, discovery-server, api-gateway,
  account-api, asset-management-api, transaction-api, notification-api).
- Suba as dependências transitivas necessárias: spring-cloud-dependencies
  (2022.0.2 -> linha 2023.x compatível), testcontainers-bom, jib-maven-plugin e a
  imagem base do Jib (eclipse-temurin 17-jre -> 21-jre).
- Corrija o que quebrar de compilação por breaking change (Spring Cloud Gateway,
  Resilience4j, Spring Security/OAuth2 Resource Server, JPA/Hibernate).

Validação obrigatória antes de abrir o PR:
- Build completo passando.
- Rode os testes dos módulos afetados, incluindo os de integração com Testcontainers.
- Suba a stack com docker compose e comprove que os serviços registram no Eureka e que
  os endpoints de actuator/health respondem.
- Grave a navegação da validação como evidência e anexe à sessão.

Abra um Pull Request explicando o que mudou, por que, e o que foi validado. Não altere
regra de negócio nem contrato de API.
```

Variante mais impressionante se o cliente citou legado antigo — troque a primeira linha por
"de Java 8 para Java 21" em um repositório mais antigo, se houver um indexado.

---

## Etapa 3b — Tarefa curta de backup (se a migração demorar)

Sessão rápida, boa para mostrar ciclo completo em poucos minutos:

**[AGENTE]**
```
O account-api hoje não valida o payload de criação de conta. Adicione validação com
Bean Validation nos DTOs de request do account-api e do transaction-api, retornando 400
com um corpo de erro padronizado, e cubra com teste unitário. Rode os testes dos módulos
afetados e abra um PR.
```

---

## Etapa 4 — Knowledge

Mostre o Devin sugerindo conhecimento a partir do que aprendeu na sessão:

**[CHAT]**
```
A partir do que você aprendeu neste repositório, quais nós de knowledge você sugere
criar para que qualquer sessão futura acerte de primeira? Foque em comando de build
correto, ordem de subida dos serviços, padrão de nomenclatura e contrato de erro das
APIs.
```

---

## Etapa 5 — Playbooks

**[CHAT]**
```
Gere um playbook a partir da sessão de upgrade que acabamos de rodar, para ser
reaproveitado em outros repositórios Java/Spring Boot: overview, inputs necessários,
passos, pontos de atenção e ações proibidas.
```

Depois mostre o disparo em paralelo (mesmo playbook, múltiplos repositórios).

---

## Etapa 6 — Skills, Rules e Automações

**[CHAT]**
```
Quais rules você recomenda para este repositório, considerando que ele é um sistema
bancário com 7 microsserviços, testes com Testcontainers e configuração externalizada
em config-files/?
```

Mostre em seguida, no GitHub, o `AGENTS.md` e o diretório `.agents/skills/` deste repositório —
padrões versionados junto com o código, que toda sessão passa a seguir.

---

## Etapa 7 — Segurança

**[CHAT]**
```
Faça uma varredura de risco de segurança neste repositório e me diga o que exporia
dados ou permitiria acesso indevido em produção: segredos versionados, configuração de
autenticação do gateway, credenciais de infraestrutura no docker-compose e dependências
com vulnerabilidade conhecida. Priorize por explorabilidade real, não por severidade
teórica.
```

**[AGENTE]** (opcional, se houver tempo e o cliente pedir prova)
```
Remova o JWT hardcoded de config-files/api-gateway.properties e as credenciais fixas do
docker-compose.yml, substituindo por variáveis de ambiente com valores default apenas
para desenvolvimento local. Atualize o README e a documentação de execução local. Não
quebre a subida da stack: valide com docker compose antes de abrir o PR.
```

Esse último é excelente na demo porque é um achado real deste repositório, corrigido ao vivo.
