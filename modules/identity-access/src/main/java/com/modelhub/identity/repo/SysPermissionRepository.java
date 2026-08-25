package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysPermissionRepository extends JpaRepository<SysPermissionEntity, Long> {

    Optional<SysPermissionEntity> findByCode(String code);

    List<SysPermissionEntity> findByCodeIn(Collection<String> codes);

    List<SysPermissionEntity> findAllByOrderById();
}
