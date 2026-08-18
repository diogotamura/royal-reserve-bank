---
name: run-tests
description: Run the validated Maven test procedure for this repository.
---

# Run tests

1. Use Java 17 for the current baseline:

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
   export PATH="$JAVA_HOME/bin:$PATH"
   ```

2. Start infrastructure and config-server before testing. The application Docker stack may remain running, but the config server on `localhost:8888` is mandatory:

   ```bash
   docker compose -f docker-compose-infrastructure-services.yml up -d
   docker compose up -d config-server
   ```

3. Confirm Docker for Testcontainers:

   ```bash
   docker ps
   ```

   `account-api` uses `MongoDBContainer`; the Docker daemon must be reachable.

4. Run the validated command:

   ```bash
   mvn -B test
   ```

   With the required services running, the measured result was 55 tests, 0 failures, and 0 errors across account-api, asset-management-api, transaction-api, and notification-api.

5. The default Surefire execution does not select classes ending in `*IT`. The repository has no configured Failsafe execution, so do not claim those integration classes ran unless they are invoked explicitly.

If config-server is unavailable, expect `ConfigClientFailFastException` with `Connection refused` for `http://localhost:8888/config-server/...`. Fix service startup first; do not disable config import in tests.
