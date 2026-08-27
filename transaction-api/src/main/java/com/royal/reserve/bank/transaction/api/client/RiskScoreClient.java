package com.royal.reserve.bank.transaction.api.client;

import com.royal.reserve.bank.transaction.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.transaction.api.dto.RiskScoreResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * A Feign client interface for interacting with the Risk Score API.
 */
@FeignClient(name = "risk-score-api")
@Retry(name = "risk-score")
public interface RiskScoreClient {

    /**
     *
     *Evaluates the risk of a transaction through the Risk Score API.
     *@param riskScoreRequest The request containing the data used to calculate the risk.
     *@return A RiskScoreResponse object containing the score, the risk level and the approval flag.
     */
    @PostMapping("/api/risk-score")
    RiskScoreResponse evaluateRisk(@RequestBody RiskScoreRequest riskScoreRequest);
}
