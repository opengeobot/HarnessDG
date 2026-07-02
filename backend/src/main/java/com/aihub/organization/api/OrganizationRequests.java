/*
 * 功能: organization API 请求体集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.api;

/**
 * organization API 请求体集合。
 *
 * <p>仅承载入参，不暴露领域对象；字段名与 OpenAPI 契约对齐。
 */
public final class OrganizationRequests {

    private OrganizationRequests() {
    }

    /** 创建组织请求（CreateOrganizationRequest）。 */
    public record CreateOrganizationRequest(String code, String name, String giteaOrganization) {
    }

    /** 添加组织成员请求（AddOrganizationMemberRequest）。 */
    public record AddOrganizationMemberRequest(String principalId) {
    }

    /** 创建项目请求（CreateProjectRequest）。 */
    public record CreateProjectRequest(String code, String name) {
    }
}
