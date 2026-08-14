package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.RepositoryLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositoryLikeRepository extends JpaRepository<RepositoryLikeEntity, Long> {

    boolean existsByUserIdAndRepositoryId(Long userId, Long repositoryId);

    /** 幂等插入：唯一约束冲突静默忽略（03 §6.1 并发兜底，不抛竞态异常）。 */
    @Modifying
    @Query(value = "INSERT INTO repository_likes(user_id, repository_id, created_at) "
            + "VALUES (:userId, :repositoryId, now()) "
            + "ON CONFLICT (user_id, repository_id) DO NOTHING",
            nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("repositoryId") Long repositoryId);

    long deleteByUserIdAndRepositoryId(Long userId, Long repositoryId);

    long countByRepositoryId(Long repositoryId);

    long countByUserId(Long userId);
}
