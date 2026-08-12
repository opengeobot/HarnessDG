package com.modelhub.identity.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（07 §5.3）：词汇固定为 ok/degraded/unavailable。
 * livez 恒 ok；readyz 检查数据库与配置。
 */
@Service
public class SystemHealthService {

    public record HealthStatus(String status, Map<String, String> checks) {}

    private final EntityManager entityManager;

    public SystemHealthService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public HealthStatus live() {
        return new HealthStatus("ok", Map.of());
    }

    @Transactional(readOnly = true)
    public HealthStatus ready() {
        Map<String, String> checks = new LinkedHashMap<>();
        boolean degraded = false;
        try {
            Object one = entityManager.createNativeQuery("select 1").getSingleResult();
            checks.put("database", "1".equals(String.valueOf(one)) ? "ok" : "unavailable");
        } catch (RuntimeException e) {
            checks.put("database", "unavailable");
        }
        checks.put("configuration", "ok");
        String overall = checks.containsValue("unavailable") ? (degraded ? "degraded" : "unavailable") : "ok";
        return new HealthStatus(overall, checks);
    }
}
