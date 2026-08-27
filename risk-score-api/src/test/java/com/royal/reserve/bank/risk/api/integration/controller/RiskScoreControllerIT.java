package com.royal.reserve.bank.risk.api.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.royal.reserve.bank.risk.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.risk.api.service.RiskScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import(RiskScoreService.class)
class RiskScoreControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testEvaluateRiskApprovesLowRiskTransaction() throws Exception {
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId("ACC-123")
                .totalValue(500)
                .assetCodes(List.of("MSFT"))
                .build();
        String jsonRequest = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/risk-score")
                        .content(jsonRequest)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void testEvaluateRiskRejectsHighRiskTransaction() throws Exception {
        RiskScoreRequest request = RiskScoreRequest.builder()
                .accountId(null)
                .totalValue(150_000)
                .assetCodes(Arrays.asList("MSFT", "COIN"))
                .build();
        String jsonRequest = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/risk-score")
                        .content(jsonRequest)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.approved").value(false));
    }
}
