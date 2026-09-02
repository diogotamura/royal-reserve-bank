package com.royal.reserve.bank.risk.assessment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) class that represents a single transaction item to be assessed.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskAssessmentItem {
    private String assetCode;
    private String assetName;
    private int value;
}
