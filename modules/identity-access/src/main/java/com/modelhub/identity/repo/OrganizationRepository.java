package com.modelhub.identity.repo;

import com.modelhub.identity.domain.OrganizationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, Long> {

    Optional<OrganizationEntity> findByPublicId(UUID publicId);

    boolean existsBySlug(String slug);

    @Query(value = """
            select o from OrganizationEntity o
            where o.id in (select m.organizationId from OrganizationMembershipEntity m
                           where m.userId = :userId and m.status = 'active')
            order by o.id asc
            """,
            countQuery = """
            select count(o) from OrganizationEntity o
            where o.id in (select m.organizationId from OrganizationMembershipEntity m
                           where m.userId = :userId and m.status = 'active')
            """)
    Page<OrganizationEntity> findMyOrganizations(@Param("userId") Long userId, Pageable pageable);

    /** CAS 条件更新（If-Match，04 §10）：仅当版本一致时更新，返回受影响行数。 */
    @Modifying
    @Query("update OrganizationEntity o set o.name = :name, o.description = :description, "
            + "o.version = o.version + 1, o.updatedAt = :now "
            + "where o.id = :id and o.version = :expectedVersion")
    int compareAndSwap(@Param("id") Long id, @Param("expectedVersion") long expectedVersion,
                       @Param("name") String name, @Param("description") String description,
                       @Param("now") OffsetDateTime now);
}
