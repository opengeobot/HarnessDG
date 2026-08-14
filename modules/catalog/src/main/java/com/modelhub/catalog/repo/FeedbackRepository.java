package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.FeedbackEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {

    /** cursor 分页（id 游标，BIGSERIAL 单调对应 created_at 序），新→旧，过滤已删除。 */
    List<FeedbackEntity> findByRepositoryIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long repositoryId, Long lastId, Pageable pageable);
}
