package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SysRoleRepository extends JpaRepository<SysRoleEntity, Long> {

    Optional<SysRoleEntity> findByPublicId(UUID publicId);

    Optional<SysRoleEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<SysRoleEntity> findAllByOrderById();
}
