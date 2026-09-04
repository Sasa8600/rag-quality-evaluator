package com.rageval.repository;

import com.rageval.model.EvaluationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface EvaluationResultRepository extends JpaRepository<EvaluationResult, Long> {
    List<EvaluationResult> findByTestQueryId(Long testQueryId);

    @Query("SELECT AVG(e.ragScore) FROM EvaluationResult e")
    BigDecimal findAverageRagScore();

    @Query("SELECT AVG(e.precisionAtK) FROM EvaluationResult e")
    BigDecimal findAveragePrecision();

    @Query("SELECT AVG(e.recall) FROM EvaluationResult e")
    BigDecimal findAverageRecall();

    @Query("SELECT AVG(e.answerRelevance) FROM EvaluationResult e")
    BigDecimal findAverageAnswerRelevance();

    @Query("SELECT AVG(e.faithfulness) FROM EvaluationResult e")
    BigDecimal findAverageFaithfulness();

    List<EvaluationResult> findAllByOrderByCreatedAtDesc();
}
