package com.modelhub.identity.repo;

import com.modelhub.identity.domain.PlatformRoleAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlatformRoleAssignmentRepository extends JpaRepository<PlatformRoleAssignmentEntity, Long> {

    @Query("select p.role from PlatformRoleAssignmentEntity p where p.userId = :userId and p.revokedAt is null")
    List<String> findActiveRoles(@Param("userId") Long userId);

    long countByRevokedAtIsNull();
}
