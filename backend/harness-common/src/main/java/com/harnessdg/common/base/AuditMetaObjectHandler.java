/**
 * 功能：MyBatis-Plus 自动填充处理器（审计字段）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.base;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String currentUser = getCurrentUser();
        OffsetDateTime now = OffsetDateTime.now();

        this.strictInsertFill(metaObject, "createdBy", String.class, currentUser);
        this.strictInsertFill(metaObject, "updatedBy", String.class, currentUser);
        this.strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedBy", String.class, getCurrentUser());
        this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now());
    }

    private String getCurrentUser() {
        // 从 SecurityContext 获取当前用户，未认证时返回 "system"
        // 后续由 harness-auth 模块提供具体实现
        return "system";
    }
}
