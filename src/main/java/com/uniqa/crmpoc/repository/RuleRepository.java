package com.uniqa.crmpoc.repository;

import com.uniqa.crmpoc.domain.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, Long> {
    /**
     * Not ordered by stage here: Rule.stage is @Enumerated(STRING), so a DB-level
     * ORDER BY would sort alphabetically rather than by RuleStage's declared
     * hierarchy. RuleEngineService sorts the result in Java instead.
     */
    List<Rule> findByActiveTrue();
}
