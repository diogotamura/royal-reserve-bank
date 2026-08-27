package com.royal.reserve.bank.transaction.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.royal.reserve.bank.transaction.api.dto.RiskLevel;

import jakarta.persistence.*;
import java.util.List;

/**
 * Represents a transaction.
 */
@Entity
@Table(name = "t_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String transactionId;
    private Integer riskScore;
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    @OneToMany(cascade = CascadeType.ALL)
    private List<TransactionItems> transactionItemsList;
}
