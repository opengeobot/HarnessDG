package com.modelhub.catalog.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 热搜词投影（09 §2.2 全局搜索覆盖层）：按 hot-desc 公式取 top-10 公开 active 仓库，
 * word 取 displayName（空则 name）。可重建投影：内存缓存 5 分钟，version 为词表内容
 * SHA-256 前 16 位十六进制（客户端可据此判断词表变化）。
 */
@Service
public class HotSearchesService {

    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int LIMIT = 10;

    /** 契约 HotSearches schema：version + words。 */
    public record HotSearchesView(String version, List<String> words) {}

    private record Cached(HotSearchesView view, long expiresAt) {}

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public HotSearchesView hotSearches() {
        long now = System.currentTimeMillis();
        Cached cached = cache.get();
        if (cached != null && cached.expiresAt() > now) {
            return cached.view();
        }
        HotSearchesView fresh = build();
        // 并发下可能重复构建，投影幂等可重建，无需加锁
        cache.set(new Cached(fresh, now + CACHE_TTL_MILLIS));
        return fresh;
    }

    private HotSearchesView build() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT r.display_name, r.name FROM repositories r "
                        + "LEFT JOIN repository_stats s ON s.repository_id = r.id "
                        + "WHERE r.visibility = 'public' AND r.lifecycle_status = 'active' "
                        + "ORDER BY (COALESCE(s.visits,0) + 3*COALESCE(s.downloads,0) "
                        + "+ 2*COALESCE(s.likes,0)) DESC, r.updated_at DESC, r.public_id DESC "
                        + "LIMIT " + LIMIT).getResultList();
        List<String> words = new ArrayList<>();
        for (Object[] row : rows) {
            String display = row[0] == null ? null : row[0].toString().trim();
            String word = display == null || display.isEmpty() ? row[1].toString() : display;
            if (!words.contains(word)) {
                words.add(word);
            }
        }
        return new HotSearchesView(versionOf(words), List.copyOf(words));
    }

    private static String versionOf(List<String> words) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("\u0000", words).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 为 JDK 必备算法，实际不可达
            return Integer.toHexString(String.join("\u0000", words).hashCode());
        }
    }
}
