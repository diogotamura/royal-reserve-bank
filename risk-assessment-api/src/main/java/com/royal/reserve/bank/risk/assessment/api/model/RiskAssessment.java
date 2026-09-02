package com.royal.reserve.bank.risk.assessment.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents the result of a risk assessment performed on a transaction.
 */
@Entity
@Table(name = "t_risk_assessment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String transactionId;
    private int riskScore;
    private boolean approved;
    private LocalDateTime evaluatedAt;
}
