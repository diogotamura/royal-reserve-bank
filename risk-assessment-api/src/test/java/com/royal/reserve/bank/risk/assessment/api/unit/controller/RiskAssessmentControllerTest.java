package com.royal.reserve.bank.risk.assessment.api.unit.controller;

import com.royal.reserve.bank.risk.assessment.api.controller.RiskAssessmentController;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskLevel;
import com.royal.reserve.bank.risk.assessment.api.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link RiskAssessmentController} class.
 */
@ExtendWith(MockitoExtension.class)
class RiskAssessmentControllerTest {

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @Test
    void assess_shouldDelegateToService() {
        RiskAssessmentController controller = new RiskAssessmentController(riskAssessmentService);
        RiskAssessmentRequest request = new RiskAssessmentRequest("tx-1", List.of());
        RiskAssessmentResponse expected = RiskAssessmentResponse.builder()
                .transactionId("tx-1").riskLevel(RiskLevel.LOW).approved(true).build();
        when(riskAssessmentService.assess(request)).thenReturn(expected);

        RiskAssessmentResponse response = controller.assess(request);

        verify(riskAssessmentService, times(1)).assess(request);
        assertEquals(expected, response);
    }
}
