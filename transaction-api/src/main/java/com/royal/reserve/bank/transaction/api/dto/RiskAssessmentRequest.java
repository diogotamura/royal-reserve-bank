package com.royal.reserve.bank.transaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object (DTO) class that represents the request payload sent to the Risk Assessment API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskAssessmentRequest {
    private String transactionId;
    private String accountId;
    private List<RiskAssessmentItem> items;
}
