/*
 * 功能: 上传暂存区清理 Job Handler——物化完成后删除 MinIO staging 对象。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.transfer.domain.StoragePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 上传暂存区清理 Worker。
 */
@Component
public class StagingCleanupJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StagingCleanupJobHandler.class);
    private static final String STAGING_BUCKET = "asset-staging";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    public StagingCleanupJobHandler(StoragePort storagePort, ObjectMapper objectMapper) {
        this.storagePort = storagePort;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "STAGING_CLEANUP";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        String assetId = payload.path("assetId").asText(context.assetId());
        String versionId = payload.path("versionId").asText();
        String sessionId = payload.path("sessionId").asText();

        String sessionKey = "staging/" + assetId + "/" + versionId + "/" + sessionId;
        if (storagePort.objectExists(STAGING_BUCKET, sessionKey)) {
            storagePort.deleteObject(STAGING_BUCKET, sessionKey);
            LOG.info("deleted staging object key={}", sessionKey);
        }
    }
}
