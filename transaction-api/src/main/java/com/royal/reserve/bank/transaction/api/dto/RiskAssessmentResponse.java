package com.royal.reserve.bank.transaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) class that represents the result of a risk assessment.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskAssessmentResponse {
    private String transactionId;
    private String riskLevel;
    private int totalValue;
    private boolean approved;
    private String reason;
}
