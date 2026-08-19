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

   Notes learned while testing the demo UI:

   - Build the gateway image with Java 17 (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`); Java 21 fails on the pinned Lombok.
   - After a machine restart the containers are gone but images/volumes survive: re-run steps 1 and 4; `config-server` may exit 255 once before stabilising.
   - Give the stack ~2 minutes. Until `account-api` registers, `GET http://localhost:8080/api/account` returns a 503 `Service Unavailable` JSON body, which the UI surfaces as "Could not load accounts". Confirm readiness with
     `curl -s http://localhost:8761/eureka/apps | grep -o '<name>[^<]*'` (expect CONFIG-SERVER, DISCOVERY registrations, API-GATEWAY, ACCOUNT-API, TRANSACTION-API, ASSET-MANAGEMENT-API, NOTIFICATION-API).
   - Seeded asset codes come from `asset-management-api/.../util/AssetTestData.java`; availability is `value > 0`, so `SEC`/`BTC`/`INV`/`LEASE` succeed and `DERIV` (value 0) triggers the Resilience4j fallback `Oops! Something went wrong, please try again later!`.
   - To check that the non-demo path is still JWT-protected without touching the compose stack, run a throwaway gateway on the same network:

     ```bash
     docker run -d --rm --name gw-nodemo --network royal-reserve-bank_default -p 8081:8080 \
       -e spring.profiles.active=docker \
       -e spring.cloud.config.uri=http://config-server:8888/config-server \
       royal-reserve-bank/api-gateway:demo
     sleep 60 && curl -i http://localhost:8081/api/account   # expect 401 Unauthorized
     docker stop gw-nodemo
     ```

5. Stop the stack when finished:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose-demo.yml down
   docker compose -f docker-compose-infrastructure-services.yml down
   ```
