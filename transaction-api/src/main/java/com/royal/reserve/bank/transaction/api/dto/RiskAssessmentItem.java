package com.royal.reserve.bank.transaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) class that represents a transaction item sent for risk assessment.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskAssessmentItem {
    private String assetCode;
    private String assetName;
    private int value;
}
