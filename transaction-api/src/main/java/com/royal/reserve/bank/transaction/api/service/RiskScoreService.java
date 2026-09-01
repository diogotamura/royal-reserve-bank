package com.royal.reserve.bank.transaction.api.service;

import com.royal.reserve.bank.transaction.api.dto.RiskAssessment;
import com.royal.reserve.bank.transaction.api.model.RiskLevel;
import com.royal.reserve.bank.transaction.api.model.TransactionItems;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class that calculates the risk score of a transaction.
 */
@Service
public class RiskScoreService {

    private final double valueWeight;
    private final double itemWeight;
    private final int maxScore;
    private final int mediumThreshold;
    private final int highThreshold;

    public RiskScoreService(@Value("${risk.score.value-weight:0.001}") double valueWeight,
                            @Value("${risk.score.item-weight:2}") double itemWeight,
                            @Value("${risk.score.max:100}") int maxScore,
                            @Value("${risk.score.medium-threshold:40}") int mediumThreshold,
                            @Value("${risk.score.high-threshold:70}") int highThreshold) {
        this.valueWeight = valueWeight;
        this.itemWeight = itemWeight;
        this.maxScore = maxScore;
        this.mediumThreshold = mediumThreshold;
        this.highThreshold = highThreshold;
    }

    /**
     * Calculates the risk assessment of the provided transaction items.
     *
     * @param transactionItems The transaction items to assess.
     * @return The risk assessment, holding the score and the corresponding risk level.
     */
    public RiskAssessment assess(List<TransactionItems> transactionItems) {
        long totalValue = transactionItems == null ? 0L : transactionItems.stream()
                .mapToLong(TransactionItems::getValue)
                .sum();
        int itemCount = transactionItems == null ? 0 : transactionItems.size();

        double rawScore = totalValue * valueWeight + itemCount * itemWeight;
        int score = (int) Math.min(maxScore, Math.max(0, Math.round(rawScore)));

        return new RiskAssessment(score, resolveLevel(score));
    }

    /**
     * Resolves the risk level for the provided score.
     *
     * @param score The risk score.
     * @return The corresponding risk level.
     */
    private RiskLevel resolveLevel(int score) {
        if (score >= highThreshold) {
            return RiskLevel.HIGH;
        }
        if (score >= mediumThreshold) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
