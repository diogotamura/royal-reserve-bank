package com.royal.reserve.bank.risk.api.unit.service;

import com.royal.reserve.bank.risk.api.dto.RiskLevel;
import com.royal.reserve.bank.risk.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.risk.api.dto.RiskScoreResponse;
import com.royal.reserve.bank.risk.api.service.RiskScoreService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link RiskScoreService} class.
 */
class RiskScoreServiceTest {

    private final RiskScoreService riskScoreService = new RiskScoreService();

    /**
     * Test for the {@link RiskScoreService#evaluateRisk(RiskScoreRequest)} method.
     */
    @Test
    void evaluateRiskWithLowValueAndKnownAccount_shouldReturnLowRiskAndApprove() {
        // Given
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId("ACC-123")
                .totalValue(500)
                .assetCodes(List.of("MSFT"))
                .build();

        // When
        RiskScoreResponse response = riskScoreService.evaluateRisk(request);

        // Then
        assertEquals(2, response.getScore());
        assertEquals(RiskLevel.LOW, response.getRiskLevel());
        assertTrue(response.isApproved());
    }

    /**
     * Test for the {@link RiskScoreService#evaluateRisk(RiskScoreRequest)} method.
     */
    @Test
    void evaluateRiskWithMediumValue_shouldReturnMediumRiskAndApprove() {
        // Given
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId("ACC-123")
                .totalValue(20_000)
                .assetCodes(Arrays.asList("MSFT", "COIN", "QQQ"))
                .build();

        // When
        RiskScoreResponse response = riskScoreService.evaluateRisk(request);

        // Then
        assertEquals(31, response.getScore());
        assertEquals(RiskLevel.MEDIUM, response.getRiskLevel());
        assertTrue(response.isApproved());
    }

    /**
     * Test for the {@link RiskScoreService#evaluateRisk(RiskScoreRequest)} method.
     */
    @Test
    void evaluateRiskWithHighValueAndUnknownAccount_shouldReturnHighRiskAndReject() {
        // Given
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId(null)
                .totalValue(150_000)
                .assetCodes(Arrays.asList("MSFT", "COIN"))
                .build();

        // When
        RiskScoreResponse response = riskScoreService.evaluateRisk(request);

        // Then
        assertEquals(84, response.getScore());
        assertEquals(RiskLevel.HIGH, response.getRiskLevel());
        assertFalse(response.isApproved());
    }

    /**
     * Test for the {@link RiskScoreService#evaluateRisk(RiskScoreRequest)} method.
     */
    @Test
    void evaluateRiskWithManyItems_shouldCapItemPoints() {
        // Given
        List<String> assetCodes = Arrays.asList(
                "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12");
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId("ACC-123")
                .totalValue(0)
                .assetCodes(assetCodes)
                .build();

        // When
        RiskScoreResponse response = riskScoreService.evaluateRisk(request);

        // Then
        assertEquals(20, response.getScore());
        assertEquals(RiskLevel.LOW, response.getRiskLevel());
        assertTrue(response.isApproved());
    }

    /**
     * Test for the {@link RiskScoreService#evaluateRisk(RiskScoreRequest)} method.
     */
    @Test
    void evaluateRiskWithNullAssetCodes_shouldNotAddItemPoints() {
        // Given
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId("ACC-123")
                .totalValue(1_000)
                .assetCodes(null)
                .build();

        // When
        RiskScoreResponse response = riskScoreService.evaluateRisk(request);

        // Then
        assertEquals(10, response.getScore());
        assertEquals(RiskLevel.LOW, response.getRiskLevel());
        assertTrue(response.isApproved());
    }
}
