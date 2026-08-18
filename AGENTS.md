# Repository working rules

## Build and test

- Use Java 17 for the current baseline. The validated baseline build is `mvn -B -DskipTests package`.
- The project build documented for local installation is `mvn clean install -pl '!config-server'`.
- Start `config-server` before tests or application contexts. Without it, tests fail with `ConfigClientFailFastException` and `Connection refused` on `localhost:8888`.
- Start infrastructure before tests: `docker compose -f docker-compose-infrastructure-services.yml up -d`.
- Docker must be running because `account-api` uses MongoDB Testcontainers.
- With config-server and infrastructure available, `mvn -B test` passes the 55 tests selected by Surefire. Classes ending in `*IT` are not selected by the default Surefire patterns.

## Service topology

- Start in this order: config-server, discovery-server, api-gateway, then the business APIs.
- Keep database-per-service boundaries: MongoDB belongs to `account-api`, MySQL to `asset-management-api`, and PostgreSQL to `transaction-api`. Do not cross service database boundaries.
- Synchronous service calls use Feign clients with Resilience4j fallbacks. Notifications are asynchronous through Kafka.
- External business routes go through the API Gateway; do not expose business-service ports as a shortcut.
- When adding configuration, update both `config-files/<service>.properties` and `config-files/<service>-docker.properties`.

## Security and review

- Never commit tokens, passwords, private keys, or other secrets. Use environment variables for new sensitive configuration.
- A PR is ready only after the affected modules build and test, and changes to dependencies, configuration, or startup are validated with the relevant stack running. Include command output and runtime evidence in the PR description.
