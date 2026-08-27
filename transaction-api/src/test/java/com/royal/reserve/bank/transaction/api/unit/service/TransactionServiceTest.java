package com.royal.reserve.bank.transaction.api.unit.service;

import com.royal.reserve.bank.transaction.api.client.AssetManagementClient;
import com.royal.reserve.bank.transaction.api.client.RiskScoreClient;
import com.royal.reserve.bank.transaction.api.dto.AssetManagementResponse;
import com.royal.reserve.bank.transaction.api.dto.RiskLevel;
import com.royal.reserve.bank.transaction.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.transaction.api.dto.RiskScoreResponse;
import com.royal.reserve.bank.transaction.api.dto.TransactionItemsDto;
import com.royal.reserve.bank.transaction.api.dto.TransactionRequest;
import com.royal.reserve.bank.transaction.api.event.TransactionEvent;
import com.royal.reserve.bank.transaction.api.model.Transaction;
import com.royal.reserve.bank.transaction.api.repository.TransactionRepository;
import com.royal.reserve.bank.transaction.api.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link TransactionService} class.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private TransactionService transactionService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AssetManagementClient assetManagementClient;

    @Mock
    private RiskScoreClient riskScoreClient;

    @Mock
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithAvailableAssets_shouldSaveTransactionAndSendNotification() {
        // Given
        transactionService = new TransactionService(transactionRepository,assetManagementClient,
                riskScoreClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setAccountId("ACC-123");
        TransactionItemsDto transactionItemsDto = new TransactionItemsDto();
        transactionItemsDto.setAssetCode("DVN");
        transactionItemsDto.setAssetName("Devon Energy Corporation");
        transactionItemsDto.setValue(100);
        transactionRequest.setTransactionItemsDtoList(Collections.singletonList(transactionItemsDto));

        Transaction transaction = new Transaction();
        transaction.setTransactionId("transactionId");

        AssetManagementResponse assetManagementResponse = Mockito.mock(AssetManagementResponse.class);
        Mockito.when(assetManagementResponse.isAssetAvailable()).thenReturn(true);

        Mockito.when(assetManagementClient.checkAssetAvailability(ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(assetManagementResponse));
        Mockito.when(riskScoreClient.evaluateRisk(ArgumentMatchers.any(RiskScoreRequest.class)))
                .thenReturn(new RiskScoreResponse(2, RiskLevel.LOW, true));
        Mockito.when(transactionRepository.save(ArgumentMatchers.any(Transaction.class))).thenReturn(transaction);

        // When
        String result = transactionService.processTransaction(transactionRequest);

        // Then
        assertEquals("Transaction completed successfully!", result);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(kafkaTemplate, times(1)).send(eq("notificationTopic"),
                any(TransactionEvent.class));
    }

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithUnavailableAssets_shouldThrowIllegalArgumentException() {
        // Given
        transactionService = new TransactionService(transactionRepository,assetManagementClient,
                riskScoreClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
        TransactionItemsDto transactionItemsDto = new TransactionItemsDto();
        transactionItemsDto.setAssetCode("DM");
        transactionItemsDto.setAssetName("Desktop Metal, Inc.");
        transactionItemsDto.setValue(200);
        transactionRequest.setTransactionItemsDtoList(Collections.singletonList(transactionItemsDto));

        AssetManagementResponse assetManagementResponse = Mockito.mock(AssetManagementResponse.class);
        Mockito.when(assetManagementResponse.isAssetAvailable()).thenReturn(false);

        Mockito.when(assetManagementClient.checkAssetAvailability(ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(assetManagementResponse));

        // When and Then
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.processTransaction(transactionRequest));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaTemplate, never()).send(anyString(), any(TransactionEvent.class));
    }

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithHighRisk_shouldThrowIllegalArgumentException() {
        // Given
        transactionService = new TransactionService(transactionRepository,assetManagementClient,
                riskScoreClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
        TransactionItemsDto transactionItemsDto = new TransactionItemsDto();
        transactionItemsDto.setAssetCode("COIN");
        transactionItemsDto.setAssetName("Coinbase Inc.");
        transactionItemsDto.setValue(150_000);
        transactionRequest.setTransactionItemsDtoList(Collections.singletonList(transactionItemsDto));

        AssetManagementResponse assetManagementResponse = Mockito.mock(AssetManagementResponse.class);
        Mockito.when(assetManagementResponse.isAssetAvailable()).thenReturn(true);

        Mockito.when(assetManagementClient.checkAssetAvailability(ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(assetManagementResponse));
        Mockito.when(riskScoreClient.evaluateRisk(ArgumentMatchers.any(RiskScoreRequest.class)))
                .thenReturn(new RiskScoreResponse(84, RiskLevel.HIGH, false));

        // When and Then
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.processTransaction(transactionRequest));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaTemplate, never()).send(anyString(), any(TransactionEvent.class));
    }

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithRiskServiceUnavailable_shouldFailClosed() {
        // Given
        transactionService = new TransactionService(transactionRepository,assetManagementClient,
                riskScoreClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setAccountId("ACC-123");
        TransactionItemsDto transactionItemsDto = new TransactionItemsDto();
        transactionItemsDto.setAssetCode("DVN");
        transactionItemsDto.setAssetName("Devon Energy Corporation");
        transactionItemsDto.setValue(100);
        transactionRequest.setTransactionItemsDtoList(Collections.singletonList(transactionItemsDto));

        AssetManagementResponse assetManagementResponse = Mockito.mock(AssetManagementResponse.class);
        Mockito.when(assetManagementResponse.isAssetAvailable()).thenReturn(true);

        Mockito.when(assetManagementClient.checkAssetAvailability(ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(assetManagementResponse));
        Mockito.when(riskScoreClient.evaluateRisk(ArgumentMatchers.any(RiskScoreRequest.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // When and Then
        assertThrows(IllegalStateException.class,
                () -> transactionService.processTransaction(transactionRequest));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaTemplate, never()).send(anyString(), any(TransactionEvent.class));
    }

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithApprovedRisk_shouldPersistRiskScoreAndLevel() {
        // Given
        transactionService = new TransactionService(transactionRepository,assetManagementClient,
                riskScoreClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setAccountId("ACC-123");
        TransactionItemsDto transactionItemsDto = new TransactionItemsDto();
        transactionItemsDto.setAssetCode("MSFT");
        transactionItemsDto.setAssetName("Microsoft Corporation");
        transactionItemsDto.setValue(11_300);
        transactionRequest.setTransactionItemsDtoList(Collections.singletonList(transactionItemsDto));

        AssetManagementResponse assetManagementResponse = Mockito.mock(AssetManagementResponse.class);
        Mockito.when(assetManagementResponse.isAssetAvailable()).thenReturn(true);

        Mockito.when(assetManagementClient.checkAssetAvailability(ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(assetManagementResponse));
        Mockito.when(riskScoreClient.evaluateRisk(ArgumentMatchers.any(RiskScoreRequest.class)))
                .thenReturn(new RiskScoreResponse(27, RiskLevel.LOW, true));

        // When
        transactionService.processTransaction(transactionRequest);

        // Then
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).save(transactionCaptor.capture());
        assertEquals(27, transactionCaptor.getValue().getRiskScore());
        assertEquals(RiskLevel.LOW, transactionCaptor.getValue().getRiskLevel());
    }
}
