package com.royal.reserve.bank.risk.assessment.api.controller;

import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentRequest;
import com.royal.reserve.bank.risk.assessment.api.dto.RiskAssessmentResponse;
import com.royal.reserve.bank.risk.assessment.api.service.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
     *@return The risk assessment response containing the risk score and approval decision.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public RiskAssessmentResponse assessRisk(@RequestBody RiskAssessmentRequest request) {
        log.info("Received risk assessment request for transaction: {}", request.getTransactionId());
        return riskAssessmentService.assessRisk(request);
    }
}
