package com.modelhub.identity.repo;

import com.modelhub.identity.domain.SysDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SysDictRepository extends JpaRepository<SysDictEntity, Long> {

    Optional<SysDictEntity> findByPublicId(UUID publicId);

    Optional<SysDictEntity> findByDictCode(String dictCode);

    boolean existsByDictCode(String dictCode);

    List<SysDictEntity> findAllByOrderById();
}
