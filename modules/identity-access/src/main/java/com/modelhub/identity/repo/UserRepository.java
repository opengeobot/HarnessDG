package com.modelhub.identity.repo;

import com.modelhub.identity.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPublicId(UUID publicId);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("update UserEntity u set u.failedAttempts = 0 where u.id = :id")
    int resetFailedAttempts(@Param("id") Long id);
}
