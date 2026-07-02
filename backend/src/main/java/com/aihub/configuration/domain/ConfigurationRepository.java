/*
 * 功能: 统一配置仓储端口，约定配置的查询与更新（含乐观并发校验）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.domain;

import java.util.List;
import java.util.Optional;

/**
 * 统一配置仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。更新以 expectedVersion 做乐观并发校验。
 */
public interface ConfigurationRepository {

    /** 列出全部配置。 */
    List<SystemConfig> findAll();

    /** 按键查找配置。 */
    Optional<SystemConfig> findByKey(String configKey);

    /**
     * 更新配置值，以 expectedVersion 做乐观并发校验，version 自增。
     *
     * @return 更新后的配置（含新 version）
     */
    SystemConfig update(String configKey, String configValue, long expectedVersion, String updatedBy);
}
