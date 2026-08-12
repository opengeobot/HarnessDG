package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.RepoStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepoStatsRepository extends JpaRepository<RepoStatsEntity, Long> {
}
