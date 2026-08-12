package com.modelhub.identity.repo;

import com.modelhub.identity.domain.RefreshSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSessionEntity, Long> {

    Optional<RefreshSessionEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSessionEntity s where s.tokenHash = :hash")
    Optional<RefreshSessionEntity> findByTokenHashForUpdate(@Param("hash") String hash);

    @Query("select s from RefreshSessionEntity s where s.userId = :userId and s.revokedAt is null")
    List<RefreshSessionEntity> findActiveByUserId(@Param("userId") Long userId);

    /** 吊销用户全部未吊销会话（logout-all / change-password，02 §6.1）；同时置过期防止宽限窗内补发。 */
    @Modifying
    @Query("update RefreshSessionEntity s set s.revokedAt = :now, s.expiresAt = :now "
            + "where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * 吊销整个 family（重放检测，02 §6.1）。
     * REQUIRES_NEW 独立提交：调用方 rotate 随后抛出 401 会回滚外层事务，吊销事实不能丢；
     * 同时置过期，防止 family 内会话在宽限窗内被并发补发逻辑复活。
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update RefreshSessionEntity s set s.revokedAt = :now, s.expiresAt = :now "
            + "where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    /** 原子轮换占位：仅当未吊销时置 revoked，返回受影响行数（并发 refresh 的 CAS 点）。 */
    @Modifying
    @Query("update RefreshSessionEntity s set s.revokedAt = :now, s.replacedBy = :replacedBy "
            + "where s.id = :id and s.revokedAt is null")
    int rotateIfActive(@Param("id") Long id, @Param("now") OffsetDateTime now,
                       @Param("replacedBy") Long replacedBy);
}
