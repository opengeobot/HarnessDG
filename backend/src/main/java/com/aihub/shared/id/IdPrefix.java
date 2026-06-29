/*
 * 功能: 业务 ID 前缀枚举，定义"业务前缀 + ULID"中的前缀部分。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.id;

/**
 * 业务 ID 前缀。
 *
 * <p>公共标识统一采用"业务前缀_ULID"形式，前缀用于人读区分对象类型，
 * 数据库内部可使用 BIGINT 主键优化关联，但跨系统标识只暴露业务 ID。
 */
public enum IdPrefix {

    /** 访问主体。 */
    PRINCIPAL("prn"),

    /** 用户。 */
    USER("usr"),

    /** Agent。 */
    AGENT("agt"),

    /** 组织。 */
    ORGANIZATION("org"),

    /** 项目。 */
    PROJECT("prj"),

    /** 资产。 */
    ASSET("ast"),

    /** 版本。 */
    VERSION("ver"),

    /** 上传会话。 */
    UPLOAD_SESSION("upl"),

    /** 任务。 */
    JOB("job"),

    /** 审计记录。 */
    AUDIT("aud"),

    /** 请求标识。 */
    REQUEST("req");

    private final String value;

    IdPrefix(String value) {
        this.value = value;
    }

    /**
     * @return 前缀字面值（不含分隔符）
     */
    public String value() {
        return value;
    }
}
