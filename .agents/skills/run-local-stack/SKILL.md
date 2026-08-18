---
name: run-local-stack
description: Start and validate the Royal Reserve Bank local Docker stack.
---

# Run the local stack

1. Start infrastructure first:

   ```bash
   docker compose -f docker-compose-infrastructure-services.yml up -d
   ```

2. Start the application stack:

   ```bash
   docker compose up -d
   ```

   Startup is dependency-driven but services may restart once while config-server becomes ready. The stable order is config-server, discovery-server, api-gateway, then the business APIs.

3. Validate the running stack:

   ```bash
   curl -fsS http://localhost:8761/eureka/apps
   curl -fsS http://localhost:8888/config-server/account-api/default
   curl -fsS http://localhost:9411/
   curl -fsS http://localhost:9090/-/ready
   curl -fsS http://localhost:3000/api/health
   docker ps
   ```

   Key URLs are Gateway `:8080`, Eureka `:8761`, Config Server `:8888`, Grafana `:3000`, Prometheus `:9090`, and Zipkin `:9411`.

4. For the browser demo, build the changed gateway image and use the local-only override:

   ```bash
   mvn -B -pl api-gateway -DskipTests package jib:dockerBuild \
     -Djib.to.image=royal-reserve-bank/api-gateway:demo
   docker compose -f docker-compose.yml -f docker-compose-demo.yml up -d
   ```

   Open `http://localhost:8085`. The override enables the opt-in `demo` Spring profile and serves the static UI. It is not for real environments.

5. Stop the stack when finished:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose-demo.yml down
   docker compose -f docker-compose-infrastructure-services.yml down
   ```
