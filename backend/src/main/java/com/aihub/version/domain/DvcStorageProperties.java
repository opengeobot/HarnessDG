/*
 * 功能: DVC 对象存储配置端口——应用层签发凭据时所需端点与 scoped 密钥。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.version.domain;

/**
 * DVC 远程存储配置端口。
 *
 * <p>由 MinIO 基础设施或 bootstrap 配置属性实现，应用层不得依赖具体基础设施类。
 */
public interface DvcStorageProperties {

    /** MinIO 内部端点 URL。 */
    String getEndpoint();

    /** 外部可达端点（可选，Docker 网络映射场景）。 */
    String getExternalEndpoint();

    /** 解析 DVC 专用访问密钥（不回退 root 密钥）。 */
    String resolveDvcAccessKey();

    /** 解析 DVC 专用秘密密钥（不回退 root 密钥）。 */
    String resolveDvcSecretKey();
}
