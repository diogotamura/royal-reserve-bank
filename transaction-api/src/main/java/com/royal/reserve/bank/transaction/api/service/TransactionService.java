package com.royal.reserve.bank.transaction.api.service;

import com.royal.reserve.bank.transaction.api.client.AssetManagementClient;
import com.royal.reserve.bank.transaction.api.dto.AssetManagementResponse;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessment;
import com.royal.reserve.bank.transaction.api.dto.TransactionItemsDto;
import com.royal.reserve.bank.transaction.api.dto.TransactionRequest;
import com.royal.reserve.bank.transaction.api.event.TransactionEvent;
import com.royal.reserve.bank.transaction.api.model.Transaction;
import com.royal.reserve.bank.transaction.api.model.TransactionItems;
import com.royal.reserve.bank.transaction.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final RiskScoreService riskScoreService;

    @Value("${risk.score.block-threshold:90}")
    private int blockThreshold = 90;

    /**
     *
     *Process a transaction based on the provided transaction request.
     *@param transactionRequest The transaction request containing the necessary information.
     *@return A string message indicating the result of the transaction processing.
     *@throws IllegalArgumentException If any of the requested assets are not available or the risk score is too high.
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

        RiskAssessment riskAssessment = riskScoreService.assess(transactionItems);
        transaction.setRiskScore(riskAssessment.score());
        transaction.setRiskLevel(riskAssessment.level());

        if (riskAssessment.score() >= blockThreshold) {
            throw new IllegalArgumentException("Transaction blocked: risk score " + riskAssessment.score()
                    + " exceeds the allowed threshold of " + blockThreshold);
        }

        transactionRepository.save(transaction);
        kafkaTemplate.send("notificationTopic", new TransactionEvent(transaction.getTransactionId(),
                riskAssessment.score(), riskAssessment.level().name()));
        return "Transaction completed successfully!";
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
