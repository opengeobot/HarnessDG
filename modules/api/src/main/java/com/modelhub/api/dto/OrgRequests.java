package com.modelhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 组织与成员请求 DTO（04 §4）。 */
public final class OrgRequests {

    private OrgRequests() {}

    public record CreateOrgRequest(
            @NotBlank @Size(min = 3, max = 63) String slug,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 1024) String description) {}

    public record UpdateOrgRequest(
            @Size(max = 128) String name,
            @Size(max = 1024) String description) {}

    public record AddMemberRequest(
            @NotBlank String userId,
            @NotBlank String role) {}

    public record UpdateMemberRoleRequest(
            @NotBlank String role) {}
}
