package com.royal.reserve.bank.transaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) class that represents the response payload for a risk assessment.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskScoreResponse implements Serializable {
    private int score;
    private RiskLevel riskLevel;
    private boolean approved;
}
