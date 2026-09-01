package com.royal.reserve.bank.transaction.api.unit.service;

import com.royal.reserve.bank.transaction.api.dto.RiskAssessment;
import com.royal.reserve.bank.transaction.api.model.RiskLevel;
import com.royal.reserve.bank.transaction.api.model.TransactionItems;
import com.royal.reserve.bank.transaction.api.service.RiskScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the {@link RiskScoreService} class.
 */
class RiskScoreServiceTest {

    private RiskScoreService riskScoreService;

    @BeforeEach
    void setUp() {
        riskScoreService = new RiskScoreService(0.001, 2, 100, 40, 70);
    }

    @Test
    void assessSmallTransaction_shouldReturnLowRisk() {
        // Given
        List<TransactionItems> transactionItems = Collections.singletonList(transactionItem("DVN", 1000));

        // When
        RiskAssessment riskAssessment = riskScoreService.assess(transactionItems);

        // Then
        assertEquals(3, riskAssessment.score());
        assertEquals(RiskLevel.LOW, riskAssessment.level());
    }

    @Test
    void assessMediumTransaction_shouldReturnMediumRisk() {
        // Given
        List<TransactionItems> transactionItems = List.of(transactionItem("MSFT", 30000),
                transactionItem("COIN", 15000));

        // When
        RiskAssessment riskAssessment = riskScoreService.assess(transactionItems);

        // Then
        assertEquals(49, riskAssessment.score());
        assertEquals(RiskLevel.MEDIUM, riskAssessment.level());
    }

    @Test
    void assessLargeTransaction_shouldCapScoreAndReturnHighRisk() {
        // Given
        List<TransactionItems> transactionItems = Collections.singletonList(transactionItem("NVDA", 500000));

        // When
        RiskAssessment riskAssessment = riskScoreService.assess(transactionItems);

        // Then
        assertEquals(100, riskAssessment.score());
        assertEquals(RiskLevel.HIGH, riskAssessment.level());
    }

    @Test
    void assessEmptyTransaction_shouldReturnZeroScore() {
        // When
        RiskAssessment riskAssessment = riskScoreService.assess(Collections.emptyList());

        // Then
        assertEquals(0, riskAssessment.score());
        assertEquals(RiskLevel.LOW, riskAssessment.level());
    }

    @Test
    void assessTransactionWithNegativeValues_shouldIgnoreThemInsteadOfOffsettingTheScore() {
        // Given
        List<TransactionItems> transactionItems = List.of(transactionItem("NVDA", 500000),
                transactionItem("MSFT", -499000));

        // When
        RiskAssessment riskAssessment = riskScoreService.assess(transactionItems);

        // Then
        assertEquals(100, riskAssessment.score());
        assertEquals(RiskLevel.HIGH, riskAssessment.level());
    }

    private TransactionItems transactionItem(String assetCode, int value) {
        TransactionItems transactionItems = new TransactionItems();
        transactionItems.setAssetCode(assetCode);
        transactionItems.setValue(value);
        return transactionItems;
    }
}
