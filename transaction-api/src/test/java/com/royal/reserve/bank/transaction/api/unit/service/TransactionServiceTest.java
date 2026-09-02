package com.royal.reserve.bank.transaction.api.unit.service;

import com.royal.reserve.bank.transaction.api.client.AssetManagementClient;
import com.royal.reserve.bank.transaction.api.client.RiskAssessmentClient;
import com.royal.reserve.bank.transaction.api.dto.AssetManagementResponse;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.transaction.api.dto.TransactionItemsDto;
import com.royal.reserve.bank.transaction.api.dto.TransactionRequest;
import com.royal.reserve.bank.transaction.api.event.TransactionEvent;
import com.royal.reserve.bank.transaction.api.model.Transaction;
import com.royal.reserve.bank.transaction.api.model.TransactionItems;
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
    private RiskAssessmentClient riskAssessmentClient;

    @Mock
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithAvailableAssets_shouldSaveTransactionAndSendNotification() {
        // Given
        transactionService = new TransactionService(transactionRepository, assetManagementClient,
                riskAssessmentClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
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
        Mockito.when(riskAssessmentClient.assessRisk(ArgumentMatchers.any(RiskAssessmentRequest.class)))
                .thenReturn(RiskAssessmentResponse.builder().riskLevel("LOW").approved(true).build());
        Mockito.when(transactionRepository.save(ArgumentMatchers.any(Transaction.class))).thenReturn(transaction);

        // When
        String result = transactionService.processTransaction(transactionRequest);

        // Then
        assertEquals("Transaction completed successfully!", result);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(kafkaTemplate, times(1)).send(eq("notificationTopic"),
                any(TransactionEvent.class));
        verify(riskAssessmentClient, times(1)).assessRisk(any(RiskAssessmentRequest.class));
    }

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithUnavailableAssets_shouldThrowIllegalArgumentException() {
        // Given
        transactionService = new TransactionService(transactionRepository, assetManagementClient,
                riskAssessmentClient, kafkaTemplate);
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
        verify(riskAssessmentClient, never()).assessRisk(any(RiskAssessmentRequest.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaTemplate, never()).send(anyString(), any(TransactionEvent.class));
    }

    /**
     * Test method for {@link TransactionService#processTransaction(TransactionRequest)}.
     */
    @Test
    void processTransactionWithHighRisk_shouldThrowIllegalArgumentExceptionBeforePersisting() {
        // Given
        transactionService = new TransactionService(transactionRepository, assetManagementClient,
                riskAssessmentClient, kafkaTemplate);
        TransactionRequest transactionRequest = new TransactionRequest();
        TransactionItemsDto transactionItemsDto = new TransactionItemsDto();
        transactionItemsDto.setAssetCode("NVDA");
        transactionItemsDto.setAssetName("NVIDIA Corporation");
        transactionItemsDto.setValue(50000);
        transactionRequest.setTransactionItemsDtoList(Collections.singletonList(transactionItemsDto));

        AssetManagementResponse assetManagementResponse = Mockito.mock(AssetManagementResponse.class);
        Mockito.when(assetManagementResponse.isAssetAvailable()).thenReturn(true);
        Mockito.when(assetManagementClient.checkAssetAvailability(ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(assetManagementResponse));
        Mockito.when(riskAssessmentClient.assessRisk(ArgumentMatchers.any(RiskAssessmentRequest.class)))
                .thenReturn(RiskAssessmentResponse.builder().riskLevel("HIGH").approved(false)
                        .reason("Total value 50000 exceeds high risk threshold 10000").build());

        // When and Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> transactionService.processTransaction(transactionRequest));
        assertTrue(exception.getMessage().contains("risk assessment"));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaTemplate, never()).send(anyString(), any(TransactionEvent.class));
    }

    /**
     * Test method for {@link TransactionService#assessRisk(Transaction)}.
     */
    @Test
    void assessRisk_shouldSendTransactionIdAndItemsToRiskAssessmentClient() {
        // Given
        transactionService = new TransactionService(transactionRepository, assetManagementClient,
                riskAssessmentClient, kafkaTemplate);
        Transaction transaction = new Transaction();
        transaction.setTransactionId("transactionId");
        TransactionItems item = new TransactionItems();
        item.setAssetCode("DVN");
        item.setAssetName("Devon Energy Corporation");
        item.setValue(100);
        transaction.setTransactionItemsList(Collections.singletonList(item));

        RiskAssessmentResponse expected = RiskAssessmentResponse.builder().approved(true).build();
        Mockito.when(riskAssessmentClient.assessRisk(ArgumentMatchers.any(RiskAssessmentRequest.class)))
                .thenReturn(expected);

        // When
        RiskAssessmentResponse response = transactionService.assessRisk(transaction);

        // Then
        assertEquals(expected, response);
        ArgumentCaptor<RiskAssessmentRequest> captor = ArgumentCaptor.forClass(RiskAssessmentRequest.class);
        verify(riskAssessmentClient).assessRisk(captor.capture());
        assertEquals("transactionId", captor.getValue().getTransactionId());
        assertEquals(1, captor.getValue().getItems().size());
        assertEquals("DVN", captor.getValue().getItems().get(0).getAssetCode());
        assertEquals(100, captor.getValue().getItems().get(0).getValue());
    }
}
