package com.royal.reserve.bank.risk.assessment.api.controller;

import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.service.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller class that handles HTTP requests related to risk assessment.
 */
@RestController
@RequestMapping("/api/risk-assessment")
@RequiredArgsConstructor
@Slf4j
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    /**
     *
     *Assesses the risk of a transaction.
     *@param request The transaction data to assess.
     *@return The risk assessment result.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public RiskAssessmentResponse assess(@RequestBody RiskAssessmentRequest request) {
        log.info("Received risk assessment request for transaction: {}", request.getTransactionId());
        return riskAssessmentService.assess(request);
    }
}
