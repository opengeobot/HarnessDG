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

    /** Team。 */
    TEAM("team"),

    /** 组织/项目成员关系。 */
    MEMBER("mbr"),

    /** 受控标签。 */
    TAG("tag"),

    /** 角色。 */
    ROLE("rol"),

    /** 权限。 */
    PERMISSION("prm"),

    /** 角色绑定。 */
    ROLE_BINDING("rbd"),

    /** 资源 ACL。 */
    RESOURCE_ACL("acl"),

    /** 字典项。 */
    DICT("dct"),

    /** 配置项。 */
    CONFIG("cfg"),

    /** 通知。 */
    NOTIFICATION("ntf"),

    /** Webhook。 */
    WEBHOOK("whk"),

    /** 凭据 / Token（仅承载摘要标识，非 Token 明文）。 */
    TOKEN("tok"),

    /** 资产。 */
    ASSET("ast"),

    /** 版本。 */
    VERSION("ver"),

    /** 版本工件。 */
    ARTIFACT("art"),

    /** 上传会话。 */
    UPLOAD_SESSION("upl"),

    /** 上传文件。 */
    UPLOAD_FILE("upf"),

    /** 资产预览。 */
    PREVIEW("prv"),

    /** 任务。 */
    JOB("job"),

    /** 审计记录。 */
    AUDIT("aud"),

    /** 请求标识。 */
    REQUEST("req"),

    /** 讨论线程。 */
    THREAD("thr"),

    /** 评论。 */
    COMMENT("cmt"),

    /** 评论修订。 */
    REVISION("rev"),

    /** 校验报告。 */
    VALIDATION_REPORT("vrp"),

    /** 发布请求。 */
    PUBLISH_REQUEST("pub"),

    /** 审批决策。 */
    REVIEW_DECISION("rvw"),

    /** 系统告警。 */
    ALERT("alt"),

    /** 资产血缘关系。 */
    RELATION("rel");

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
