package com.royal.reserve.bank.transaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Data Transfer Object (DTO) class that represents the request payload for a risk assessment.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskScoreRequest implements Serializable {
    private String accountId;
    private int totalValue;
    private List<String> assetCodes;
}
