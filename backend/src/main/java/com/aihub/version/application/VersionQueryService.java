/*
 * 功能: 版本只读查询门面——供跨模块适配器复用，避免直接依赖 VersionRepository。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.version.application;

import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 版本只读查询服务。
 */
@Service
public class VersionQueryService {

    private static final int DEFAULT_LIST_LIMIT = 50;

    private final VersionRepository versionRepository;

    public VersionQueryService(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    /** 批量查询各资产最新已发布版本。 */
    @Transactional(readOnly = true)
    public Map<String, Version> findLatestPublishedByAssetIds(Set<String> assetIds) {
        return versionRepository.findLatestPublishedByAssetIds(assetIds);
    }

    /** 查询单资产最新已发布版本。 */
    @Transactional(readOnly = true)
    public Optional<Version> findLatestPublishedByAssetId(String assetId) {
        return versionRepository.findLatestPublishedByAssetId(assetId);
    }

    /** 按版本 ID 查询版本（不存在时抛 NotFoundException）。 */
    @Transactional(readOnly = true)
    public Version getVersion(String versionId) {
        return versionRepository.findByVersionId(versionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.VERSION_NOT_FOUND,
                        "version not found: " + versionId, Map.of()));
    }

    /** 列出资产下全部版本（默认分页上限）。 */
    @Transactional(readOnly = true)
    public List<Version> listVersions(String assetId) {
        CursorPage<Version> page = versionRepository.listByAsset(assetId, null, DEFAULT_LIST_LIMIT);
        return page.items();
    }
}
