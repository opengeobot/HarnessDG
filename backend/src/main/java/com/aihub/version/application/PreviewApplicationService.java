/*
 * 功能: 预览生成应用服务，编排 PREVIEW_GENERATE 任务入队。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import com.aihub.job.application.JobApplicationService;
import com.aihub.job.domain.Job;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 预览生成应用服务。
 *
 * <p>供 REST 适配器与上传物化 Job 复用，统一 PREVIEW_GENERATE 入队逻辑。
 */
@Service
public class PreviewApplicationService {

    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;

    public PreviewApplicationService(JobApplicationService jobApplicationService,
                                     ObjectMapper objectMapper) {
        this.jobApplicationService = jobApplicationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 入队预览生成任务。
     *
     * @param assetId     资产 ID
     * @param versionId   版本 ID
     * @param content     预览源内容（可空，由 Worker 从 MinIO 拉取时再扩展）
     * @param contentType 内容类型
     * @param principalId 发起主体
     * @return 任务 ID
     */
    public String enqueuePreview(String assetId, String versionId, String content,
                                 String contentType, String principalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", assetId);
        payload.put("versionId", versionId);
        payload.put("contentType", contentType != null ? contentType : "text/csv");
        if (content != null && !content.isBlank()) {
            payload.put("content", content);
        }
        try {
            Job job = jobApplicationService.enqueue(
                    "PREVIEW_GENERATE",
                    objectMapper.writeValueAsString(payload),
                    principalId,
                    null,
                    assetId,
                    3);
            return job.jobId();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize preview payload", ex);
        }
    }
}
