package com.modelhub.identity.repo;

import com.modelhub.identity.domain.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 只追加：仅暴露保存与查询，不提供删除 API（02 §2）。 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByActorOrderByIdDesc(String actor);

    List<AuditLogEntity> findTop200ByOrderByIdDesc();
}
