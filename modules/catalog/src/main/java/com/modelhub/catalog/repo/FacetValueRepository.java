package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.FacetValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FacetValueRepository extends JpaRepository<FacetValueEntity, Long> {

    List<FacetValueEntity> findByRepositoryId(Long repositoryId);

    @Modifying
    @Query("delete from FacetValueEntity f where f.repositoryId = :repositoryId")
    void deleteByRepositoryId(@Param("repositoryId") Long repositoryId);
}
