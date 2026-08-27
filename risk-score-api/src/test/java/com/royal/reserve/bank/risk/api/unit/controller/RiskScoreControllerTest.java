package com.royal.reserve.bank.risk.api.unit.controller;

import com.royal.reserve.bank.risk.api.controller.RiskScoreController;
import com.royal.reserve.bank.risk.api.dto.RiskLevel;
import com.royal.reserve.bank.risk.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.risk.api.dto.RiskScoreResponse;
import com.royal.reserve.bank.risk.api.service.RiskScoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link RiskScoreController} class.
 */
@ExtendWith(MockitoExtension.class)
class RiskScoreControllerTest {

    @Mock
    private RiskScoreService riskScoreService;

    /**
     * Test for the {@link RiskScoreController#evaluateRisk(RiskScoreRequest)} method.
     */
    @Test
    void testEvaluateRiskReturnsServiceResponse() {
        // Given
        RiskScoreController riskScoreController = new RiskScoreController(riskScoreService);
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId("ACC-123")
                .totalValue(20_000)
                .assetCodes(List.of("MSFT", "COIN"))
                .build();
        RiskScoreResponse expectedResponse = RiskScoreResponse.builder()
                .score(29)
                .riskLevel(RiskLevel.LOW)
                .approved(true)
                .build();
        when(riskScoreService.evaluateRisk(request)).thenReturn(expectedResponse);

        // When
        RiskScoreResponse response = riskScoreController.evaluateRisk(request);

        // Then
        verify(riskScoreService, times(1)).evaluateRisk(request);
        assertEquals(expectedResponse, response);
    }
}
