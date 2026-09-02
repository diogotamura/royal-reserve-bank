package com.royal.reserve.bank.transaction.api.client;

import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.transaction.api.dto.RiskAssessmentResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * A Feign client interface for interacting with the Risk Assessment API.
 */
@FeignClient(name = "risk-assessment-api")
@Retry(name = "risk-assessment")
public interface RiskAssessmentClient {

    /**
     *
     *Requests a synchronous risk assessment of a transaction from the Risk Assessment API.
     *@param request The transaction data to assess.
     *@return A RiskAssessmentResponse containing the risk score and the approval decision.
     */
    @PostMapping("/api/risk-assessment")
    RiskAssessmentResponse assessRisk(@RequestBody RiskAssessmentRequest request);
}
