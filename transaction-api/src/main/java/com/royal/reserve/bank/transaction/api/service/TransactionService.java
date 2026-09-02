package com.royal.reserve.bank.transaction.api.service;

import com.royal.reserve.bank.transaction.api.client.AssetManagementClient;
import com.royal.reserve.bank.transaction.api.client.RiskAssessmentClient;
import com.royal.reserve.bank.transaction.api.dto.AssetManagementResponse;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentItem;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.transaction.api.dto.TransactionItemsDto;
import com.royal.reserve.bank.transaction.api.dto.TransactionRequest;
import com.royal.reserve.bank.transaction.api.event.TransactionEvent;
import com.royal.reserve.bank.transaction.api.model.Transaction;
import com.royal.reserve.bank.transaction.api.model.TransactionItems;
import com.royal.reserve.bank.transaction.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service class that provides operations for managing transactions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AssetManagementClient assetManagementClient;
    private final RiskAssessmentClient riskAssessmentClient;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    /**
     *
     *Process a transaction based on the provided transaction request.
     *@param transactionRequest The transaction request containing the necessary information.
     *@return A string message indicating the result of the transaction processing.
     *@throws IllegalArgumentException If any of the requested assets are not available or the transaction
     *is assessed as high risk.
     */
    @CacheEvict(value = "assetAvailability", allEntries = true)
    public String processTransaction(TransactionRequest transactionRequest) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());

        List<TransactionItems> transactionItems = transactionRequest.getTransactionItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        transaction.setTransactionItemsList(transactionItems);

        List<String> assetCodes = transaction.getTransactionItemsList().stream()
                .map(TransactionItems::getAssetCode)
                .toList();

        boolean assetIsAvailable = checkAssetAvailability(assetCodes);

        if (!assetIsAvailable) {
            throw new IllegalArgumentException("Asset is not available, please try again later");
        }

        RiskAssessmentResponse riskAssessment = assessRisk(transaction);

        if (!riskAssessment.isApproved()) {
            throw new IllegalArgumentException("Transaction rejected by risk assessment: "
                    + riskAssessment.getReason());
        }

        transactionRepository.save(transaction);
        kafkaTemplate.send("notificationTopic", new TransactionEvent(transaction.getTransactionId()));
        return "Transaction completed successfully!";
    }

    /**
     * Requests a risk assessment for the transaction.
     *
     * @param transaction The transaction to assess.
     * @return The risk assessment result.
     */
    public RiskAssessmentResponse assessRisk(Transaction transaction) {
        List<RiskAssessmentItem> items = transaction.getTransactionItemsList().stream()
                .map(item -> new RiskAssessmentItem(item.getAssetCode(), item.getAssetName(), item.getValue()))
                .toList();
        return riskAssessmentClient.assessRisk(new RiskAssessmentRequest(transaction.getTransactionId(), items));
    }

    /**
     * Checks the availability of assets.
     *
     * @param assetCodes The list of asset codes to check.
     * @return true if all assets are available, false otherwise.
     */
    @Cacheable("assetAvailability")
    public boolean checkAssetAvailability(List<String> assetCodes) {
        return assetManagementClient.checkAssetAvailability(assetCodes)
                .stream()
                .allMatch(AssetManagementResponse::isAssetAvailable);
    }

    /**
     *
     *Maps the provided transaction items DTO to a TransactionItems object.
     *@param transactionItemsDto The transaction items DTO to be mapped.
     *@return The corresponding TransactionItems object.
     */
    private TransactionItems mapToDto(TransactionItemsDto transactionItemsDto) {
        TransactionItems transactionItems = new TransactionItems();
        transactionItems.setAssetCode(transactionItemsDto.getAssetCode());
        transactionItems.setAssetName(transactionItemsDto.getAssetName());
        transactionItems.setValue(transactionItemsDto.getValue());
        return transactionItems;
    }
}
