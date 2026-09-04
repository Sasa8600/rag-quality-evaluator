package com.rageval.repository;

import com.rageval.model.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {
    List<EvaluationRun> findAllByOrderByCreatedAtDesc();
}
