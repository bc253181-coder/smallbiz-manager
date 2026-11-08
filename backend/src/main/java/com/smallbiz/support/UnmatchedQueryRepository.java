package com.smallbiz.support;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnmatchedQueryRepository extends JpaRepository<UnmatchedQuery, Long> {

    Optional<UnmatchedQuery> findByNormalizedText(String normalizedText);

    List<UnmatchedQuery> findTop100ByResolvedFalseOrderByOccurrencesDescLastSeenAtDesc();
}
