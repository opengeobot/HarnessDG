package com.modelhub.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Artifacts 请求 DTO（契约 components/schemas）。 */
public final class ArtifactRequests {

    private ArtifactRequests() {}

    /** InitiateUploadRequest：branch/baseCommitSha/path/sizeBytes 必填。 */
    public record InitiateUploadRequest(
            @NotBlank String branch,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$") String baseCommitSha,
            @NotBlank @Size(min = 1, max = 1024) String path,
            @NotNull @Min(1) Long sizeBytes,
            @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sha256,
            String contentType) {}

    /** PartUrlRequest：批次 1..100、去重。 */
    public record PartUrlRequest(
            @NotEmpty @Size(min = 1, max = 100)
            List<@NotNull @Min(1) @Max(10000) Integer> partNumbers) {}

    /** PublishUploadRequest：conflict 后免重传发布，baseCommitSha 为期望的当前分支 head。 */
    public record PublishUploadRequest(
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$") String baseCommitSha,
            @Pattern(regexp = "^(fail_if_path_changed|overwrite)$") String conflictResolution,
            @Size(max = 500) String commitMessage) {}
}
