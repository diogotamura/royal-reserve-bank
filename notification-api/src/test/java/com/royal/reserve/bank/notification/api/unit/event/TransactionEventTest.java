package com.royal.reserve.bank.notification.api.unit.event;


import com.royal.reserve.bank.notification.api.event.TransactionEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransactionEvent} class.
 */
class TransactionEventTest {

    /**
     * Test for the {@link TransactionEvent#TransactionEvent(String)} constructor.
     */
    @Test
    void testTransactionEvent() {
        // Given
        String expectedTransactionId = "13645940";

        // When
        TransactionEvent event = new TransactionEvent(expectedTransactionId);

        // Then
        Assertions.assertEquals(expectedTransactionId, event.getTransactionId());
    }

    /**
     * Test for the {@link TransactionEvent#TransactionEvent(String, int, String)} constructor.
     */
    @Test
    void testTransactionEventWithRiskScore() {
        // Given
        String expectedTransactionId = "13645941";

        // When
        TransactionEvent event = new TransactionEvent(expectedTransactionId, 85, "HIGH");

        // Then
        Assertions.assertEquals(expectedTransactionId, event.getTransactionId());
        Assertions.assertEquals(85, event.getRiskScore());
        Assertions.assertEquals("HIGH", event.getRiskLevel());
    }
}

