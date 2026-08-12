package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.CollaboratorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollaboratorRepository extends JpaRepository<CollaboratorEntity, Long> {

    List<CollaboratorEntity> findByRepositoryIdOrderByIdAsc(Long repositoryId);

    Optional<CollaboratorEntity> findByRepositoryIdAndSubjectTypeAndSubjectUserId(
            Long repositoryId, String subjectType, Long subjectUserId);

    Optional<CollaboratorEntity> findByRepositoryIdAndSubjectTypeAndSubjectOrganizationId(
            Long repositoryId, String subjectType, Long subjectOrganizationId);

    /** 仓库的所有协作者 subject 组织 id（授权解析用）。 */
    @Query("select c.subjectOrganizationId from CollaboratorEntity c "
            + "where c.repositoryId = :repositoryId and c.subjectType = 'organization'")
    List<Long> findCollaboratorOrgIds(@Param("repositoryId") Long repositoryId);

    /** 用户直接协作者的仓库 id 集合（列表授权过滤用）。 */
    @Query("select c.repositoryId from CollaboratorEntity c "
            + "where c.subjectType = 'user' and c.subjectUserId = :userId "
            + "and (c.expiresAt is null or c.expiresAt > :now)")
    List<Long> findRepoIdsForUser(@Param("userId") Long userId,
                                  @Param("now") java.time.OffsetDateTime now);

    /** 组织 subject 协作者的仓库 id 集合（列表授权过滤用）。 */
    @Query("select c.repositoryId from CollaboratorEntity c "
            + "where c.subjectType = 'organization' and c.subjectOrganizationId in :orgIds "
            + "and (c.expiresAt is null or c.expiresAt > :now)")
    List<Long> findRepoIdsForOrgs(@Param("orgIds") java.util.Collection<Long> orgIds,
                                  @Param("now") java.time.OffsetDateTime now);
}
