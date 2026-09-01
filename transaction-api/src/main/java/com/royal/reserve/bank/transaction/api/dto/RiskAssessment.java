package com.royal.reserve.bank.transaction.api.dto;

import com.royal.reserve.bank.transaction.api.model.RiskLevel;

/**
 * Data Transfer Object (DTO) class that represents the risk assessment of a transaction.
 *
 * @param score the risk score, between 0 and the configured maximum.
 * @param level the risk level derived from the score.
 */
public record RiskAssessment(int score, RiskLevel level) {
}
