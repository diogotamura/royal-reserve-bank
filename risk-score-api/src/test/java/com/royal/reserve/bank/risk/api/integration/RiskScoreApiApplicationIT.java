package com.royal.reserve.bank.risk.api.integration;

import com.royal.reserve.bank.risk.api.RiskScoreApiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for the {@link RiskScoreApiApplication} class.
 */
@SpringBootTest
class RiskScoreApiApplicationIT {
    @Test
    void contextLoads() {
        Assertions.assertDoesNotThrow(() -> {
            RiskScoreApiApplication.main(new String[]{});
        });
    }
}
