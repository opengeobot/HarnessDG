package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysMenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysMenuRepository extends JpaRepository<SysMenuEntity, Long> {

    Optional<SysMenuEntity> findByCode(String code);

    boolean existsByCode(String code);

    long countByParentCode(String parentCode);

    List<SysMenuEntity> findAllByOrderBySortOrderAscIdAsc();
}
