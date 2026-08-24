package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.GatedRequestEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatedRequestRepository extends JpaRepository<GatedRequestEntity, Long> {

    Optional<GatedRequestEntity> findByPublicId(UUID publicId);

    Optional<GatedRequestEntity> findFirstByRepositoryIdAndUserIdAndStatus(
            Long repositoryId, Long userId, String status);

    /**
     * 申请列表 keyset 分页（04 §6.2，Task 8）：排序与续读键一致（id DESC + id < lastId）。
     * id 为 BIGSERIAL，按插入顺序单调递增，与 createdAt DESC 等价排序但无同秒并列歧义，
     * 并发写入下翻页不重复、不遗漏。统一取 limit+1 行由调用方判定 hasMore。
     * status 过滤拆成独立方法（而非 :status is null OR）：PostgreSQL 无法推断
     * null 绑定参数的类型（42P18），有/无过滤分开走派生查询保持参数全类型化。
     */
    List<GatedRequestEntity> findByRepositoryIdOrderByIdDesc(Long repositoryId, Pageable pageable);

    List<GatedRequestEntity> findByRepositoryIdAndIdLessThanOrderByIdDesc(
            Long repositoryId, Long lastId, Pageable pageable);

    List<GatedRequestEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<GatedRequestEntity> findByUserIdAndIdLessThanOrderByIdDesc(
            Long userId, Long lastId, Pageable pageable);

    List<GatedRequestEntity> findByUserIdAndStatusOrderByIdDesc(
            Long userId, String status, Pageable pageable);

    List<GatedRequestEntity> findByUserIdAndIdLessThanAndStatusOrderByIdDesc(
            Long userId, Long lastId, String status, Pageable pageable);
}
