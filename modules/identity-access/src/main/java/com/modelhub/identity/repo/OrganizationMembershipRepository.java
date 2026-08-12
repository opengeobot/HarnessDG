package com.modelhub.identity.repo;

import com.modelhub.identity.domain.OrganizationMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembershipEntity, Long> {

    Optional<OrganizationMembershipEntity> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    List<OrganizationMembershipEntity> findByOrganizationIdAndStatusOrderByIdAsc(Long organizationId, String status);

    /** cursor 分页：按 id 升序取 id > lastId 的 active 成员。 */
    @Query("""
            select m from OrganizationMembershipEntity m
            where m.organizationId = :orgId and m.status = 'active' and m.id > :lastId
            order by m.id asc
            limit :limit
            """)
    List<OrganizationMembershipEntity> findActiveAfter(@Param("orgId") Long organizationId,
                                                       @Param("lastId") long lastId,
                                                       @Param("limit") int limit);

    List<OrganizationMembershipEntity> findByUserIdAndStatus(Long userId, String status);

    @Query("select count(m) from OrganizationMembershipEntity m "
            + "where m.organizationId = :orgId and m.role = 'owner' and m.status = 'active'")
    long countActiveOwners(@Param("orgId") Long organizationId);
}
