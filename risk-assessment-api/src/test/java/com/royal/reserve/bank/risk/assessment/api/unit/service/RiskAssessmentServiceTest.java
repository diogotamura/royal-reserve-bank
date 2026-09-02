package com.royal.reserve.bank.risk.assessment.api.unit.service;

import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentItem;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskLevel;
import com.royal.reserve.bank.risk.assessment.api.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link RiskAssessmentService} class.
 */
class RiskAssessmentServiceTest {

    private final RiskAssessmentService riskAssessmentService = new RiskAssessmentService(5000, 10000);

    @Test
    void assessBelowMediumThreshold_shouldBeLowRiskAndApproved() {
        RiskAssessmentRequest request = new RiskAssessmentRequest("tx-1",
                List.of(new RiskAssessmentItem("DVN", "Devon Energy Corporation", 100)));

        RiskAssessmentResponse response = riskAssessmentService.assess(request);

        assertEquals("tx-1", response.getTransactionId());
        assertEquals(RiskLevel.LOW, response.getRiskLevel());
        assertEquals(100, response.getTotalValue());
        assertTrue(response.isApproved());
    }

    @Test
    void assessBetweenThresholds_shouldBeMediumRiskAndApproved() {
        RiskAssessmentRequest request = new RiskAssessmentRequest("tx-2",
                List.of(new RiskAssessmentItem("NVDA", "NVIDIA", 3000),
                        new RiskAssessmentItem("NFLX", "Netflix", 2500)));

        RiskAssessmentResponse response = riskAssessmentService.assess(request);

        assertEquals(RiskLevel.MEDIUM, response.getRiskLevel());
        assertEquals(5500, response.getTotalValue());
        assertTrue(response.isApproved());
    }

    @Test
    void assessAtHighThreshold_shouldBeHighRiskAndRejected() {
        RiskAssessmentRequest request = new RiskAssessmentRequest("tx-3",
                List.of(new RiskAssessmentItem("QQQ", "Invesco QQQ", 10000)));

        RiskAssessmentResponse response = riskAssessmentService.assess(request);

        assertEquals(RiskLevel.HIGH, response.getRiskLevel());
        assertFalse(response.isApproved());
        assertTrue(response.getReason().contains("10000"));
    }

    @Test
    void assessWithoutItems_shouldBeLowRiskAndApproved() {
        RiskAssessmentResponse response = riskAssessmentService.assess(new RiskAssessmentRequest("tx-4", null));

        assertEquals(RiskLevel.LOW, response.getRiskLevel());
        assertEquals(0, response.getTotalValue());
        assertTrue(response.isApproved());
    }
}
