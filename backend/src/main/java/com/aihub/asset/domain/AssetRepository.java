/*
 * 功能: 资产仓储端口，定义聚合持久化与检索契约。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

import com.aihub.shared.api.CursorPage;
import java.util.Map;
import java.util.Optional;

/**
 * 资产仓储端口（Port）。
 *
 * <p>领域只定义持久化与检索契约，具体由 infrastructure 适配器以 MyBatis-Plus（单表 CRUD/乐观锁）
 * 与显式 SQL（检索/权限下推）实现。
 */
public interface AssetRepository {

    /**
     * 按业务资产 ID 查找（不含已逻辑删除记录）。
     *
     * @param assetId 业务资产 ID
     * @return 资产聚合
     */
    Optional<Asset> findByAssetId(String assetId);

    /**
     * 判断坐标是否已被占用（不含已逻辑删除记录）。
     *
     * @param namespace 命名空间
     * @param type      类型
     * @param name      名称
     * @return 是否存在
     */
    boolean existsByCoordinate(String namespace, AssetType type, String name);

    /**
     * 插入新资产（含类型扩展）。
     *
     * @param asset 资产聚合
     */
    void insert(Asset asset);

    /**
     * 更新资产（乐观锁，按 {@code rowVersion} 校验）。
     *
     * @param asset 资产聚合
     */
    void update(Asset asset);

    /**
     * 逻辑删除资产。
     *
     * @param assetId   业务资产 ID
     * @param updatedBy 操作者主体 ID
     */
    void softDelete(String assetId, String updatedBy);

    /**
     * 按条件检索资产摘要（权限可见性在 SQL 阶段过滤）。
     *
     * @param criteria 检索条件
     * @return 游标分页的资产摘要
     */
    CursorPage<AssetSummary> search(AssetSearchCriteria criteria);

    /**
     * 按维度统计资产数量（Facet），应用与 search 相同的访问作用域过滤。
     *
     * @param criteria 检索条件
     * @return 各维度计数映射
     */
    Map<String, Map<String, Long>> facet(AssetSearchCriteria criteria);
}
