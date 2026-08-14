package com.modelhub.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** catalog 域请求体（契约 CreateRepositoryRequest/Collaborator 等，additionalProperties=false 由 Jackson 配置兜底）。 */
public final class RepoRequests {

    private RepoRequests() {}

    /** 契约 CreateRepositoryRequest：required [namespaceId,type,name,visibility,metadataSchemaVersion]。 */
    public record CreateRepositoryRequest(
            @NotNull UUID namespaceId,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "type 格式非法") String type,
            @NotNull @Min(1) Integer metadataSchemaVersion,
            @NotBlank @Size(min = 1, max = 128) String name,
            @Size(max = 128) String displayName,
            @Size(max = 4000) String description,
            @NotBlank @Pattern(regexp = "^(public|organization|private)$", message = "visibility 仅支持 public/organization/private") String visibility,
            Boolean gated,
            JsonNode metadata) {}

    /** 契约添加协作者请求：required [subjectId,subjectType,role]。 */
    public record AddCollaboratorRequest(
            @NotNull UUID subjectId,
            @NotBlank @Pattern(regexp = "^(user|organization)$", message = "subjectType 仅支持 user/organization") String subjectType,
            @NotBlank @Pattern(regexp = "^(admin|maintain|write|read)$", message = "role 非法") String role) {}

    /** 契约更新协作者请求：required [role]。 */
    public record UpdateCollaboratorRequest(
            @NotBlank @Pattern(regexp = "^(admin|maintain|write|read)$", message = "role 非法") String role) {}

    /** 契约 gated 访问申请请求：required [reason]。 */
    public record RequestAccessRequest(
            @NotBlank @Size(max = 2000) String reason) {}

    /** 契约 ApproveAccessRequestRequest：可选 grantExpiresAt。 */
    public record ApproveAccessRequestRequest(OffsetDateTime grantExpiresAt) {}

    /** 契约 CreateFeedbackRequest：required [content]，1..10000（04 §5）。 */
    public record CreateFeedbackRequest(
            @NotBlank @Size(min = 1, max = 10000) String content) {}
}
