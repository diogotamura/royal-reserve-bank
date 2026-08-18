# Checklist pré-demo

Tudo abaixo foi verificado nesta stack (Java 17, Maven, Docker Engine 27.4.1). Faça na ordem.

## D-1 (dia anterior)

- [ ] Repositório `diogotamura/royal-reserve-bank` indexado e Wiki gerada. Abra a Wiki e confira se os
      7 microsserviços, os padrões arquiteturais e os diagramas estão lá.
- [ ] Knowledge notes provisionados (ver [KNOWLEDGE-E-PLAYBOOKS.md](./KNOWLEDGE-E-PLAYBOOKS.md), seção 1).
- [ ] Playbook de upgrade Java/Spring criado (seção 2 do mesmo arquivo).
- [ ] Uma automação real ligada — sugestão: status report semanal no Slack.
- [ ] **Sessão de referência pronta como plano B:** rode o prompt de migração da
      [Etapa 3](./PROMPTS.md#etapa-3--migração) inteiro no dia anterior, com PR aberto e gravação
      anexada. Se a sessão ao vivo atrasar, você mostra essa.
- [ ] Confira os números de pricing/ACU vigentes antes de citar valores na Etapa 8.

## D-0, 1 hora antes

- [ ] Subir a stack local (para a evidência no browser):

```bash
cd ~/repos/royal-reserve-bank
docker compose -f docker-compose-infrastructure-services.yml up -d   # infra
docker compose -f docker-compose.yml -f docker-compose-demo.yml up -d # stack + UI de demo
```

- [ ] Validar os pontos que você vai mostrar (todos verificados sem token):
  - Eureka (7 serviços registrados): http://localhost:8761/ e http://localhost:8761/eureka/apps
  - UI de demo: http://localhost:8085/
  - API Gateway: http://localhost:8080/
  - Config Server: http://localhost:8888/config-server/account-api/default
  - Zipkin (traces): http://localhost:9411/
  - Prometheus: http://localhost:9090/ · Grafana: http://localhost:3000/
- [ ] Rodar os testes uma vez para garantir ambiente sadio (55 testes, ~15s com a stack no ar):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B test
```

- [ ] Abrir as abas na ordem da demo e deixar prontas: Wiki → Chat → Sessão em execução → Knowledge →
      Playbooks → Rules/Automações → Segurança → Pricing.
- [ ] Fechar notificações, Slack, e-mail e qualquer aba com dado de outro cliente.
- [ ] **Disparar a sessão de migração da Etapa 3 agora**, antes da call começar. Ela roda durante a
      Etapa 0 e 1 e chega com resultado na hora certa.

## Armadilhas conhecidas deste repositório

| Armadilha | O que fazer |
|---|---|
| Testes falham com `ConfigClientFailFastException: Connection refused localhost:8888` | O config-server e a infra precisam estar no ar antes de `mvn test` |
| Rotas de negócio do gateway devolvem 401 | O JWT versionado em `config-files/api-gateway.properties` está expirado; use o perfil `demo` (`docker-compose-demo.yml`), que é o caminho preparado para a navegação no browser |
| Comandos do README usam sintaxe antiga (`docker-compose <arquivo> up`) | Use `docker compose -f <arquivo> up -d` |
| Módulos `config-server`, `discovery-server` e `api-gateway` "não têm testes" | Eles só têm classes `*IT`, fora do padrão do Surefire — não é ausência de teste |
| Não há Swagger/OpenAPI no projeto | A navegação visual é pela UI de demo (`docs/demo/demo-ui/`), Eureka, Zipkin e Grafana |

## Fatos verificados que valem citar ao vivo

- O upgrade Java 17 → 21 com Spring Boot 3.0.6 → 3.3.4 e Spring Cloud 2022.0.2 → 2023.0.3 **compila
  os 7 módulos sem erro** (validado em dry-run, ~30s de build). Ou seja: a demo de migração é segura,
  e o valor é o agente fazer a matriz de compatibilidade e a validação, não o cliente descobrir o que
  quebra.
- Os testes de integração usam Testcontainers de verdade (`MongoDBContainer("mongo:6.0.5")`) — bom
  argumento quando perguntarem se o agente "roda teste sério".
- Achados de segurança reais para a Etapa 7: JWT hardcoded em `config-files/api-gateway.properties`,
  credenciais fixas no `docker-compose.yml`, dependências desatualizadas.

## Pós-demo

- [ ] Enviar ao cliente: link do PR gerado ao vivo, a gravação da sessão e o playbook usado.
- [ ] Registrar em knowledge as dores citadas por eles (vira contexto para a M2).
- [ ] `docker compose -f docker-compose.yml -f docker-compose-demo.yml down` para liberar a máquina.
