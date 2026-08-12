package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.GatedPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatedPolicyRepository extends JpaRepository<GatedPolicyEntity, Long> {
}
