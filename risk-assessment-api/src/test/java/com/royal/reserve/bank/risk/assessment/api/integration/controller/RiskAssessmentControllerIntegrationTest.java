package com.royal.reserve.bank.risk.assessment.api.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.royal.reserve.bank.risk.assessment.api.controller.RiskAssessmentController;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentItem;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration tests for the {@link RiskAssessmentController} class.
 */
@WebMvcTest(RiskAssessmentController.class)
class RiskAssessmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RiskAssessmentService riskAssessmentService;

    /**
     * Test for the {@link RiskAssessmentController#assessRisk(RiskAssessmentRequest)} method.
     *
     * @throws Exception if an exception occurs during the test
     */
    @Test
    void testAssessRiskApproved() throws Exception {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .transactionId("tx-1")
                .items(List.of(new RiskAssessmentItem("NVDA", "NVIDIA", 100)))
                .build();
        when(riskAssessmentService.assessRisk(any(RiskAssessmentRequest.class)))
                .thenReturn(new RiskAssessmentResponse(10, true));

        // When and Then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.riskScore").value(10))
                .andExpect(jsonPath("$.approved").value(true));
    }

    /**
     * Test for the {@link RiskAssessmentController#assessRisk(RiskAssessmentRequest)} method.
     *
     * @throws Exception if an exception occurs during the test
     */
    @Test
    void testAssessRiskRejected() throws Exception {
        // Given
        RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .transactionId("tx-2")
                .items(List.of(new RiskAssessmentItem("TSLA", "Tesla", 999999)))
                .build();
        when(riskAssessmentService.assessRisk(any(RiskAssessmentRequest.class)))
                .thenReturn(new RiskAssessmentResponse(100, false));

        // When and Then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.riskScore").value(100))
                .andExpect(jsonPath("$.approved").value(false));
    }
}
