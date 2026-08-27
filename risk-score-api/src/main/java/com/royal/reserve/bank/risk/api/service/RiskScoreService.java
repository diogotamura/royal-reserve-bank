package com.royal.reserve.bank.risk.api.service;

import com.royal.reserve.bank.risk.api.dto.RiskLevel;
import com.royal.reserve.bank.risk.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.risk.api.dto.RiskScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class that calculates the risk score of a transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoreService {

    static final int HIGH_RISK_THRESHOLD = 60;
    static final int MEDIUM_RISK_THRESHOLD = 30;

    private static final int UNKNOWN_ACCOUNT_POINTS = 30;
    private static final int POINTS_PER_ITEM = 2;
    private static final int MAX_ITEM_POINTS = 20;

    /**
     *
     *Evaluates the risk of a transaction based on deterministic rules:
     *the higher the total value, the number of items and the lack of an account identifier,
     *the higher the score.
     *@param riskScoreRequest The request containing the data used to calculate the risk.
     *@return A RiskScoreResponse with the calculated score, the risk level and the approval flag.
     */
    public RiskScoreResponse evaluateRisk(RiskScoreRequest riskScoreRequest) {
        int score = valuePoints(riskScoreRequest.getTotalValue())
                + itemPoints(riskScoreRequest.getAssetCodes())
                + accountPoints(riskScoreRequest.getAccountId());

        RiskLevel riskLevel = toRiskLevel(score);
        log.info("Risk evaluated with score {} and level {}", score, riskLevel);

        return RiskScoreResponse.builder()
                .score(score)
                .riskLevel(riskLevel)
                .approved(riskLevel != RiskLevel.HIGH)
                .build();
    }

    private int valuePoints(int totalValue) {
        if (totalValue >= 100_000) {
            return 50;
        } else if (totalValue >= 50_000) {
            return 40;
        } else if (totalValue >= 10_000) {
            return 25;
        } else if (totalValue >= 1_000) {
            return 10;
        }
        return 0;
    }

    private int itemPoints(List<String> assetCodes) {
        int itemCount = assetCodes == null ? 0 : assetCodes.size();
        return Math.min(itemCount * POINTS_PER_ITEM, MAX_ITEM_POINTS);
    }

    private int accountPoints(String accountId) {
        return accountId == null || accountId.isBlank() ? UNKNOWN_ACCOUNT_POINTS : 0;
    }

    private RiskLevel toRiskLevel(int score) {
        if (score >= HIGH_RISK_THRESHOLD) {
            return RiskLevel.HIGH;
        } else if (score >= MEDIUM_RISK_THRESHOLD) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
