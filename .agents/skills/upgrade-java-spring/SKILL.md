---
name: upgrade-java-spring
description: Execute the validated Java and Spring Boot upgrade workflow.
---

# Upgrade Java and Spring

## Validated matrix

| Component | Current | Validated target |
|---|---|---|
| Java | 17.0.13 | 21.0.11 |
| Spring Boot parent | 3.0.6 | 3.3.4 |
| Spring Cloud BOM | 2022.0.2 | 2023.0.3 |
| Testcontainers BOM | 1.18.0 | 1.18.0 |
| Jib | 3.3.0 | 3.3.0 |
| Jib base | Temurin 17 JRE | `eclipse-temurin:21-jre` |

## Procedure

1. Work in a detached scratch worktree. Do not alter the baseline branch.
2. Update the root POM parent, `java.version`, Spring Cloud property, and Jib base property together.
3. Run:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
   PATH="/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH" \
   mvn -B -DskipTests package
   ```

4. The measured validation compiled all seven modules successfully in about 30 seconds. Then run affected tests with config-server, infrastructure, and Docker available.
5. If application startup or configuration changes, validate both local and `-docker` files in `config-files/`, service registration in Eureka, actuator health, and a browser business flow before review.

## Do not

- Do not commit the scratch migration or change business rules/API contracts.
- Do not disable or rewrite tests to hide upgrade failures.
- Do not version secrets.
- Do not change only one configuration variant.
- Do not declare an upgrade validated from compilation alone when runtime or test evidence is required.
