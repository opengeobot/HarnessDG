package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.TaxonomyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxonomyRepository extends JpaRepository<TaxonomyEntity, Long> {

    Optional<TaxonomyEntity> findByTaxonomyKey(String taxonomyKey);

    List<TaxonomyEntity> findAllByOrderById();
}
