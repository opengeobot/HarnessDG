/*
 * 功能: DownloadStatsService 单元测试——审计聚合与授权校验。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.observability.domain.AssetDownloadStats;
import com.aihub.shared.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DownloadStatsServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AuthorizationService authorizationService;
    private DownloadStatsService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        authorizationService = mock(AuthorizationService.class);
        service = new DownloadStatsService(jdbcTemplate, authorizationService);
    }

    @Test
    void getAssetStatsRequiresAssetRead() {
        doThrow(new AuthorizationException(ErrorCode.AUTH_PERMISSION_DENIED, "denied", java.util.Map.of()))
                .when(authorizationService).requirePermission(Permissions.ASSET_READ);

        org.junit.jupiter.api.Assertions.assertThrows(AuthorizationException.class,
                () -> service.getAssetStats("ast_001"));
    }

    @Test
    void getAssetStatsAggregatesDownloadEvents() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString(), anyString()))
                .thenReturn(42L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                anyString(), anyString())).thenReturn(List.of());

        AssetDownloadStats stats = service.getAssetStats("ast_001");

        assertThat(stats.assetId()).isEqualTo("ast_001");
        assertThat(stats.totalDownloads()).isEqualTo(42L);
        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }

    @Test
    void getDownloadLeaderboardRequiresSystemObserve() {
        service.getDownloadLeaderboard(10);
        verify(authorizationService).requirePermission(Permissions.SYSTEM_OBSERVE);
    }
}
