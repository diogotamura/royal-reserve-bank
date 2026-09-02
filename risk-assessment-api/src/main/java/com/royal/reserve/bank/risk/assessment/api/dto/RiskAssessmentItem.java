package com.royal.reserve.bank.risk.assessment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) class that represents a single item of the transaction being assessed.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskAssessmentItem {
    private String assetCode;
    private String assetName;
    private int value;
}
