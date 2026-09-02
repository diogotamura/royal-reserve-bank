package com.royal.reserve.bank.risk.assessment.api.service;

import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentItem;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class that scores the risk of a transaction.
 * <p>
 * Current rules are value based: the sum of the item values is compared against configurable thresholds.
 * Transactions with a total at or above the high risk threshold are not approved.
 */
@Service
@Slf4j
public class RiskAssessmentService {

    private final int mediumRiskThreshold;
    private final int highRiskThreshold;

    public RiskAssessmentService(@Value("${risk.assessment.medium-risk-threshold:5000}") int mediumRiskThreshold,
                                 @Value("${risk.assessment.high-risk-threshold:10000}") int highRiskThreshold) {
        this.mediumRiskThreshold = mediumRiskThreshold;
        this.highRiskThreshold = highRiskThreshold;
    }

    /**
     *
     *Assesses the risk of the given transaction.
     *@param request The transaction data to assess.
     *@return The risk level and whether the transaction is approved.
     */
    public RiskAssessmentResponse assess(RiskAssessmentRequest request) {
        List<RiskAssessmentItem> items = request.getItems() == null ? List.of() : request.getItems();
        int totalValue = items.stream().mapToInt(RiskAssessmentItem::getValue).sum();
        RiskLevel riskLevel = scoreByTotalValue(totalValue);
        boolean approved = riskLevel != RiskLevel.HIGH;

        log.info("Transaction {} assessed with total value {} as {} risk (approved={})",
                request.getTransactionId(), totalValue, riskLevel, approved);

        return RiskAssessmentResponse.builder()
                .transactionId(request.getTransactionId())
                .riskLevel(riskLevel)
                .totalValue(totalValue)
                .approved(approved)
                .reason(approved ? "Transaction within acceptable risk limits"
                        : "Total value " + totalValue + " exceeds high risk threshold " + highRiskThreshold)
                .build();
    }

    private RiskLevel scoreByTotalValue(int totalValue) {
        if (totalValue >= highRiskThreshold) {
            return RiskLevel.HIGH;
        }
        if (totalValue >= mediumRiskThreshold) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
