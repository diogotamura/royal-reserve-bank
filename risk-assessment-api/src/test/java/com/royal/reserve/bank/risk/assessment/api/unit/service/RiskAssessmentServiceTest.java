package com.royal.reserve.bank.risk.assessment.api.unit.service;

import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentItem;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.model.RiskAssessment;
import com.royal.reserve.bank.risk.assessment.api.repository.RiskAssessmentRepository;
import com.royal.reserve.bank.risk.assessment.api.service.RiskAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the {@link RiskAssessmentService} class.
 */
@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    private static final long MAX_TRANSACTION_VALUE = 10000L;
    private static final int MAX_ITEMS = 3;

    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;

    private RiskAssessmentService riskAssessmentService;

    @BeforeEach
    void setUp() {
        riskAssessmentService = new RiskAssessmentService(riskAssessmentRepository, MAX_TRANSACTION_VALUE, MAX_ITEMS);
    }

    /**
     * Test for the {@link RiskAssessmentService#assessRisk(RiskAssessmentRequest)} method.
     */
    @Test
    void assessRiskWithinLimits_shouldApproveAndPersist() {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .transactionId("tx-1")
                .items(List.of(new RiskAssessmentItem("NVDA", "NVIDIA", 2000),
                        new RiskAssessmentItem("NFLX", "Netflix", 3000)))
                .build();

        // When
        RiskAssessmentResponse response = riskAssessmentService.assessRisk(request);

        // Then
        assertTrue(response.isApproved());
        assertEquals(66, response.getRiskScore());
        ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
        verify(riskAssessmentRepository, times(1)).save(captor.capture());
        RiskAssessment saved = captor.getValue();
        assertEquals("tx-1", saved.getTransactionId());
        assertTrue(saved.isApproved());
        assertEquals(66, saved.getRiskScore());
        assertNotNull(saved.getEvaluatedAt());
    }

    /**
     * Test for the {@link RiskAssessmentService#assessRisk(RiskAssessmentRequest)} method.
     */
    @Test
    void assessRiskExceedingMaxTransactionValue_shouldReject() {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .transactionId("tx-2")
                .items(List.of(new RiskAssessmentItem("TSLA", "Tesla", 15000)))
                .build();

        // When
        RiskAssessmentResponse response = riskAssessmentService.assessRisk(request);

        // Then
        assertFalse(response.isApproved());
        assertEquals(100, response.getRiskScore());
        verify(riskAssessmentRepository, times(1)).save(any(RiskAssessment.class));
    }

    /**
     * Test for the {@link RiskAssessmentService#assessRisk(RiskAssessmentRequest)} method.
     */
    @Test
    void assessRiskExceedingMaxItems_shouldReject() {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .transactionId("tx-3")
                .items(List.of(new RiskAssessmentItem("A", "A", 10),
                        new RiskAssessmentItem("B", "B", 10),
                        new RiskAssessmentItem("C", "C", 10),
                        new RiskAssessmentItem("D", "D", 10)))
                .build();

        // When
        RiskAssessmentResponse response = riskAssessmentService.assessRisk(request);

        // Then
        assertFalse(response.isApproved());
        assertEquals(100, response.getRiskScore());
        verify(riskAssessmentRepository, times(1)).save(any(RiskAssessment.class));
    }

    /**
     * Test for the {@link RiskAssessmentService#assessRisk(RiskAssessmentRequest)} method.
     */
    @Test
    void assessRiskAtExactLimits_shouldApprove() {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .transactionId("tx-4")
                .items(List.of(new RiskAssessmentItem("A", "A", 4000),
                        new RiskAssessmentItem("B", "B", 3000),
                        new RiskAssessmentItem("C", "C", 3000)))
                .build();

        // When
        RiskAssessmentResponse response = riskAssessmentService.assessRisk(request);

        // Then
        assertTrue(response.isApproved());
        assertEquals(100, response.getRiskScore());
    }

    /**
     * Test for the {@link RiskAssessmentService#assessRisk(RiskAssessmentRequest)} method.
     */
    @Test
    void assessRiskWithNoItems_shouldApproveWithZeroScore() {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder().transactionId("tx-5").build();

        // When
        RiskAssessmentResponse response = riskAssessmentService.assessRisk(request);

        // Then
        assertTrue(response.isApproved());
        assertEquals(0, response.getRiskScore());
    }
}
