package com.rageval.repository;

import com.rageval.model.TestQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestQueryRepository extends JpaRepository<TestQuery, Long> {
    List<TestQuery> findAll();
}
