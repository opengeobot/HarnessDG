package com.aihub.version.domain;

/**
 * 版本状态枚举。
 *
 * <p>状态机：DRAFT → VALIDATING → PENDING_REVIEW → PUBLISHED → DEPRECATED → ARCHIVED。
 * 数据库 CHECK 约束与 Java 枚举保持同步。
 */
public enum VersionStatus {

    DRAFT,
    VALIDATING,
    PENDING_REVIEW,
    PUBLISHED,
    DEPRECATED,
    ARCHIVED;

    /**
     * 校验从当前状态转换到目标状态是否合法。
     *
     * @throws IllegalStateException 状态转换不合法
     */
    public void assertTransitionTo(VersionStatus target) {
        boolean allowed = switch (this) {
            case DRAFT -> target == VALIDATING || target == ARCHIVED;
            case VALIDATING -> target == PENDING_REVIEW || target == DRAFT || target == ARCHIVED;
            case PENDING_REVIEW -> target == PUBLISHED || target == DRAFT || target == ARCHIVED;
            case PUBLISHED -> target == DEPRECATED || target == ARCHIVED;
            case DEPRECATED -> target == ARCHIVED || target == PUBLISHED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                    "version status transition not allowed: " + this + " -> " + target);
        }
    }
}
