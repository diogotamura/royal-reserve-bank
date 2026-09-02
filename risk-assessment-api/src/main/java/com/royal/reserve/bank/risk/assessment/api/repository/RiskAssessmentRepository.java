package com.royal.reserve.bank.risk.assessment.api.repository;

import com.royal.reserve.bank.risk.assessment.api.model.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for storing risk assessments in the database.
 * It provides CRUD operations and other database-related functionality.
 */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
}
