package com.royal.reserve.bank.risk.api.controller;

import com.royal.reserve.bank.risk.api.dto.RiskScoreRequest;
import com.royal.reserve.bank.risk.api.dto.RiskScoreResponse;
import com.royal.reserve.bank.risk.api.service.RiskScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Controller class that handles HTTP requests related to risk assessment.
 */
@RestController
@RequestMapping("/api/risk-score")
@RequiredArgsConstructor
@Slf4j
public class RiskScoreController {

    private final RiskScoreService riskScoreService;

    /**
     *
     *Evaluates the risk of a transaction.
     *@param riskScoreRequest The request containing the data used to calculate the risk.
     *@return The risk score response containing the score, the risk level and the approval flag.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public RiskScoreResponse evaluateRisk(@RequestBody RiskScoreRequest riskScoreRequest) {
        log.info("Received risk evaluation request for account: {}", riskScoreRequest.getAccountId());
        return riskScoreService.evaluateRisk(riskScoreRequest);
    }
}
