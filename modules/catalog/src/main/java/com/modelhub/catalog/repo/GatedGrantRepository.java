package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.GatedGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GatedGrantRepository extends JpaRepository<GatedGrantEntity, Long> {

    /** 有效 grant：未吊销、未过期且 generation 与当前策略一致（02 §4：旧 generation 永不复活）。 */
    @Query("select g from GatedGrantEntity g where g.repositoryId = :repositoryId "
            + "and g.userId = :userId and g.revokedAt is null and g.expiresAt > :now "
            + "and g.policyGeneration = :policyGeneration")
    List<GatedGrantEntity> findActive(@Param("repositoryId") Long repositoryId,
                                      @Param("userId") Long userId,
                                      @Param("now") OffsetDateTime now,
                                      @Param("policyGeneration") long policyGeneration);

    Optional<GatedGrantEntity> findByRequestId(Long requestId);

    List<GatedGrantEntity> findByRepositoryId(Long repositoryId);
}
