package com.royal.reserve.bank.risk.assessment.api.service;

import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentItem;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.model.RiskAssessment;
import com.royal.reserve.bank.risk.assessment.api.repository.RiskAssessmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service class that evaluates the risk of a transaction based on configurable rules.
 */
@Service
@Slf4j
public class RiskAssessmentService {

    private static final int MAX_SCORE = 100;

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final long maxTransactionValue;
    private final int maxItems;

    public RiskAssessmentService(RiskAssessmentRepository riskAssessmentRepository,
                                 @Value("${risk.max-transaction-value}") long maxTransactionValue,
                                 @Value("${risk.max-items}") int maxItems) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.maxTransactionValue = maxTransactionValue;
        this.maxItems = maxItems;
    }

    /**
     *
     *Assesses the risk of a transaction, persists the result and returns it.
     *The transaction is rejected when its total value exceeds {@code risk.max-transaction-value}
     *or when the number of items exceeds {@code risk.max-items}.
     *@param request The transaction data to assess.
     *@return A RiskAssessmentResponse containing the risk score and the approval decision.
     */
    @Transactional
    public RiskAssessmentResponse assessRisk(RiskAssessmentRequest request) {
        log.info("Assessing risk for transaction {}", request.getTransactionId());
        List<RiskAssessmentItem> items = request.getItems() == null ? List.of() : request.getItems();

        long totalValue = items.stream().mapToLong(RiskAssessmentItem::getValue).sum();
        int itemCount = items.size();

        int valueScore = maxTransactionValue <= 0 ? MAX_SCORE
                : (int) Math.min(MAX_SCORE, totalValue * MAX_SCORE / maxTransactionValue);
        int itemScore = maxItems <= 0 ? MAX_SCORE
                : (int) Math.min(MAX_SCORE, (long) itemCount * MAX_SCORE / maxItems);
        int riskScore = Math.max(valueScore, itemScore);
        boolean approved = totalValue <= maxTransactionValue && itemCount <= maxItems;

        RiskAssessment riskAssessment = new RiskAssessment();
        riskAssessment.setTransactionId(request.getTransactionId());
        riskAssessment.setRiskScore(riskScore);
        riskAssessment.setApproved(approved);
        riskAssessment.setEvaluatedAt(LocalDateTime.now());
        riskAssessmentRepository.save(riskAssessment);

        log.info("Transaction {} assessed with score {} (approved={})",
                request.getTransactionId(), riskScore, approved);
        return RiskAssessmentResponse.builder()
                .riskScore(riskScore)
                .approved(approved)
                .build();
    }
}
