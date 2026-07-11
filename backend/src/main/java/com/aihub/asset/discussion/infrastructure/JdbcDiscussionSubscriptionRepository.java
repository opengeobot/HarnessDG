/*
 * 功能: 资产讨论订阅 JDBC 仓储实现，读写 asset_subscription 表。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.discussion.infrastructure;

import com.aihub.asset.discussion.domain.DiscussionSubscriptionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 资产讨论订阅 JDBC 仓储。
 */
@Repository
public class JdbcDiscussionSubscriptionRepository implements DiscussionSubscriptionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDiscussionSubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> findSubscribers(String assetId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT principal_id FROM asset_subscription WHERE asset_id = ?",
                String.class, assetId);
        return Set.copyOf(new HashSet<>(ids));
    }

    @Override
    public boolean isSubscribed(String assetId, String principalId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_subscription WHERE asset_id = ? AND principal_id = ?",
                Long.class, assetId, principalId);
        return count != null && count > 0;
    }

    @Override
    public void subscribe(String assetId, String principalId) {
        jdbcTemplate.update(
                "INSERT INTO asset_subscription (asset_id, principal_id) VALUES (?, ?) "
                        + "ON CONFLICT (asset_id, principal_id) DO NOTHING",
                assetId, principalId);
    }

    @Override
    public void unsubscribe(String assetId, String principalId) {
        jdbcTemplate.update(
                "DELETE FROM asset_subscription WHERE asset_id = ? AND principal_id = ?",
                assetId, principalId);
    }
}
