package com.modelhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 组织与成员请求 DTO（04 §4）。 */
public final class OrgRequests {

    private OrgRequests() {}

    public record CreateOrgRequest(
            @NotBlank @Size(min = 2, max = 64) String slug,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 1024) String description) {}

    public record UpdateOrgRequest(
            @Size(max = 128) String name,
            String status) {}

    public record AddMemberRequest(
            @NotNull UUID userId,
            @NotBlank String role) {}

    public record UpdateMemberRoleRequest(
            @NotBlank String role) {}
}
