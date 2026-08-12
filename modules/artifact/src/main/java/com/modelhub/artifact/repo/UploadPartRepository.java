package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.UploadPartEntity;
import com.modelhub.artifact.domain.UploadPartId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadPartRepository extends JpaRepository<UploadPartEntity, UploadPartId> {

    List<UploadPartEntity> findByUploadSessionIdOrderByPartNumber(Long uploadSessionId);

    void deleteByUploadSessionId(Long uploadSessionId);
}
