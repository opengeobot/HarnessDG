package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysDictItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysDictItemRepository extends JpaRepository<SysDictItemEntity, Long> {

    List<SysDictItemEntity> findByDictIdOrderBySortOrder(Long dictId);

    Optional<SysDictItemEntity> findByDictIdAndItemValue(Long dictId, String itemValue);

    long countByDictId(Long dictId);
}
